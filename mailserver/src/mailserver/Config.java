package mailserver;

/**
 * Cau hinh chung. Co the doi bang tham so JVM, vi du:
 *   java -Dsmtp.port=25 -Dpop3.port=110 -Ddb.path=data/mail.db ...
 */
public final class Config {
    private Config() {}

    public static final int SMTP_PORT = Integer.getInteger("smtp.port", 2525);
    public static final int POP3_PORT = Integer.getInteger("pop3.port", 1100);
    public static final int CHAT_PORT = Integer.getInteger("chat.port", 5000);

    // File CSDL SQLite (tu tao neu chua co)
    public static final String DB_PATH = System.getProperty("db.path", "mailserver.db");

    // File users.txt cu: neu bang users dang rong, server se nhap tai khoan tu file nay (1 lan)
    public static final String LEGACY_USERS_FILE = "users.txt";

    public static final String MAIL_DOMAIN = "mailclient.local";

    // So client toi da phuc vu dong thoi tren MOI dich vu (SMTP / POP3 / CHAT)
    public static final int MAX_CLIENTS = 200;

    // Ngat ket noi SMTP/POP3 neu client im lang qua lau (giai phong khoa mailbox)
    public static final int SESSION_TIMEOUT_MS = 5 * 60 * 1000;
}
