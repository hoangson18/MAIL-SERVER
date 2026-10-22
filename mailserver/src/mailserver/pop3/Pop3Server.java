package mailserver.pop3;

import mailserver.Config;
import mailserver.TcpServer;
import mailserver.UserStore;

/** POP3 server: moi client 1 thread trong ExecutorService (xem TcpServer). */
public class Pop3Server extends TcpServer {
    public Pop3Server(UserStore users) {
        super("POP3", Config.POP3_PORT,
              socket -> new Pop3Session(socket, users),
              "-ERR Server qua tai, thu lai sau");
    }
}
