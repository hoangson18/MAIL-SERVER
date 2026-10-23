package mailclient.ui;

import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;

import mailclient.net.ChatClient;

/**
 * Tab "Trò chuyện": danh sách người dùng (online/offline) + khung hội thoại.
 * Tin nhắn đi qua ChatServer nên người nhận thấy ngay (real-time); người offline sẽ nhận khi đăng nhập lại.
 * Mọi thao tác cập nhật UI đều chạy trên EDT.
 */
public class ChatPanel extends JPanel {

    public interface EventListener {
        void onNewMail(String from, String subject);
        void onUnreadChanged(int total);
    }

    private static final String ALL = "*";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventListener events;
    private volatile ChatClient client;

    private String host, me, pass;
    private boolean active;           // tab Trò chuyện có đang hiển thị không
    private boolean updatingList;     // chặn đệ quy khi tự cập nhật danh sách

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> contacts = new JList<>(listModel);
    private final Map<String, Boolean> online = new HashMap<>();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Map<String, List<String>> convo = new HashMap<>();
    private final Set<String> historyRequested = new HashSet<>();
    private String selected = ALL;

    private final JTextArea transcript = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton sendButton = new JButton("Gửi");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton reconnectButton = new JButton("Kết nối lại");
    private final JLabel titleLabel = new JLabel("Tất cả mọi người");

    public ChatPanel(EventListener events) {
        this.events = events;
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- thanh trạng thái phía trên ---
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(statusLabel, BorderLayout.CENTER);
        reconnectButton.setVisible(false);
        reconnectButton.addActionListener(e -> {
            if (host != null) connect(host, me, pass);
        });
        top.add(reconnectButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // --- danh sách liên hệ ---
        contacts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contacts.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                String key = (String) value;
                String text;
                boolean isOnline = ALL.equals(key) || online.getOrDefault(key, false);
                if (ALL.equals(key)) text = "# Tất cả mọi người";
                else text = (isOnline ? "● " : "○ ") + key;
                int n = unread.getOrDefault(key, 0);
                if (n > 0) text += "  (" + n + ")";
                JLabel l = (JLabel) super.getListCellRendererComponent(list, text, index, sel, focus);
                if (!sel) l.setForeground(isOnline ? new Color(0, 110, 40) : Color.GRAY);
                if (n > 0) l.setFont(l.getFont().deriveFont(Font.BOLD));
                return l;
            }
        });
        contacts.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingList) return;
            String key = contacts.getSelectedValue();
            if (key != null) selectKey(key);
        });
        JScrollPane contactScroll = new JScrollPane(contacts);
        contactScroll.setPreferredSize(new Dimension(190, 0));
        add(contactScroll, BorderLayout.WEST);

        // --- khung hội thoại ---
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        center.add(titleLabel, BorderLayout.NORTH);
        center.add(new JScrollPane(transcript), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);
        center.add(bottom, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        input.addActionListener(e -> sendCurrent());
        sendButton.addActionListener(e -> sendCurrent());
        setInputEnabled(false);
        resetState();
    }

    // ====== API cho MainFrame / DashboardPanel ======

    public void connect(String host, String user, String pass) {
        disconnect();
        this.host = host;
        this.me = user;
        this.pass = pass;
        resetState();
        reconnectButton.setVisible(false);
        setStatus("Đang kết nối chat...");

        ChatClient c = new ChatClient();
        client = c;
        new Thread(() -> {
            try {
                c.connect(host, user, pass, new ChatClient.Listener() {
                    @Override public void onUsers(Map<String, Boolean> users) {
                        SwingUtilities.invokeLater(() -> applyUsers(users));
                    }
                    @Override public void onPresence(String u, boolean on) {
                        SwingUtilities.invokeLater(() -> applyPresence(u, on));
                    }
                    @Override public void onMessage(String from, String to, long t, String text, boolean hist) {
                        SwingUtilities.invokeLater(() -> applyMessage(from, to, t, text, hist));
                    }
                    @Override public void onNewMail(String from, String subject) {
                        SwingUtilities.invokeLater(() -> events.onNewMail(from, subject));
                    }
                    @Override public void onError(String message) {
                        SwingUtilities.invokeLater(() -> setStatus("Server: " + message));
                    }
                    @Override public void onClosed(String reason) {
                        SwingUtilities.invokeLater(() -> {
                            if (client != c) return; // phiên cũ, bỏ qua
                            setInputEnabled(false);
                            setStatus("Đã ngắt chat: " + reason);
                            reconnectButton.setVisible(true);
                        });
                    }
                });
                SwingUtilities.invokeLater(() -> {
                    if (client != c) return;
                    setStatus("Đã kết nối chat với tư cách " + user);
                    setInputEnabled(true);
                    selectKey(ALL);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    if (client != c) return;
                    client = null;
                    setStatus("Không kết nối được chat: " + e.getMessage());
                    reconnectButton.setVisible(true);
                });
            }
        }, "chat-connect").start();
    }

    public void disconnect() {
        ChatClient c = client;
        client = null;
        if (c != null) c.close();
        setInputEnabled(false);
    }

    /** Dashboard báo tab Trò chuyện đang được xem hay không (để tính tin chưa đọc). */
    public void setActive(boolean active) {
        this.active = active;
        if (active && unread.remove(selected) != null) {
            contacts.repaint();
            fireUnread();
        }
    }

    // ====== xử lý sự kiện (EDT) ======

    private void applyUsers(Map<String, Boolean> users) {
        online.clear();
        online.putAll(users);
        online.remove(me);
        rebuildList(online.keySet());
    }

    private void applyPresence(String user, boolean on) {
        if (user.equals(me)) return;
        online.put(user, on);
        rebuildList(online.keySet());
    }

    private void rebuildList(Collection<String> names) {
        updatingList = true;
        try {
            TreeSet<String> sorted = new TreeSet<>(names);
            for (String k : convo.keySet()) if (!ALL.equals(k) && !k.equals(me)) sorted.add(k);
            listModel.clear();
            listModel.addElement(ALL);
            for (String n : sorted) listModel.addElement(n);
            contacts.setSelectedValue(selected, true);
        } finally {
            updatingList = false;
        }
    }

    private void applyMessage(String from, String to, long time, String text, boolean history) {
        String key = ALL.equals(to) ? ALL : (from.equals(me) ? to : from);
        String line = "[" + TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())) + "] "
                + (from.equals(me) ? "Bạn" : from) + ": " + text;

        convo.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        if (!ALL.equals(key) && !listModel.contains(key)) {
            online.putIfAbsent(key, false);
            rebuildList(online.keySet());
        }

        if (key.equals(selected)) {
            transcript.append(line + "\n");
            transcript.setCaretPosition(transcript.getDocument().getLength());
        }
        boolean visibleNow = key.equals(selected) && active;
        if (!history && !from.equals(me) && !visibleNow) {
            unread.merge(key, 1, Integer::sum);
            contacts.repaint();
            fireUnread();
        }
    }

    private void selectKey(String key) {
        selected = key;
        titleLabel.setText(ALL.equals(key) ? "Tất cả mọi người" : "Trò chuyện với " + key);
        if (unread.remove(key) != null) fireUnread();

        updatingList = true;
        contacts.setSelectedValue(key, true);
        updatingList = false;
        contacts.repaint();

        transcript.setText("");
        ChatClient c = client;
        if (historyRequested.add(key) && c != null) {
            convo.put(key, new ArrayList<>());     // lịch sử từ server sẽ điền lại
            try {
                c.send("HISTORY " + key);
            } catch (IOException e) {
                setStatus("Lỗi gửi: " + e.getMessage());
            }
        } else {
            for (String l : convo.getOrDefault(key, List.of())) transcript.append(l + "\n");
            transcript.setCaretPosition(transcript.getDocument().getLength());
        }
        input.requestFocusInWindow();
    }

    private void sendCurrent() {
        String text = input.getText().trim();
        ChatClient c = client;
        if (text.isEmpty() || c == null) return;
        try {
            c.send(ALL.equals(selected) ? "ALL " + text : "MSG " + selected + " " + text);
            input.setText("");
        } catch (IOException e) {
            setStatus("Lỗi gửi: " + e.getMessage());
        }
    }

    // ====== tiện ích ======

    private void resetState() {
        online.clear();
        unread.clear();
        convo.clear();
        historyRequested.clear();
        selected = ALL;
        listModel.clear();
        listModel.addElement(ALL);
        transcript.setText("");
        titleLabel.setText("Tất cả mọi người");
        fireUnread();
    }

    private void fireUnread() {
        int total = 0;
        for (int n : unread.values()) total += n;
        events.onUnreadChanged(total);
    }

    private void setInputEnabled(boolean enabled) {
        input.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void setStatus(String text) {
        statusLabel.setText(" " + text);
    }
}
