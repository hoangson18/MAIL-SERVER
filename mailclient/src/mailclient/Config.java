package mailclient;

public final class Config {
    private Config() {}

    // Phai trung voi cau hinh server (co the doi bang -Dsmtp.port=... khi chay client)
    public static final int SMTP_PORT = Integer.getInteger("smtp.port", 2525);
    public static final int POP3_PORT = Integer.getInteger("pop3.port", 1100);
    public static final int CHAT_PORT = Integer.getInteger("chat.port", 5000);

    public static final int CONNECT_TIMEOUT = 5000;
    public static final int SOCKET_TIMEOUT = 10000;
}
