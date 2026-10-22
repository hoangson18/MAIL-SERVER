package mailserver.pop3;

import mailserver.*;
import mailserver.MailStore.Mail;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xu ly 1 phien POP3 (RFC 1939): AUTHORIZATION -> TRANSACTION -> UPDATE.
 * Lenh: USER, PASS, STAT, LIST, RETR, DELE, RSET, NOOP, TOP, UIDL, QUIT
 * + lenh mo rong REG <user> <pass> de dang ky tai khoan (chi truoc khi dang nhap).
 */
public class Pop3Session implements Runnable {

    // 1 user chi co 1 phien POP3 tai 1 thoi diem
    private static final Set<String> LOCKED = ConcurrentHashMap.newKeySet();

    private enum State { AUTHORIZATION, TRANSACTION }

    private final Socket socket;
    private final UserStore users;

    private State state = State.AUTHORIZATION;
    private String username;
    private String lockedUser;

    private List<Mail> messages;     // snapshot luc dang nhap
    private boolean[] deleted;

    public Pop3Session(Socket socket, UserStore users) {
        this.socket = socket;
        this.users = users;
    }

    @Override
    public void run() {
        String peer = String.valueOf(socket.getRemoteSocketAddress());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(Config.SESSION_TIMEOUT_MS);
            send(out, "+OK POP3 server ready");

            String line;
            while ((line = in.readLine()) != null) {
                String up = line.toUpperCase();
                String shown = up.startsWith("PASS") || up.startsWith("REG")
                        ? line.split(" ")[0] + " ****" : line;
                System.out.println("[POP3 <- " + peer + "] " + shown);
                try {
                    if (!handle(line, out)) break;
                } catch (DbException e) {
                    System.err.println("[POP3] Loi CSDL: " + e.getMessage());
                    send(out, "-ERR Loi may chu, thu lai sau");
                }
            }
        } catch (IOException e) {
            System.err.println("[POP3] Loi ket noi " + peer + ": " + e.getMessage());
        } finally {
            releaseLock();   // dam bao nha khoa du client ngat dot ngot
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[POP3] Dong ket noi " + peer);
        }
    }

    private boolean handle(String line, PrintWriter out) {
        String[] parts = line.trim().split("\\s+", 2);
        String cmd = parts[0].toUpperCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "REG":  return reg(arg, out);
            case "USER": return user(arg, out);
            case "PASS": return pass(arg, out);
            case "STAT": return stat(out);
            case "LIST": return list(arg, out);
            case "UIDL": return uidl(arg, out);
            case "RETR": return retr(arg, out);
            case "TOP":  return top(arg, out);
            case "DELE": return dele(arg, out);
            case "RSET": return rset(out);
            case "NOOP": send(out, "+OK"); return true;
            case "QUIT": return quit(out);
            default:     send(out, "-ERR Lenh khong duoc ho tro"); return true;
        }
    }

    private boolean reg(String arg, PrintWriter out) {
        if (state != State.AUTHORIZATION) {
            send(out, "-ERR Chi dang ky duoc truoc khi dang nhap");
            return true;
        }
        String[] p = arg.split("\\s+");
        if (p.length != 2) {
            send(out, "-ERR Sai cu phap. Dung: REG <username> <password>");
            return true;
        }
        String err = users.register(p[0], p[1]);
        send(out, err == null ? "+OK Tai khoan da duoc tao thanh cong" : "-ERR " + err);
        return true;
    }

    private boolean user(String arg, PrintWriter out) {
        if (state != State.AUTHORIZATION) send(out, "-ERR Lenh khong hop le o trang thai hien tai");
        else if (arg.isEmpty()) send(out, "-ERR Thieu username");
        else { username = UserStore.normalize(arg); send(out, "+OK User accepted, gui PASS"); }
        return true;
    }

    private boolean pass(String arg, PrintWriter out) {
        if (state != State.AUTHORIZATION || username == null) {
            send(out, "-ERR Can gui USER truoc");
            return true;
        }
        String u = username;
        username = null;
        if (!users.authenticate(u, arg)) {
            send(out, "-ERR Sai username hoac password");
            return true;
        }
        if (!LOCKED.add(u)) {
            send(out, "-ERR [IN-USE] Mailbox dang duoc phien khac su dung");
            return true;
        }
        lockedUser = u;
        username = u;
        messages = MailStore.list(u);
        deleted = new boolean[messages.size()];
        state = State.TRANSACTION;
        send(out, "+OK Dang nhap thanh cong, " + messages.size() + " thu trong hop");
        return true;
    }

    private boolean stat(PrintWriter out) {
        if (!needTransaction(out)) return true;
        int count = 0; long total = 0;
        for (int i = 0; i < messages.size(); i++)
            if (!deleted[i]) { count++; total += messages.get(i).size(); }
        send(out, "+OK " + count + " " + total);
        return true;
    }

    private boolean list(String arg, PrintWriter out) {
        if (!needTransaction(out)) return true;
        if (!arg.isEmpty()) {
            int i = index(arg);
            if (!valid(i)) send(out, "-ERR Khong ton tai thu so " + arg);
            else send(out, "+OK " + (i + 1) + " " + messages.get(i).size());
            return true;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (boolean d : deleted) if (!d) count++;
        sb.append("+OK ").append(count).append(" thu\r\n");
        for (int i = 0; i < messages.size(); i++)
            if (!deleted[i]) sb.append(i + 1).append(' ').append(messages.get(i).size()).append("\r\n");
        sb.append(".\r\n");
        out.print(sb);
        out.flush();
        return true;
    }

    /** UIDL: ma dinh danh on dinh cua thu (= id trong CSDL), khong doi khi thu khac bi xoa. */
    private boolean uidl(String arg, PrintWriter out) {
        if (!needTransaction(out)) return true;
        if (!arg.isEmpty()) {
            int i = index(arg);
            if (!valid(i)) send(out, "-ERR Khong ton tai thu so " + arg);
            else send(out, "+OK " + (i + 1) + " " + messages.get(i).id);
            return true;
        }
        StringBuilder sb = new StringBuilder("+OK\r\n");
        for (int i = 0; i < messages.size(); i++)
            if (!deleted[i]) sb.append(i + 1).append(' ').append(messages.get(i).id).append("\r\n");
        sb.append(".\r\n");
        out.print(sb);
        out.flush();
        return true;
    }

    private boolean retr(String arg, PrintWriter out) {
        if (!needTransaction(out)) return true;
        int i = index(arg);
        if (!valid(i)) { send(out, "-ERR Khong ton tai thu so " + arg); return true; }
        Mail m = messages.get(i);
        send(out, "+OK " + m.size() + " octets");
        writeMultiline(out, m.render());
        return true;
    }

    private boolean top(String arg, PrintWriter out) {
        if (!needTransaction(out)) return true;
        String[] p = arg.split("\\s+");
        int i = index(p[0]);
        int n;
        try { n = p.length > 1 ? Integer.parseInt(p[1]) : 0; } catch (NumberFormatException e) { n = -1; }
        if (!valid(i) || n < 0) { send(out, "-ERR Tham so khong hop le"); return true; }
        String raw = messages.get(i).render();
        int blank = raw.indexOf("\n\n");
        String head = blank >= 0 ? raw.substring(0, blank + 1) : raw;
        String body = blank >= 0 ? raw.substring(blank + 2) : "";
        StringBuilder sb = new StringBuilder(head).append("\n");
        String[] bl = body.split("\n", -1);
        for (int k = 0; k < Math.min(n, bl.length); k++) sb.append(bl[k]).append("\n");
        send(out, "+OK");
        writeMultiline(out, sb.toString());
        return true;
    }

    private boolean dele(String arg, PrintWriter out) {
        if (!needTransaction(out)) return true;
        int i = index(arg);
        if (!valid(i)) send(out, "-ERR Khong ton tai thu so " + arg);
        else { deleted[i] = true; send(out, "+OK Thu " + arg + " duoc danh dau xoa"); }
        return true;
    }

    private boolean rset(PrintWriter out) {
        if (!needTransaction(out)) return true;
        Arrays.fill(deleted, false);
        send(out, "+OK");
        return true;
    }

    /** UPDATE state: chi khi QUIT moi xoa that su cac thu da danh dau DELE. */
    private boolean quit(PrintWriter out) {
        if (state == State.TRANSACTION) {
            for (int i = 0; i < messages.size(); i++)
                if (deleted[i]) MailStore.delete(lockedUser, messages.get(i).id);
        }
        send(out, "+OK Bye");
        releaseLock();
        return false;
    }

    // ---- tien ich ----
    private void writeMultiline(PrintWriter out, String content) {
        for (String l : content.split("\n", -1)) {
            if (l.startsWith(".")) out.print(".");   // byte-stuffing
            out.print(l);
            out.print("\r\n");
        }
        out.print(".\r\n");
        out.flush();
    }

    private void releaseLock() {
        if (lockedUser != null) { LOCKED.remove(lockedUser); lockedUser = null; }
    }

    private boolean needTransaction(PrintWriter out) {
        if (state != State.TRANSACTION) { send(out, "-ERR Can dang nhap (USER/PASS) truoc"); return false; }
        return true;
    }

    private boolean valid(int i) { return i >= 0 && i < messages.size() && !deleted[i]; }

    private static int index(String arg) {
        try { return Integer.parseInt(arg.trim()) - 1; } catch (NumberFormatException e) { return -1; }
    }

    private static void send(PrintWriter out, String line) {
        out.print(line + "\r\n");
        out.flush();
    }
}
