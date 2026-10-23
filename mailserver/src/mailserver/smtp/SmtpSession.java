package mailserver.smtp;

import mailserver.*;
import mailserver.chat.ChatHub;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Xu ly 1 phien SMTP.
 * Lenh: HELO/EHLO, AUTH PLAIN, MAIL FROM, RCPT TO, DATA, RSET, NOOP, QUIT.
 * Phai AUTH truoc khi MAIL FROM, va MAIL FROM phai trung tai khoan da dang nhap (chong gia mao).
 */
public class SmtpSession implements Runnable {

    private static final int MAX_DATA_BYTES = 1_000_000;
    private static final Pattern HEADER_LINE = Pattern.compile("^[A-Za-z][A-Za-z0-9-]*:.*");

    private final Socket socket;
    private final UserStore users;

    private String authUser;            // null = chua dang nhap
    private String mailFrom;
    private final List<String> rcptTo = new ArrayList<>();

    public SmtpSession(Socket socket, UserStore users) {
        this.socket = socket;
        this.users = users;
    }

    @Override
    public void run() {
        String peer = String.valueOf(socket.getRemoteSocketAddress());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(Config.SESSION_TIMEOUT_MS);
            reply(out, "220 " + Config.MAIL_DOMAIN + " SMTP Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[SMTP <- " + peer + "] " + (line.toUpperCase().startsWith("AUTH") ? "AUTH ****" : line));
                try {
                    if (!handle(line, in, out)) break;
                } catch (DbException e) {
                    System.err.println("[SMTP] Loi CSDL: " + e.getMessage());
                    reply(out, "451 Loi may chu, thu lai sau");
                }
            }
        } catch (IOException e) {
            System.err.println("[SMTP] Loi ket noi " + peer + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[SMTP] Dong ket noi " + peer);
        }
    }

    private boolean handle(String line, BufferedReader in, PrintWriter out) throws IOException {
        String[] parts = line.trim().split("\\s+", 2);
        String cmd = parts[0].toUpperCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "HELO":
                reply(out, "250 " + Config.MAIL_DOMAIN + " xin chao " + (arg.isEmpty() ? "ban" : arg));
                break;

            case "EHLO":
                out.print("250-" + Config.MAIL_DOMAIN + " xin chao\r\n");
                reply(out, "250 AUTH PLAIN");
                break;

            case "AUTH":
                doAuth(arg, in, out);
                break;

            case "MAIL":
                doMail(arg, out);
                break;

            case "RCPT":
                doRcpt(arg, out);
                break;

            case "DATA":
                doData(in, out);
                break;

            case "RSET":
                resetTransaction();
                reply(out, "250 OK");
                break;

            case "NOOP":
                reply(out, "250 OK");
                break;

            case "QUIT":
                reply(out, "221 Bye");
                return false;

            default:
                reply(out, "500 Lenh khong duoc ho tro");
        }
        return true;
    }

    // ---- AUTH PLAIN: "AUTH PLAIN <base64(\0user\0pass)>" hoac 2 buoc (334) ----
    private void doAuth(String arg, BufferedReader in, PrintWriter out) throws IOException {
        String[] p = arg.trim().split("\\s+", 2);
        if (!p[0].equalsIgnoreCase("PLAIN")) {
            reply(out, "504 Chi ho tro AUTH PLAIN");
            return;
        }
        String b64 = p.length > 1 ? p[1] : null;
        if (b64 == null) {
            reply(out, "334 ");
            b64 = in.readLine();
            if (b64 == null) return;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(b64.trim()), StandardCharsets.UTF_8);
            String[] f = decoded.split("\u0000", -1); // [authzid, user, pass]
            if (f.length == 3 && users.authenticate(f[1], f[2])) {
                authUser = UserStore.normalize(f[1]);
                reply(out, "235 Xac thuc thanh cong");
                return;
            }
        } catch (IllegalArgumentException ignored) {}
        reply(out, "535 Sai username hoac password");
    }

    private void doMail(String arg, PrintWriter out) {
        if (authUser == null) { reply(out, "530 Can AUTH truoc khi gui thu"); return; }
        String from = extractAddress(arg);
        if (from == null) { reply(out, "501 Syntax error trong MAIL FROM"); return; }
        if (!UserStore.normalize(from).equals(authUser)) {
            reply(out, "553 Dia chi nguoi gui khong khop tai khoan da dang nhap");
            return;
        }
        resetTransaction();
        mailFrom = authUser + "@" + Config.MAIL_DOMAIN;
        reply(out, "250 OK");
    }

    private void doRcpt(String arg, PrintWriter out) {
        if (mailFrom == null) { reply(out, "503 Can MAIL FROM truoc"); return; }
        String rcpt = extractAddress(arg);
        if (rcpt == null) { reply(out, "501 Syntax error trong RCPT TO"); return; }

        int at = rcpt.indexOf('@');
        if (at >= 0 && !rcpt.substring(at + 1).equalsIgnoreCase(Config.MAIL_DOMAIN)) {
            reply(out, "550 Khong ho tro gui ra ngoai domain " + Config.MAIL_DOMAIN);
            return;
        }
        String user = UserStore.normalize(rcpt);
        if (!users.exists(user)) { reply(out, "550 Khong ton tai user: " + rcpt); return; }
        String full = user + "@" + Config.MAIL_DOMAIN;
        if (!rcptTo.contains(full)) rcptTo.add(full);
        reply(out, "250 OK");
    }

    private void doData(BufferedReader in, PrintWriter out) throws IOException {
        if (mailFrom == null || rcptTo.isEmpty()) {
            reply(out, "503 Can MAIL FROM va RCPT TO truoc DATA");
            return;
        }
        reply(out, "354 Nhap noi dung, ket thuc bang dong chi co dau '.'");

        List<String> lines = new ArrayList<>();
        int bytes = 0;
        boolean tooBig = false;
        String l;
        while ((l = in.readLine()) != null) {
            if (l.equals(".")) break;
            if (l.startsWith("..")) l = l.substring(1);      // bo dot-stuffing
            bytes += l.length() + 1;
            if (bytes > MAX_DATA_BYTES) { tooBig = true; continue; } // van doc het de dong bo giao thuc
            lines.add(l);
        }
        if (tooBig) {
            reply(out, "552 Thu qua lon (toi da 1MB)");
            resetTransaction();
            return;
        }

        // Tach header (chi lay Subject) va body
        String subject = "";
        int bodyStart = 0;
        int blank = lines.indexOf("");
        if (blank > 0 && lines.subList(0, blank).stream().allMatch(s -> HEADER_LINE.matcher(s).matches())) {
            for (String h : lines.subList(0, blank)) {
                if (h.regionMatches(true, 0, "Subject:", 0, 8)) subject = h.substring(8).trim();
            }
            bodyStart = blank + 1;
        }
        String body = String.join("\n", lines.subList(bodyStart, lines.size()));

        for (String rcpt : rcptTo) {
            String user = UserStore.normalize(rcpt);
            MailStore.deliver(user, mailFrom, rcpt, subject, body);
            System.out.println("[SMTP] Da luu thu " + mailFrom + " -> " + user);
            ChatHub.notifyNewMail(user, mailFrom, subject); // bao real-time cho client dang online
        }
        reply(out, "250 OK: Message accepted");
        resetTransaction();
    }

    private void resetTransaction() {
        mailFrom = null;
        rcptTo.clear();
    }

    private static String extractAddress(String arg) {
        int lt = arg.indexOf('<'), gt = arg.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String a = arg.substring(lt + 1, gt).trim();
            return a.isEmpty() ? null : a;
        }
        int colon = arg.indexOf(':');
        if (colon >= 0 && colon + 1 < arg.length()) {
            String a = arg.substring(colon + 1).trim();
            return a.isEmpty() ? null : a;
        }
        return null;
    }

    private static void reply(PrintWriter out, String line) {
        out.print(line + "\r\n");
        out.flush();
    }
}
