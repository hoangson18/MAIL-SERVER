package mailserver;

import mailserver.chat.ChatServer;
import mailserver.pop3.Pop3Server;
import mailserver.smtp.SmtpServer;

/**
 * Diem khoi dong: khoi tao CSDL, roi chay 3 dich vu (SMTP, POP3, CHAT),
 * moi dich vu 1 thread accept + 1 ExecutorService phuc vu client.
 */
public class MailServer {

    public static void main(String[] args) throws InterruptedException {
        try {
            Database.init();
            UserStore users = new UserStore();
            users.importLegacyIfEmpty(Config.LEGACY_USERS_FILE);

            Thread smtp = new Thread(new SmtpServer(users), "SMTP-Server");
            Thread pop3 = new Thread(new Pop3Server(users), "POP3-Server");
            Thread chat = new Thread(new ChatServer(users), "CHAT-Server");
            smtp.start();
            pop3.start();
            chat.start();

            System.out.println("=== Mail Server dang chay ===");
            System.out.println("SMTP: " + Config.SMTP_PORT + " | POP3: " + Config.POP3_PORT + " | CHAT: " + Config.CHAT_PORT);
            System.out.println("Nhan Ctrl+C de dung server.");

            smtp.join();
            pop3.join();
            chat.join();
        } catch (DbException e) {
            System.err.println("Khong the khoi dong server: " + e.getMessage());
            System.exit(1);
        }
    }
}
