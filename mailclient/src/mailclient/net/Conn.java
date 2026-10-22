package mailclient.net;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import mailclient.Config;

public class Conn implements Closeable {
    public final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;

    public Conn(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(
                new InetSocketAddress(host, port),
                Config.CONNECT_TIMEOUT
        );
        socket.setSoTimeout(Config.SOCKET_TIMEOUT);

        in = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        out = new BufferedWriter(
                new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.UTF_8
                )
        );
    }

    public String read() throws IOException {
        String line = in.readLine();

        if (line == null) {
            throw new IOException("Server đã đóng kết nối");
        }

        System.out.println("S: " + line);
        return line;
    }

    public void send(String line) throws IOException {
        String shown = line;

        if (line.startsWith("PASS ") || line.startsWith("REG ")) {
            shown = line.split(" ")[0] + " ****";
        }

        System.out.println("C: " + shown);

        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    public String expect(String prefix) throws IOException {
        String line = read();

        if (!line.startsWith(prefix)) {
            throw new IOException(line);
        }

        return line;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}