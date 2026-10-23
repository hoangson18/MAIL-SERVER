package mailserver;

import java.io.IOException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Quan ly tai khoan trong bang users (mat khau luu dang PBKDF2 + salt, khong luu plaintext). */
public class UserStore {

    private static final Pattern NAME = Pattern.compile("[a-z0-9._-]{2,32}");
    private static final SecureRandom RNG = new SecureRandom();

    /** "Son@mailclient.local" -> "son" */
    public static String normalize(String user) {
        if (user == null) return "";
        String u = user.trim().toLowerCase(Locale.ROOT);
        int at = u.indexOf('@');
        return at >= 0 ? u.substring(0, at) : u;
    }

    public boolean exists(String username) {
        String u = normalize(username);
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    public boolean authenticate(String username, String password) {
        String u = normalize(username);
        if (password == null) return false;
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT password_hash, salt FROM users WHERE username = ?")) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String expected = rs.getString(1);
                String actual = hash(password, Base64.getDecoder().decode(rs.getString(2)));
                return java.security.MessageDigest.isEqual(expected.getBytes(), actual.getBytes());
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    /** Dang ky. Tra ve null neu thanh cong, nguoc lai tra ve thong bao loi. */
    public synchronized String register(String username, String password) {
        String u = normalize(username);
        if (!NAME.matcher(u).matches())
            return "Username 2-32 ky tu, chi gom a-z, 0-9, dau . _ -";
        if (password == null || password.length() < 4 || password.chars().anyMatch(Character::isWhitespace))
            return "Mat khau it nhat 4 ky tu va khong co khoang trang";

        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        try (Connection c = Database.get()) {
            c.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users(username, password_hash, salt) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, u);
                    ps.setString(2, hash(password, salt));
                    ps.setString(3, Base64.getEncoder().encodeToString(salt));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); userId = keys.getLong(1); }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO mailboxes(user_id, name) VALUES (?, 'INBOX')")) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
                c.commit();
                System.out.println("[UserStore] Dang ky moi: " + u);
                return null;
            } catch (SQLException e) {
                c.rollback();
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE"))
                    return "Tai khoan da ton tai";
                throw e;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    public List<String> allUsernames() {
        List<String> list = new ArrayList<>();
        try (Connection c = Database.get();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username FROM users ORDER BY username")) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return list;
    }

    public long idOf(String username) {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, normalize(username));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : -1; }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    /** Nhap tai khoan tu users.txt cu (dinh dang user:pass) neu bang users dang rong. */
    public void importLegacyIfEmpty(String file) {
        if (!allUsernames().isEmpty()) return;
        Path p = Paths.get(file);
        if (!Files.exists(p)) return;
        int n = 0;
        try {
            for (String line : Files.readAllLines(p)) {
                line = line.trim();
                int idx = line.indexOf(':');
                if (line.isEmpty() || line.startsWith("#") || idx <= 0) continue;
                String err = register(line.substring(0, idx), line.substring(idx + 1).trim());
                if (err == null) n++;
                else System.out.println("[UserStore] Bo qua '" + line.substring(0, idx) + "': " + err);
            }
        } catch (IOException e) {
            System.err.println("[UserStore] Khong doc duoc " + file + ": " + e.getMessage());
        }
        System.out.println("[UserStore] Da nhap " + n + " tai khoan tu " + file);
    }

    private static String hash(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10_000, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
