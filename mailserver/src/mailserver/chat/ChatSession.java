package mailserver.chat;

import mailserver.DbException;
import mailserver.UserStore;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Giao thuc chat (text, moi dong 1 lenh, UTF-8):
 *
 *  Client -> Server                         Server -> Client
 *  -----------------------------------      ------------------------------------------
 *  LOGIN <user> <pass>                      OK LOGIN | ERR <ly do>
 *  USERS                                    USERS a:1,b:0,...        (1 = online)
 *  MSG <den> <noi dung>                     MSG <tu> <den> <epochMs> <noi dung>
 *  ALL <noi dung>                           MSG <tu> * <epochMs> <noi dung>
 *  HISTORY <peer | *>                       HIST <tu> <den> <epochMs> <nd> ... HISTEND <peer>
 *  PING                                     PONG
 *  QUIT
 *                                           ONLINE <user> / OFFLINE <user>   (push)
 *                                           NEWMAIL <tu> <tieu de>           (push, thu moi)
 *                                           KICK <ly do>
 */
public class ChatSession implements Runnable {

    private final Socket socket;
    private final UserStore users;
    private PrintWriter out;
    private String me;

    public ChatSession(Socket socket, UserStore users) {
        this.socket = socket;
        this.users = users;
    }

    @Override
    public void run() {
        String peer = String.valueOf(socket.getRemoteSocketAddress());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            socket.setSoTimeout(30_000);          // 30s de dang nhap, sau do khong gioi han
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.trim().split(" ", 2);
                String cmd = p[0].toUpperCase();
                String arg = p.length > 1 ? p[1] : "";
                try {
                    if (me == null) {
                        if (cmd.equals("LOGIN")) login(arg);
                        else if (cmd.equals("QUIT")) break;
                        else send("ERR Can LOGIN truoc");
                        if (me != null) socket.setSoTimeout(0);
                        continue;
                    }
                    if (!handle(cmd, arg)) break;
                } catch (DbException e) {
                    System.err.println("[CHAT] Loi CSDL: " + e.getMessage());
                    send("ERR Loi may chu");
                }
            }
        } catch (IOException e) {
            System.err.println("[CHAT] Ket noi " + peer + " ket thuc: " + e.getMessage());
        } finally {
            if (me != null) ChatHub.unregister(me, this);
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[CHAT] Dong ket noi " + peer + (me != null ? " (" + me + ")" : ""));
        }
    }

    private void login(String arg) {
        String[] f = arg.split(" ", 2);
        if (f.length != 2 || !users.authenticate(f[0], f[1])) {
            send("ERR Sai username hoac password");
            return;
        }
        me = UserStore.normalize(f[0]);
        ChatHub.register(me, this);
        send("OK LOGIN");
        send(ChatHub.userListLine(users.allUsernames()));
        for (ChatStore.Msg m : ChatStore.takePending(me)) send(m.line("MSG")); // tin nhan luc offline
        ChatHub.broadcast("ONLINE " + me, me);
        System.out.println("[CHAT] " + me + " online");
    }

    private boolean handle(String cmd, String arg) {
        switch (cmd) {
            case "USERS":
                send(ChatHub.userListLine(users.allUsernames()));
                break;

            case "MSG": {
                String[] p = arg.split(" ", 2);
                String to = UserStore.normalize(p[0]);
                String text = p.length > 1 ? clean(p[1]) : "";
                if (text.isEmpty()) { send("ERR Tin nhan rong"); break; }
                if (to.equals(me) || !users.exists(to)) { send("ERR Nguoi nhan khong hop le: " + p[0]); break; }
                boolean online = ChatHub.isOnline(to);
                ChatStore.Msg m = ChatStore.save(me, to, text, online);
                String line = m.line("MSG");
                if (online) ChatHub.sendTo(to, line);
                send(line);   // echo cho nguoi gui (client chi hien tin da qua server)
                break;
            }

            case "ALL": {
                String text = clean(arg);
                if (text.isEmpty()) { send("ERR Tin nhan rong"); break; }
                ChatStore.Msg m = ChatStore.save(me, null, text, true);
                ChatHub.broadcast(m.line("MSG"), null);   // gui cho tat ca ke ca nguoi gui
                break;
            }

            case "HISTORY": {
                String peer = arg.trim().equals("*") ? "*" : UserStore.normalize(arg);
                for (ChatStore.Msg m : ChatStore.history(me, peer)) send(m.line("HIST"));
                send("HISTEND " + peer);
                break;
            }

            case "PING":
                send("PONG");
                break;

            case "QUIT":
                return false;

            default:
                send("ERR Lenh khong ho tro");
        }
        return true;
    }

    /** Gui 1 dong (co the duoc goi tu thread khac nen can synchronized). */
    synchronized void send(String line) {
        if (out == null) return;
        out.print(line + "\n");
        out.flush();
    }

    void kick(String reason) {
        send("KICK " + reason);
        try { socket.close(); } catch (IOException ignored) {}
    }

    private static String clean(String s) {
        return s.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
