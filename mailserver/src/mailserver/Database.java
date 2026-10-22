package mailserver;

import java.sql.*;

/**
 * Ket noi CSDL qua JDBC (SQLite). Moi thao tac mo 1 Connection rieng -> an toan khi nhieu thread.
 * Muon dung MySQL: doi URL + driver trong getConnection() va dung schema trong README.
 */
public final class Database {
    private Database() {}

    private static final String URL = "jdbc:sqlite:" + Config.DB_PATH;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Thieu sqlite-jdbc.jar. Hay chay server voi: -cp \"out;lib/*\" (Windows) hoac -cp \"out:lib/*\" (Linux/Mac)", e);
        }
    }

    public static Connection get() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000"); // cho toi 5s neu DB dang bi khoa ghi
        }
        return c;
    }

    /** Tao bang neu chua ton tai. */
    public static void init() {
        try (Connection c = get(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL"); // doc/ghi dong thoi tot hon

            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    salt          TEXT NOT NULL,
                    created_at    TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS mailboxes (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name    TEXT NOT NULL DEFAULT 'INBOX',
                    UNIQUE (user_id, name)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS mails (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    mailbox_id INTEGER NOT NULL REFERENCES mailboxes(id) ON DELETE CASCADE,
                    sender     TEXT NOT NULL,
                    recipient  TEXT NOT NULL,
                    subject    TEXT NOT NULL DEFAULT '',
                    body       TEXT NOT NULL DEFAULT '',
                    sent_at    TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_mails_mailbox ON mails(mailbox_id)");

            // receiver_id = NULL nghia la tin broadcast (chat chung)
            st.execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    receiver_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
                    content     TEXT NOT NULL,
                    sent_at     INTEGER NOT NULL,
                    delivered   INTEGER NOT NULL DEFAULT 0
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chat_pair ON chat_messages(sender_id, receiver_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chat_pending ON chat_messages(receiver_id, delivered)");

            System.out.println("[DB] San sang: " + Config.DB_PATH);
        } catch (SQLException e) {
            throw new DbException("Khong khoi tao duoc CSDL: " + e.getMessage(), e);
        }
    }
}
