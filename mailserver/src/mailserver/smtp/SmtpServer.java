package mailserver.smtp;

import mailserver.Config;
import mailserver.TcpServer;
import mailserver.UserStore;

/** SMTP server: moi client 1 thread trong ExecutorService (xem TcpServer). */
public class SmtpServer extends TcpServer {
    public SmtpServer(UserStore users) {
        super("SMTP", Config.SMTP_PORT,
              socket -> new SmtpSession(socket, users),
              "421 Server qua tai, thu lai sau");
    }
}
