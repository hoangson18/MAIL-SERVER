package mailserver.chat;

import mailserver.Config;
import mailserver.TcpServer;
import mailserver.UserStore;

/** Chat server: moi client 1 thread trong ExecutorService (xem TcpServer). */
public class ChatServer extends TcpServer {
    public ChatServer(UserStore users) {
        super("CHAT", Config.CHAT_PORT,
              socket -> new ChatSession(socket, users),
              "ERR Server qua tai, thu lai sau");
    }
}
