package mailclient.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javax.swing.*;

import mailclient.model.Mail;
import mailclient.net.Pop3Client;
import mailclient.net.SmtpClient;

/**
 * Luồng: AuthPanel (Đăng nhập / Đăng ký) -> DashboardPanel (Hộp thư + Trò chuyện).
 * Thao tác mạng chạy nền bằng SwingWorker để không làm treo giao diện.
 */
public class MainFrame extends JFrame {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel root = new JPanel(cardLayout);
    private final JLabel status = new JLabel(" Sẵn sàng");

    private final AuthPanel authPanel;
    private final DashboardPanel dashboardPanel;
    private final ChatPanel chatPanel;

    private String host;
    private String username;
    private String password;

    public MainFrame() {
        super("Mail Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        authPanel = new AuthPanel(this::login, this::register);

        chatPanel = new ChatPanel(new ChatPanel.EventListener() {
            @Override
            public void onNewMail(String from, String subject) {
                // Server báo có thư mới -> tải lại hộp thư ngay (real-time)
                dashboardPanel.markNewMail();
                setStatus("Thư mới từ " + from + (subject.isEmpty() ? "" : ": " + subject));
                refreshInbox();
            }

            @Override
            public void onUnreadChanged(int total) {
                if (dashboardPanel != null) dashboardPanel.setUnreadChat(total);
            }
        });

        dashboardPanel = new DashboardPanel(
                this::sendMail, this::refreshInbox, this::deleteMail, this::logout, chatPanel);

        root.add(authPanel, "auth");
        root.add(dashboardPanel, "dashboard");

        setLayout(new BorderLayout());
        add(root, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                chatPanel.disconnect();
            }
        });

        setSize(820, 560);
        setLocationRelativeTo(null);
    }

    private void login(String host, String username, String password) {
        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            setStatus("Nhập đủ IP, user, mật khẩu");
            return;
        }

        async("Đang đăng nhập", () -> new Pop3Client(host).fetchInbox(username, password), mails -> {
            this.host = host;
            this.username = username;
            this.password = password;

            dashboardPanel.resetView();
            dashboardPanel.setUser(username, host);
            dashboardPanel.showMails(mails);
            cardLayout.show(root, "dashboard");
            setStatus("Đăng nhập thành công, " + mails.size() + " thư");

            chatPanel.connect(host, username, password); // mở kênh chat sau khi đăng nhập mail
        });
    }

    private void register(String host, String username, String password) {
        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            setStatus("Nhập đủ IP, user, mật khẩu");
            return;
        }

        async("Đang đăng ký", () -> {
            new Pop3Client(host).register(username, password);
            return true;
        }, ignored -> {
            setStatus("Đăng ký thành công, hãy đăng nhập");
            JOptionPane.showMessageDialog(this, "Đăng ký thành công! Hãy đăng nhập.");
            authPanel.setLoginFields(host, username);
        });
    }

    private void sendMail(String to, String subject, String body) {
        if (to.isEmpty()) {
            setStatus("Chưa nhập người nhận");
            return;
        }

        async("Đang gửi thư", () -> {
            new SmtpClient(host).sendMail(username, password, to, subject, body);
            return true;
        }, ignored -> {
            dashboardPanel.clearCompose();
            setStatus("Đã gửi thư tới " + to);
        });
    }

    private void refreshInbox() {
        if (host == null) return;
        async("Đang tải hộp thư", () -> new Pop3Client(host).fetchInbox(username, password), mails -> {
            dashboardPanel.showMails(mails);
            setStatus("Hộp thư có " + mails.size() + " thư");
        });
    }

    private void deleteMail(Mail mail) {
        async("Đang xóa thư", () -> {
            new Pop3Client(host).deleteMails(username, password, List.of(mail.uid));
            return new Pop3Client(host).fetchInbox(username, password);
        }, mails -> {
            dashboardPanel.showMails(mails);
            setStatus("Đã xóa thư. Hộp thư còn " + mails.size() + " thư");
        });
    }

    private void logout() {
        chatPanel.disconnect();
        dashboardPanel.resetView();
        host = username = password = null;
        cardLayout.show(root, "auth");
        setStatus("Đã đăng xuất");
    }

    private void setStatus(String message) {
        status.setText(" " + message);
    }

    private <T> void async(String busyMessage, Callable<T> job, Consumer<T> success) {
        setStatus(busyMessage + "...");

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return job.call();
            }

            @Override
            protected void done() {
                try {
                    success.accept(get());
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    String message = cause.getMessage();
                    if (message == null || message.isEmpty()) message = cause.toString();
                    message = message.replaceFirst("^-ERR\\s*", "");

                    setStatus("Lỗi: " + message);
                    JOptionPane.showMessageDialog(MainFrame.this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
