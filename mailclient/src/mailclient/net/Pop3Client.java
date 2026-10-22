package mailclient.net;

import java.io.IOException;
import java.util.*;

import mailclient.Config;
import mailclient.model.Mail;

/** Nhan thu qua POP3. Moi thao tac mo 1 phien rieng (login -> lam viec -> QUIT). */
public class Pop3Client {
    private final String host;

    public Pop3Client(String host) {
        this.host = host;
    }

    private Conn login(String username, String password) throws IOException {
        IOException lastError = null;

        // Server khoa mailbox moi user (1 phien/1 luc) -> thu lai vai lan neu gap IN-USE
        for (int attempt = 0; attempt < 5; attempt++) {
            Conn conn = new Conn(host, Config.POP3_PORT);
            try {
                conn.expect("+OK");
                conn.send("USER " + username);
                conn.expect("+OK");
                conn.send("PASS " + password);
                conn.expect("+OK");
                return conn;
            } catch (IOException e) {
                conn.close();
                lastError = e;
                if (e.getMessage() == null || !e.getMessage().contains("IN-USE")) throw e;
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Bị ngắt");
                }
            }
        }
        throw lastError;
    }

    public void register(String username, String password) throws IOException {
        try (Conn conn = new Conn(host, Config.POP3_PORT)) {
            conn.expect("+OK");
            conn.send("REG " + username + " " + password);
            String response = conn.read();
            if (!response.startsWith("+OK")) {
                throw new IOException(response.replaceFirst("^-ERR\\s*", ""));
            }
            try { conn.send("QUIT"); conn.read(); } catch (IOException ignored) {}
        }
    }

    public List<Mail> fetchInbox(String username, String password) throws IOException {
        List<Mail> mails = new ArrayList<>();

        try (Conn conn = login(username, password)) {
            conn.send("LIST");
            conn.expect("+OK");
            String line;
            while (!(line = conn.read()).equals(".")) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 2) {
                    Mail m = new Mail();
                    m.no = Integer.parseInt(p[0]);
                    m.size = Integer.parseInt(p[1]);
                    mails.add(m);
                }
            }

            Map<Integer, String> uids = readUidl(conn);
            for (Mail m : mails) m.uid = uids.getOrDefault(m.no, String.valueOf(m.no));

            for (Mail m : mails) {
                conn.send("RETR " + m.no);
                conn.expect("+OK");
                StringBuilder raw = new StringBuilder();
                while (!(line = conn.read()).equals(".")) {
                    if (line.startsWith("..")) line = line.substring(1); // bo byte-stuffing
                    raw.append(line).append('\n');
                }
                Mail.parse(m, raw.toString());
            }

            quit(conn);
        }
        return mails;
    }

    /** Xoa thu theo UID (on dinh), khong theo so thu tu (so thu tu co the lech giua cac phien). */
    public void deleteMails(String username, String password, Collection<String> uidsToDelete)
            throws IOException {
        try (Conn conn = login(username, password)) {
            Map<Integer, String> uids = readUidl(conn);
            for (Map.Entry<Integer, String> e : uids.entrySet()) {
                if (uidsToDelete.contains(e.getValue())) {
                    conn.send("DELE " + e.getKey());
                    conn.expect("+OK");
                }
            }
            quit(conn); // thu chi bi xoa that khi QUIT (UPDATE state)
        }
    }

    private Map<Integer, String> readUidl(Conn conn) throws IOException {
        Map<Integer, String> map = new LinkedHashMap<>();
        conn.send("UIDL");
        conn.expect("+OK");
        String line;
        while (!(line = conn.read()).equals(".")) {
            String[] p = line.trim().split("\\s+");
            if (p.length >= 2) map.put(Integer.parseInt(p[0]), p[1]);
        }
        return map;
    }

    private void quit(Conn conn) {
        try { conn.send("QUIT"); conn.read(); } catch (IOException ignored) {}
    }
}
