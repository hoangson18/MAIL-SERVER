# Mail Client/Server (SMTP + POP3 + Chat) — Java + SQL

Đồ án cuối kỳ Mạng máy tính. Gồm 2 project:

```
mailserver/   Server: SMTP + POP3 + Chat, lưu dữ liệu bằng SQLite qua JDBC
mailclient/   Client: giao diện Swing (Đăng nhập/Đăng ký -> Hộp thư | Trò chuyện)
```

| Dịch vụ | Cổng mặc định | Giao thức |
|---|---|---|
| SMTP (gửi thư) | 2525 | HELO/EHLO, AUTH PLAIN, MAIL FROM, RCPT TO, DATA, RSET, NOOP, QUIT |
| POP3 (nhận thư) | 1100 | USER, PASS, STAT, LIST, UIDL, RETR, TOP, DELE, RSET, NOOP, QUIT + `REG` (đăng ký) |
| Chat real-time | 5000 | Giao thức text tự định nghĩa (xem mục 6) |

---

## 1. Chạy thử nhanh (cần JDK 17 trở lên)

**Server** (máy 1):
```bash
cd mailserver
./build.sh        # Windows: build.bat
./run.sh          # Windows: run.bat
```
Lần chạy đầu server tự tạo file `mailserver.db`, và **tự nhập các tài khoản trong `users.txt` cũ** (son, ban, test, sos111) vào CSDL.

**Client** (máy 2, hoặc mở thêm nhiều cửa sổ để thử nhiều client):
```bash
cd mailclient
./build.sh        # Windows: build.bat
./run.sh          # Windows: run.bat
```
Ở màn hình đăng nhập, nhập **IP của máy chạy server** (cùng máy thì dùng `127.0.0.1`).

**Chạy trên 2 máy:** mở firewall máy server cho 3 cổng 2525, 1100, 5000
(Linux: `sudo ufw allow 2525 && sudo ufw allow 1100 && sudo ufw allow 5000`; Windows: cho phép Java qua Windows Defender Firewall hoặc tạo Inbound Rule cho 3 cổng này). Xem IP máy server bằng `ip a` (Linux) hoặc `ipconfig` (Windows).

Đổi cổng (ví dụ dùng cổng chuẩn 25/110, cần quyền root trên Linux):
```bash
./run.sh -Dsmtp.port=25 -Dpop3.port=110       # server
./run.sh -Dsmtp.port=25 -Dpop3.port=110       # client phải đổi tương ứng
```

---

## 2. Kết nối SQL (SQLite qua JDBC)

### 2.1 Cách hoạt động

Server dùng **SQLite** vì không cần cài đặt gì: driver là file `mailserver/lib/sqlite-jdbc.jar` (đã kèm sẵn) và CSDL là một file `mailserver.db` nằm cạnh nơi chạy server. Lệnh `run.sh` đã có `-cp "out:lib/*"` để nạp driver.

Toàn bộ chỗ nối vào CSDL nằm ở `Database.java`:

```java
Class.forName("org.sqlite.JDBC");                       // nạp driver
Connection c = DriverManager.getConnection("jdbc:sqlite:mailserver.db");
```

Các class khác chỉ gọi `Database.get()` rồi dùng JDBC chuẩn với `PreparedStatement` (chống SQL injection), ví dụ trong `MailStore.java`:

```java
try (Connection c = Database.get();
     PreparedStatement ps = c.prepareStatement("SELECT ... FROM mails WHERE ... = ?")) {
    ps.setString(1, username);
    try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { ... } }
}
```

Mỗi thao tác mở một `Connection` riêng nên an toàn khi nhiều thread (nhiều client) chạy cùng lúc; SQLite được bật chế độ WAL và `busy_timeout` để nhiều client ghi đồng thời không bị lỗi "database is locked".

### 2.2 Cấu trúc CSDL

```
users (id, username UNIQUE, password_hash, salt, created_at)
   │ 1
   ├──< mailboxes (id, user_id -> users, name = 'INBOX')
   │        │ 1
   │        └──< mails (id, mailbox_id -> mailboxes, sender, recipient, subject, body, sent_at)
   │
   └──< chat_messages (id, sender_id -> users, receiver_id -> users | NULL = chat chung, content, sent_at, delivered)
```

- Mật khẩu **không lưu thô**: lưu `PBKDF2-HMAC-SHA256` + salt ngẫu nhiên riêng cho từng tài khoản.
- Đăng ký tài khoản mới tự tạo mailbox `INBOX` cho tài khoản đó (cùng một transaction).
- `chat_messages.delivered = 0` là tin gửi cho người đang offline; khi họ đăng nhập server sẽ giao và đánh dấu `1`.

### 2.3 Xem / kiểm tra dữ liệu

Dùng **DB Browser for SQLite** (miễn phí) mở file `mailserver.db`, hoặc dòng lệnh:

```bash
sqlite3 mailserver.db
sqlite> SELECT id, username, created_at FROM users;
sqlite> SELECT u.username AS nguoi_nhan, m.sender, m.subject, m.sent_at
        FROM mails m JOIN mailboxes b ON b.id = m.mailbox_id
                     JOIN users u ON u.id = b.user_id
        ORDER BY m.id DESC;
sqlite> SELECT s.username AS tu, r.username AS den, c.content
        FROM chat_messages c JOIN users s ON s.id = c.sender_id
                             LEFT JOIN users r ON r.id = c.receiver_id;
```
Muốn reset toàn bộ dữ liệu: dừng server, xóa `mailserver.db*`, chạy lại.

### 2.4 Đổi sang MySQL (nếu đề tài bắt buộc MySQL)

> Lưu ý: phần SQLite đã được test đầy đủ. Phần MySQL dưới đây là hướng dẫn chuyển đổi, **mình chưa chạy thử trên MySQL thật**, bạn nên thử và đối chiếu khi chuyển.

1. Tạo CSDL và bảng trong MySQL:
```sql
CREATE DATABASE mailserver CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mailserver;

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  salt VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE mailboxes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(32) NOT NULL DEFAULT 'INBOX',
  UNIQUE (user_id, name),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE mails (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  mailbox_id BIGINT NOT NULL,
  sender VARCHAR(255) NOT NULL,
  recipient VARCHAR(255) NOT NULL,
  subject VARCHAR(500) NOT NULL DEFAULT '',
  body MEDIUMTEXT NOT NULL,
  sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_mails_mailbox (mailbox_id),
  FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NULL,
  content TEXT NOT NULL,
  sent_at BIGINT NOT NULL,
  delivered TINYINT NOT NULL DEFAULT 0,
  INDEX idx_chat_pair (sender_id, receiver_id),
  INDEX idx_chat_pending (receiver_id, delivered),
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```
2. Tải **MySQL Connector/J** (file `mysql-connector-j-x.y.z.jar`) bỏ vào `mailserver/lib/` (có thể xóa `sqlite-jdbc.jar`).
3. Sửa `Database.java`:
   - `Class.forName("com.mysql.cj.jdbc.Driver");`
   - `DriverManager.getConnection("jdbc:mysql://localhost:3306/mailserver?useUnicode=true&characterEncoding=UTF-8", "root", "mat_khau")`
   - Bỏ 2 lệnh `PRAGMA` trong `get()`, và bỏ toàn bộ phần tạo bảng trong `init()` (đã tạo ở bước 1).
4. Sửa 2 chỗ SQL riêng của SQLite:
   - `MailStore.deliver()`: thay `datetime('now','localtime')` bằng `NOW()`.
   - `UserStore.register()`: thay điều kiện `e.getMessage().contains("UNIQUE")` bằng `e.getErrorCode() == 1062` (lỗi trùng khóa của MySQL).
5. Build lại và chạy như bình thường. Các câu SQL còn lại (JOIN, `INSERT ... SELECT`, `LIMIT`) là SQL chuẩn dùng được trên cả hai.

---

## 3. Đa luồng, đa client

Cả 3 dịch vụ dùng chung class `TcpServer`: một thread lắng nghe `accept()`, mỗi client nhận được đưa vào một **`ThreadPoolExecutor`** (tối đa `Config.MAX_CLIENTS = 200` client đồng thời cho mỗi dịch vụ). Khi vượt ngưỡng, server trả lời "quá tải" rồi đóng kết nối đó thay vì để các client khác bị nghẽn. SMTP/POP3 có timeout 5 phút để giải phóng phiên/khóa mailbox của client bị treo.

Kết quả test (xem mục 8): 50 đăng ký, 150 thư và 50 phiên chat chạy đồng thời đều không mất dữ liệu; 230 kết nối POP3 cùng lúc thì 200 được phục vụ, 30 bị từ chối gọn.

## 4. Luồng của client

```
AuthPanel (Đăng nhập | Đăng ký)
   └─ đăng nhập thành công ─> DashboardPanel
         ├─ Tab "Hộp thư"
         │    ├─ Soạn thư        (gửi qua SMTP, nhiều người nhận cách nhau bằng dấu phẩy)
         │    └─ Hộp thư đến     (POP3: xem, Làm mới, Trả lời, Xóa)
         └─ Tab "Trò chuyện"     (danh sách online/offline, chat riêng, chat chung "Tất cả")
```
Khi có thư mới gửi tới, server đẩy thông báo qua kênh chat nên hộp thư của người nhận **tự làm mới** và tab hiện nhãn "(mới)". Tin nhắn chưa đọc hiện số trên tab "Trò chuyện" và cạnh tên người gửi.

## 5. Thử SMTP / POP3 bằng telnet

SMTP **bắt buộc đăng nhập** (`AUTH PLAIN`) và `MAIL FROM` phải trùng tài khoản đã đăng nhập (chống giả mạo người gửi). Chuỗi base64 của `\0son\01234` là `AHNvbgAxMjM0` (tạo bằng `printf '\0son\01234' | base64`).

```
telnet 127.0.0.1 2525
HELO test
AUTH PLAIN AHNvbgAxMjM0
MAIL FROM:<son>
RCPT TO:<ban>
DATA
Subject: Thu thu
(dòng trống)
Noi dung.
.
QUIT
```
```
telnet 127.0.0.1 1100
USER ban
PASS 1234
STAT
LIST
UIDL
RETR 1
DELE 1
QUIT          <- thư chỉ bị xóa thật khi QUIT
```
Đăng ký bằng lệnh: `REG <username> <password>` (username 2–32 ký tự a-z, 0-9, `. _ -`; mật khẩu tối thiểu 4 ký tự, không có khoảng trắng).

## 6. Giao thức chat

Mỗi dòng là một lệnh, UTF-8.

| Client → Server | Ý nghĩa |
|---|---|
| `LOGIN <user> <pass>` | đăng nhập (→ `OK LOGIN` hoặc `ERR ...`) |
| `MSG <đến> <nội dung>` | chat riêng |
| `ALL <nội dung>` | chat chung cho mọi người đang online |
| `HISTORY <peer>` hoặc `HISTORY *` | lấy 100 tin gần nhất từ CSDL |
| `USERS`, `PING`, `QUIT` | danh sách người dùng, giữ kết nối, thoát |

| Server → Client | Ý nghĩa |
|---|---|
| `MSG <từ> <đến> <epochMs> <nội dung>` | tin nhắn (đến = `*` nếu chat chung) |
| `USERS a:1,b:0` | danh sách người dùng (1 = online) |
| `ONLINE <u>` / `OFFLINE <u>` | có người vào/ra |
| `NEWMAIL <từ> <tiêu đề>` | có thư mới |
| `KICK <lý do>` | tài khoản vừa đăng nhập ở nơi khác |

Tin gửi cho người offline được lưu trong CSDL và giao ngay khi họ đăng nhập lại. Mỗi tài khoản chỉ có 1 phiên chat; đăng nhập lần 2 sẽ đá phiên cũ.

## 7. Những thay đổi so với bản cũ

**Server**
- Thay `users.txt` + file `.eml` bằng CSDL SQLite (JDBC): bảng `users`, `mailboxes`, `mails`, `chat_messages`. Mật khẩu được băm.
- Mới: Chat server (cổng 5000), thông báo thư mới real-time.
- Mới: `AUTH PLAIN` cho SMTP; lệnh `UIDL`, `TOP` cho POP3.
- Thread pool có giới hạn + từ chối khi quá tải (trước đây `newCachedThreadPool` không giới hạn).
- SMTP tách `Subject` đúng chỗ (bản cũ để `Subject` lẫn trong phần nội dung nên client phải đoán).
- Sửa: `REG` nay kiểm tra định dạng username/mật khẩu; chỉ cho gửi tới domain `mailclient.local`.

**Client**
- Giao diện 2 tab chính: **Hộp thư** (Soạn thư, Hộp thư đến) và **Trò chuyện**.
- Thêm: Trả lời, Xóa thư (xóa theo UID nên không xóa nhầm thư khác), gửi nhiều người nhận.
- Mail model gọn lại, bỏ đoạn đoán "khối header thứ hai".

**Lưu ý tương thích**
- Client cũ **không** gửi được thư tới server mới vì thiếu `AUTH`; hãy dùng client trong bộ này.
- Thư `.eml` cũ trong `mailboxes/` **không** được nhập vào CSDL (chỉ nhập tài khoản). Nếu cần giữ, hãy gửi lại hoặc nhờ mình viết thêm bước nhập.

## 8. Đã kiểm thử

Đã build bằng JDK 21 và chạy test tự động trên server thật:
- SMTP: chưa AUTH bị chặn, sai mật khẩu bị từ chối, giả mạo người gửi bị chặn, người nhận không tồn tại / domain ngoài bị `550`, tiếng Việt và dòng bắt đầu bằng dấu `.` nguyên vẹn.
- POP3: `STAT/LIST/UIDL/RETR/TOP`, `DELE` + `RSET`, thư chỉ bị xóa khi `QUIT`, phiên thứ 2 cùng user bị `IN-USE`.
- Chat: tin riêng/chung real-time, tin offline được giao khi đăng nhập, lịch sử từ CSDL, đá phiên cũ, push `NEWMAIL`.
- Tải: 50 đăng ký + 150 thư + 50 chat đồng thời, 230 kết nối vượt ngưỡng.
- Client: lớp mạng (SMTP/POP3/Chat) chạy với server thật; giao diện được render và kiểm tra ở chế độ headless.

**Chưa kiểm thử:** cửa sổ Swing thật trên màn hình (môi trường mình không có display), và MySQL. Bạn hãy chạy thử trên máy của bạn.

## 9. Giới hạn đã biết

- Mật khẩu đi qua mạng dạng thô (SMTP AUTH PLAIN, POP3 PASS, chat LOGIN) vì không có TLS; phù hợp môi trường học tập/LAN, không dùng ngoài Internet.
- POP3 chỉ phục vụ INBOX; chưa có thư mục "Đã gửi", chưa hỗ trợ đính kèm.
- Mỗi thư tối đa 1 MB.
