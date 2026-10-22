package mailserver.chat;

import mailserver.Database;
import mailserver.DbException;
import mailserver.UserStore;

import java.sql.*;
import java.util.*;

/** Luu tin nhan chat trong bang chat_messages (receiver_id NULL = tin broadcast cho moi nguoi). */
public final class ChatStore {
    private ChatStore() {}

    public static final class Msg {
        public String from, to;     // to = "*" neu la broadcast
        public long time;
        public String text;

        /** Dong gui cho client: MSG <from> <to> <epochMillis> <text> */
        public String line(String prefix) {
            return prefix + " " + from + " " + to + " " + time + " " + text;
        }
    }

    public static Msg save(String from, String toOrNull, String text, boolean delivered) {
        Msg m = new Msg();
        m.from = from;
        m.to = toOrNull == null ? "*" : toOrNull;
        m.time = System.currentTimeMillis();
        m.text = text;
        String sql = """
            INSERT INTO chat_messages(sender_id, receiver_id, content, sent_at, delivered)
            VALUES ((SELECT id FROM users WHERE username = ?),
                    (SELECT id FROM users WHERE username = ?), ?, ?, ?)""";
        try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, toOrNull);   // NULL -> receiver_id NULL (subquery tra NULL)
            ps.setString(3, text);
            ps.setLong(4, m.time);
            ps.setInt(5, delivered ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return m;
    }

    /** Lich su 100 tin gan nhat. peer = "*" -> chat chung, nguoc lai -> hoi thoai 2 nguoi. */
    public static List<Msg> history(String me, String peer) {
        boolean all = "*".equals(peer);
        String sql = all
            ? """
              SELECT s.username, NULL, m.content, m.sent_at FROM chat_messages m
              JOIN users s ON s.id = m.sender_id
              WHERE m.receiver_id IS NULL ORDER BY m.id DESC LIMIT 100"""
            : """
              SELECT s.username, r.username, m.content, m.sent_at FROM chat_messages m
              JOIN users s ON s.id = m.sender_id JOIN users r ON r.id = m.receiver_id
              WHERE (s.username = ? AND r.username = ?) OR (s.username = ? AND r.username = ?)
              ORDER BY m.id DESC LIMIT 100""";
        List<Msg> list = new ArrayList<>();
        try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (!all) { ps.setString(1, me); ps.setString(2, peer); ps.setString(3, peer); ps.setString(4, me); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Msg m = new Msg();
                    m.from = rs.getString(1);
                    m.to = all ? "*" : rs.getString(2);
                    m.text = rs.getString(3);
                    m.time = rs.getLong(4);
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        Collections.reverse(list);  // cu -> moi
        return list;
    }

    /** Tin gui rieng cho `me` luc offline (chua delivered); danh dau da giao. */
    public static List<Msg> takePending(String me) {
        List<Msg> list = new ArrayList<>();
        String sel = """
            SELECT m.id, s.username, m.content, m.sent_at FROM chat_messages m
            JOIN users s ON s.id = m.sender_id JOIN users r ON r.id = m.receiver_id
            WHERE r.username = ? AND m.delivered = 0 ORDER BY m.id""";
        try (Connection c = Database.get()) {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sel)) {
                ps.setString(1, me);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                        Msg m = new Msg();
                        m.from = rs.getString(2);
                        m.to = me;
                        m.text = rs.getString(3);
                        m.time = rs.getLong(4);
                        list.add(m);
                    }
                }
            }
            try (PreparedStatement up = c.prepareStatement("UPDATE chat_messages SET delivered = 1 WHERE id = ?")) {
                for (long id : ids) { up.setLong(1, id); up.addBatch(); }
                up.executeBatch();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return list;
    }
}
