package mailclient.ui;

import java.awt.*;
import java.util.List;
import javax.swing.*;

import mailclient.model.Mail;

/**
 * Giao diện chính sau khi đăng nhập:
 *   [Hộp thư]  -> [Soạn thư] [Hộp thư đến]
 *   [Trò chuyện]
 */
public class DashboardPanel extends JPanel {
    public interface SendListener    { void onSend(String to, String subject, String body); }
    public interface RefreshListener { void onRefresh(); }
    public interface DeleteListener  { void onDelete(Mail mail); }
    public interface LogoutListener  { void onLogout(); }

    private static final int TAB_MAIL = 0;
    private static final int TAB_CHAT = 1;

    private final JLabel userLabel = new JLabel();

    private final JTextField toField = new JTextField();
    private final JTextField subjectField = new JTextField();
    private final JTextArea bodyArea = new JTextArea(12, 40);

    private final DefaultListModel<Mail> listModel = new DefaultListModel<>();
    private final JList<Mail> mailList = new JList<>(listModel);
    private final JTextArea reader = new JTextArea();

    private final JTabbedPane mainTabs = new JTabbedPane();
    private final JTabbedPane mailTabs = new JTabbedPane();
    private final ChatPanel chatPanel;

    public DashboardPanel(SendListener sendListener,
                          RefreshListener refreshListener,
                          DeleteListener deleteListener,
                          LogoutListener logoutListener,
                          ChatPanel chatPanel) {
        this.chatPanel = chatPanel;
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        JButton logoutButton = new JButton("Đăng xuất");
        logoutButton.addActionListener(e -> logoutListener.onLogout());
        top.add(userLabel, BorderLayout.WEST);
        top.add(logoutButton, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        mailTabs.addTab("Soạn thư", createComposePanel(sendListener));
        mailTabs.addTab("Hộp thư đến", createInboxPanel(refreshListener, deleteListener));

        mainTabs.addTab("Hộp thư", mailTabs);
        mainTabs.addTab("Trò chuyện", chatPanel);
        mainTabs.addChangeListener(e -> {
            int i = mainTabs.getSelectedIndex();
            chatPanel.setActive(i == TAB_CHAT);
            if (i == TAB_MAIL && mailTabs.getSelectedIndex() == 1) clearNewMailMark();
        });
        mailTabs.addChangeListener(e -> {
            if (mailTabs.getSelectedIndex() == 1) clearNewMailMark();
        });

        add(top, BorderLayout.NORTH);
        add(mainTabs, BorderLayout.CENTER);
    }

    // ---------- Soạn thư ----------
    private JPanel createComposePanel(SendListener sendListener) {
        JPanel compose = new JPanel(new BorderLayout(6, 6));
        compose.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new GridLayout(2, 2, 6, 6));
        header.add(new JLabel("Người nhận (cách nhau bằng dấu phẩy):"));
        header.add(toField);
        header.add(new JLabel("Tiêu đề:"));
        header.add(subjectField);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);

        compose.add(header, BorderLayout.NORTH);
        compose.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

        JButton sendButton = new JButton("Gửi");
        sendButton.addActionListener(e -> sendListener.onSend(
                toField.getText().trim(), subjectField.getText().trim(), bodyArea.getText()));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(sendButton);
        compose.add(buttons, BorderLayout.SOUTH);
        return compose;
    }

    // ---------- Hộp thư đến ----------
    private JPanel createInboxPanel(RefreshListener refreshListener, DeleteListener deleteListener) {
        JPanel inbox = new JPanel(new BorderLayout(6, 6));
        inbox.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> refreshListener.onRefresh());

        JButton replyButton = new JButton("Trả lời");
        replyButton.addActionListener(e -> reply());

        JButton deleteButton = new JButton("Xóa");
        deleteButton.addActionListener(e -> {
            Mail mail = mailList.getSelectedValue();
            if (mail == null) return;
            int ok = JOptionPane.showConfirmDialog(this, "Xóa thư \"" + subjectOf(mail) + "\"?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) deleteListener.onDelete(mail);
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.add(refreshButton);
        toolbar.add(replyButton);
        toolbar.add(deleteButton);

        reader.setEditable(false);
        reader.setLineWrap(true);
        reader.setWrapStyleWord(true);

        mailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mailList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                Mail m = (Mail) value;
                return super.getListCellRendererComponent(list,
                        shortName(m.from) + " — " + subjectOf(m), index, selected, focused);
            }
        });
        mailList.addListSelectionListener(e -> {
            Mail m = mailList.getSelectedValue();
            if (m == null || e.getValueIsAdjusting()) return;
            reader.setText("Từ: " + m.from
                    + "\nĐến: " + m.to
                    + "\nNgày: " + m.date
                    + "\nTiêu đề: " + m.subject
                    + "\n" + "-".repeat(50) + "\n"
                    + m.body);
            reader.setCaretPosition(0);
        });

        JScrollPane listScroll = new JScrollPane(mailList);
        listScroll.setPreferredSize(new Dimension(260, 0));

        inbox.add(toolbar, BorderLayout.NORTH);
        inbox.add(listScroll, BorderLayout.WEST);
        inbox.add(new JScrollPane(reader), BorderLayout.CENTER);
        return inbox;
    }

    private void reply() {
        Mail m = mailList.getSelectedValue();
        if (m == null) return;
        toField.setText(shortName(m.from));
        String s = subjectOf(m);
        subjectField.setText(s.regionMatches(true, 0, "Re:", 0, 3) ? s : "Re: " + s);
        bodyArea.setText("\n\n--- " + m.from + " đã viết ---\n" + m.body);
        bodyArea.setCaretPosition(0);
        mailTabs.setSelectedIndex(0);
        bodyArea.requestFocusInWindow();
    }

    private static String shortName(String address) {
        int at = address.indexOf('@');
        return at > 0 ? address.substring(0, at) : address;
    }

    private static String subjectOf(Mail m) {
        return m.subject.isEmpty() ? "(không tiêu đề)" : m.subject;
    }

    // ---------- API cho MainFrame ----------
    public void setUser(String username, String host) {
        userLabel.setText("Đăng nhập: " + username + " @ " + host);
    }

    /** Hiển thị thư (mới nhất lên đầu), cố giữ lại thư đang chọn. */
    public void showMails(List<Mail> mails) {
        Mail selected = mailList.getSelectedValue();
        String keepUid = selected == null ? null : selected.uid;

        listModel.clear();
        for (int i = mails.size() - 1; i >= 0; i--) listModel.addElement(mails.get(i));

        reader.setText("");
        if (keepUid != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (keepUid.equals(listModel.get(i).uid)) {
                    mailList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /** Đánh dấu có thư mới trên tiêu đề tab (khi người dùng chưa xem Hộp thư đến). */
    public void markNewMail() {
        boolean viewing = mainTabs.getSelectedIndex() == TAB_MAIL && mailTabs.getSelectedIndex() == 1;
        if (!viewing) {
            mailTabs.setTitleAt(1, "Hộp thư đến (mới)");
            mainTabs.setTitleAt(TAB_MAIL, "Hộp thư (mới)");
        }
    }

    private void clearNewMailMark() {
        mailTabs.setTitleAt(1, "Hộp thư đến");
        mainTabs.setTitleAt(TAB_MAIL, "Hộp thư");
    }

    public void setUnreadChat(int total) {
        mainTabs.setTitleAt(TAB_CHAT, total > 0 ? "Trò chuyện (" + total + ")" : "Trò chuyện");
    }

    public void clearCompose() {
        toField.setText("");
        subjectField.setText("");
        bodyArea.setText("");
    }

    public void clearInbox() {
        listModel.clear();
        reader.setText("");
    }

    public void resetView() {
        clearCompose();
        clearInbox();
        clearNewMailMark();
        setUnreadChat(0);
        mainTabs.setSelectedIndex(TAB_MAIL);
        mailTabs.setSelectedIndex(0);
    }
}
