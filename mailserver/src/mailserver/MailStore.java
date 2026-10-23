package mailserver;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Luu/doc/xoa thu trong bang mails (moi thu thuoc 1 mailbox INBOX cua 1 user). */
public final class MailStore {
    private MailStore() {}

    public static final class Mail {
        public long id;
        public String sender, recipient, subject, body, sentAt;

        /** Dang van ban gui cho client qua POP3 RETR (header + dong trong + body). */
        public String render() {
            return "From: " + sender + "\n"
                 + "To: " + recipient + "\n"
                 + "Date: " + sentAt + "\n"
                 + "Subject: " + subject + "\n"
                 + "\n"
                 + body;
        }

        public int size() { return render().getBytes(StandardCharsets.UTF_8).length; }
    }

    /** Chen thu vao INBOX cua username. */
    public static void deliver(String username, String sender, String recipient,
                               String subject, String body) {
        String sql = """
            INSERT INTO mails(mailbox_id, sender, recipient, subject, body, sent_at)
            SELECT mb.id, ?, ?, ?, ?, datetime('now','localtime')
            FROM mailboxes mb JOIN users u ON u.id = mb.user_id
            WHERE u.username = ? AND mb.name = 'INBOX'""";
        try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, recipient);
            ps.setString(3, subject);
            ps.setString(4, body);
            ps.setString(5, UserStore.normalize(username));
            if (ps.executeUpdate() == 0) throw new DbException("Khong co mailbox cho " + username, null);
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    /** Danh sach thu trong INBOX, cu -> moi. */
    public static List<Mail> list(String username) {
        String sql = """
            SELECT m.id, m.sender, m.recipient, m.subject, m.body, m.sent_at
            FROM mails m JOIN mailboxes mb ON mb.id = m.mailbox_id
            JOIN users u ON u.id = mb.user_id
            WHERE u.username = ? AND mb.name = 'INBOX'
            ORDER BY m.id""";
        List<Mail> result = new ArrayList<>();
        try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UserStore.normalize(username));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Mail m = new Mail();
                    m.id = rs.getLong(1);
                    m.sender = rs.getString(2);
                    m.recipient = rs.getString(3);
                    m.subject = rs.getString(4);
                    m.body = rs.getString(5);
                    m.sentAt = rs.getString(6);
                    result.add(m);
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return result;
    }

    /** Xoa 1 thu (chi xoa duoc thu thuoc mailbox cua chinh username). */
    public static void delete(String username, long mailId) {
        String sql = """
            DELETE FROM mails WHERE id = ? AND mailbox_id IN (
                SELECT mb.id FROM mailboxes mb JOIN users u ON u.id = mb.user_id WHERE u.username = ?)""";
        try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mailId);
            ps.setString(2, UserStore.normalize(username));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
