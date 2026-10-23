package mailclient.net;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import mailclient.Config;

/**
 * Ket noi chat toi ChatServer. Co 1 thread doc rieng; moi su kien duoc bao qua Listener
 * (luu y: callback chay tren thread doc, khong phai EDT cua Swing).
 */
public class ChatClient implements Closeable {

    public interface Listener {
        void onUsers(Map<String, Boolean> users);                 // ten -> online?
        void onPresence(String user, boolean online);
        void onMessage(String from, String to, long time, String text, boolean history);
        void onNewMail(String from, String subject);
        void onError(String message);
        void onClosed(String reason);
    }

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private volatile boolean userClosed; // true khi chinh nguoi dung chu dong dong ket noi

    /** Ket noi + dang nhap (dong bo). Nem IOException neu that bai. */
    public void connect(String host, String user, String pass, Listener listener) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, Config.CHAT_PORT), Config.CONNECT_TIMEOUT);
        socket.setKeepAlive(true);
        socket.setSoTimeout(Config.SOCKET_TIMEOUT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        send("LOGIN " + user + " " + pass);
        String reply = in.readLine();
        if (reply == null) throw new IOException("Server đóng kết nối");
        if (!reply.startsWith("OK")) {
            close();
            throw new IOException(reply.replaceFirst("^ERR\\s*", ""));
        }
        socket.setSoTimeout(0);

        Thread reader = new Thread(() -> readLoop(listener), "chat-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public synchronized void send(String line) throws IOException {
        out.write(line.replace('\n', ' ').replace('\r', ' '));
        out.write("\n");
        out.flush();
    }

    private void readLoop(Listener l) {
        String reason = "Mất kết nối tới server chat";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split(" ", 2);
                String arg = p.length > 1 ? p[1] : "";
                switch (p[0]) {
                    case "USERS":
                        l.onUsers(parseUsers(arg));
                        break;
                    case "ONLINE":
                        l.onPresence(arg.trim(), true);
                        break;
                    case "OFFLINE":
                        l.onPresence(arg.trim(), false);
                        break;
                    case "MSG":
                    case "HIST": {
                        String[] f = arg.split(" ", 4);   // from to time text
                        if (f.length == 4) {
                            try {
                                l.onMessage(f[0], f[1], Long.parseLong(f[2]), f[3], p[0].equals("HIST"));
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    }
                    case "NEWMAIL": {
                        String[] f = arg.split(" ", 2);
                        l.onNewMail(f[0], f.length > 1 ? f[1] : "");
                        break;
                    }
                    case "ERR":
                        l.onError(arg);
                        break;
                    case "KICK":
                        reason = arg;
                        break;
                    default:
                        break; // OK, PONG, HISTEND...
                }
            }
        } catch (IOException e) {
            reason = "Mất kết nối: " + e.getMessage();
        } finally {
            if (!userClosed) l.onClosed(reason);
        }
    }

    private static Map<String, Boolean> parseUsers(String arg) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String item : arg.split(",")) {
            int colon = item.lastIndexOf(':');
            if (colon > 0) map.put(item.substring(0, colon), item.substring(colon + 1).trim().equals("1"));
        }
        return map;
    }

    @Override
    public void close() {
        userClosed = true;
        try {
            if (socket != null) {
                try { send("QUIT"); } catch (IOException ignored) {}
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}
