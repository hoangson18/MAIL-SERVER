package mailserver;

/**
 * Cau hinh chung. Co the doi bang tham so JVM, vi du:
 *   java -Dsmtp.port=25 -Dpop3.port=110 ...
 *   java -Ddb.host=localhost\SQLEXPRESS -Ddb.name=MailDB ...
 */
public final class Config {
    private Config() {}

    public static final int SMTP_PORT = Integer.getInteger("smtp.port", 2525);
    public static final int POP3_PORT = Integer.getInteger("pop3.port", 1100);
    public static final int CHAT_PORT = Integer.getInteger("chat.port", 5000);

    // Cau hinh SQL Server (SQL Server Authentication)
    public static final String DB_HOST     = System.getProperty("db.host", "localhost\\SQLEXPRESS");
    public static final String DB_NAME     = System.getProperty("db.name", "MailDB");
    public static final String DB_PASS     = System.getProperty("db.pass", "YourPassword123");

    // File users.txt cu: neu bang users dang rong, server se nhap tai khoan tu file nay (1 lan)
    public static final String LEGACY_USERS_FILE = "users.txt";

    public static final String MAIL_DOMAIN = "mailclient.local";

    public static final int MAX_CLIENTS = 200;

    public static final int SESSION_TIMEOUT_MS = 5 * 60 * 1000;
}
