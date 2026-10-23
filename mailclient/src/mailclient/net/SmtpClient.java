package mailclient.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import mailclient.Config;

/** Gui thu qua SMTP: HELO -> AUTH PLAIN -> MAIL FROM -> RCPT TO (nhieu) -> DATA -> QUIT. */
public class SmtpClient {
    private final String host;

    public SmtpClient(String host) {
        this.host = host;
    }

    /** `to` co the la nhieu nguoi nhan, cach nhau boi dau phay / cham phay / khoang trang. */
    public void sendMail(String from, String password, String to,
                         String subject, String body) throws IOException {

        List<String> recipients = new ArrayList<>();
        for (String r : to.split("[,;\\s]+")) {
            if (!r.isEmpty()) recipients.add(r);
        }
        if (recipients.isEmpty()) throw new IOException("Chưa nhập người nhận");

        try (Conn conn = new Conn(host, Config.SMTP_PORT)) {
            conn.expect("220");

            conn.send("HELO client");
            conn.expect("250");

            String plain = "\0" + from + "\0" + password;
            conn.send("AUTH PLAIN " + Base64.getEncoder()
                    .encodeToString(plain.getBytes(StandardCharsets.UTF_8)));
            try {
                conn.expect("235");
            } catch (IOException e) {
                throw new IOException("Xác thực SMTP thất bại: " + clean(e.getMessage()));
            }

            conn.send("MAIL FROM:<" + from + ">");
            conn.expect("250");

            for (String rcpt : recipients) {
                conn.send("RCPT TO:<" + rcpt + ">");
                String response = conn.read();
                if (response.startsWith("550")) {
                    throw new IOException("Không gửi được tới " + rcpt + ": " + clean(response));
                }
                if (!response.startsWith("250")) {
                    throw new IOException(response);
                }
            }

            conn.send("DATA");
            conn.expect("354");

            conn.send("Subject: " + subject.replace('\n', ' '));
            conn.send("");

            for (String line : body.split("\\r?\\n", -1)) {
                conn.send(line.startsWith(".") ? "." + line : line); // dot-stuffing
            }

            conn.send(".");
            conn.expect("250");

            conn.send("QUIT");
            try { conn.read(); } catch (IOException ignored) {}
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceFirst("^\\d{3}\\s*", "");
    }
}
