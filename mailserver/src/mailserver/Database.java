package mailserver;

import java.sql.*;

/**
 * Ket noi CSDL qua JDBC (SQL Server - Windows Authentication).
 * Driver: mssql-jdbc tai C:\java-libs\mssql-jdbc-12.8.1.jre11.jar
 */
public final class Database {
    private Database() {}

    // SQL Server Authentication (user/pass)
    private static final String URL =
        "jdbc:sqlserver://localhost\\SQLEXPRESS;"
        + "databaseName=" + Config.DB_NAME + ";"
        + "user=sa;"
        + "password=" + Config.DB_PASS + ";"
        + "encrypt=false;"
        + "trustServerCertificate=true;";

    static {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Thieu mssql-jdbc.jar tai C:\\java-libs\\. " +
                "Chay: Copy-Item \"...\\mssql-jdbc-12.8.1.jre11.jar\" \"C:\\java-libs\\\"", e);
        }
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /** Tao bang neu chua ton tai (T-SQL syntax). */
    public static void init() {
        try (Connection c = get(); Statement st = c.createStatement()) {

            st.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='users' AND xtype='U')
                CREATE TABLE users (
                    id            INT IDENTITY(1,1) PRIMARY KEY,
                    username      NVARCHAR(100) NOT NULL UNIQUE,
                    password_hash NVARCHAR(255) NOT NULL,
                    salt          NVARCHAR(255) NOT NULL,
                    created_at    NVARCHAR(50)  NOT NULL DEFAULT (FORMAT(GETDATE(),'yyyy-MM-dd HH:mm:ss'))
                )""");

            st.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='mailboxes' AND xtype='U')
                CREATE TABLE mailboxes (
                    id      INT IDENTITY(1,1) PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name    NVARCHAR(100) NOT NULL DEFAULT 'INBOX',
                    CONSTRAINT uq_mailbox UNIQUE (user_id, name)
                )""");

            st.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='mails' AND xtype='U')
                CREATE TABLE mails (
                    id         INT IDENTITY(1,1) PRIMARY KEY,
                    mailbox_id INT NOT NULL REFERENCES mailboxes(id) ON DELETE CASCADE,
                    sender     NVARCHAR(255) NOT NULL,
                    recipient  NVARCHAR(255) NOT NULL,
                    subject    NVARCHAR(500) NOT NULL DEFAULT '',
                    body       NVARCHAR(MAX) NOT NULL DEFAULT '',
                    sent_at    NVARCHAR(50)  NOT NULL DEFAULT (FORMAT(GETDATE(),'yyyy-MM-dd HH:mm:ss'))
                )""");
            st.execute("""
                IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='idx_mails_mailbox')
                CREATE INDEX idx_mails_mailbox ON mails(mailbox_id)""");

            st.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='chat_messages' AND xtype='U')
                CREATE TABLE chat_messages (
                    id          INT IDENTITY(1,1) PRIMARY KEY,
                    sender_id   INT  NOT NULL REFERENCES users(id) ON DELETE NO ACTION,
                    receiver_id INT  REFERENCES users(id) ON DELETE NO ACTION,
                    content     NVARCHAR(MAX) NOT NULL,
                    sent_at     BIGINT        NOT NULL,
                    delivered   INT           NOT NULL DEFAULT 0
                )""");
            st.execute("""
                IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='idx_chat_pair')
                CREATE INDEX idx_chat_pair ON chat_messages(sender_id, receiver_id)""");
            st.execute("""
                IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='idx_chat_pending')
                CREATE INDEX idx_chat_pending ON chat_messages(receiver_id, delivered)""");

            System.out.println("[DB] San sang: " + Config.DB_HOST + " / " + Config.DB_NAME);
        } catch (SQLException e) {
            throw new DbException("Khong khoi tao duoc CSDL: " + e.getMessage(), e);
        }
    }
}
