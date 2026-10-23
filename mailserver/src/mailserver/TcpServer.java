package mailserver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Server TCP da luong dung chung cho SMTP / POP3 / CHAT.
 * Accept loop + ExecutorService (toi da Config.MAX_CLIENTS client dong thoi, moi client 1 thread).
 * Khi qua tai se tra loi "busy" va dong ket noi thay vi lam nghen server.
 */
public class TcpServer implements Runnable {

    private final String name;
    private final int port;
    private final Function<Socket, Runnable> sessionFactory;
    private final String busyReply;
    private final ExecutorService pool;

    public TcpServer(String name, int port, Function<Socket, Runnable> sessionFactory, String busyReply) {
        this.name = name;
        this.port = port;
        this.sessionFactory = sessionFactory;
        this.busyReply = busyReply;

        AtomicInteger counter = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(
                0, Config.MAX_CLIENTS, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, name + "-worker-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    @Override
    public void run() {
        // bind 0.0.0.0 -> nhan duoc ket noi tu may khac trong mang
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            System.out.println("[" + name + "] Dang lang nghe tai port " + port);
            while (true) {
                Socket client = server.accept();
                client.setKeepAlive(true);
                try {
                    pool.execute(sessionFactory.apply(client));
                } catch (RejectedExecutionException full) {
                    reject(client);
                }
            }
        } catch (IOException e) {
            System.err.println("[" + name + "] Loi server: " + e.getMessage());
        }
    }

    private void reject(Socket client) {
        try (client) {
            OutputStream o = client.getOutputStream();
            o.write((busyReply + "\r\n").getBytes());
            o.flush();
        } catch (IOException ignored) {}
        System.err.println("[" + name + "] Qua tai, tu choi " + client.getRemoteSocketAddress());
    }
}
