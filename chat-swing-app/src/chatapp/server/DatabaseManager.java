package chatapp.server;

import chatapp.common.Config;
import chatapp.common.Message;
import chatapp.common.MessageType;

import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

/**
 * Quản lý Cơ Sở Dữ Liệu SQL (SQLite Database):
 * - Bảng 'users': Quản lý tài khoản người dùng, tạo tự động khi đăng nhập, lưu IP, port, thời gian tạo và lần đăng nhập cuối.
 * - Bảng 'friendships': Quản lý quan hệ bạn bè (PENDING, ACCEPTED), cho phép kết bạn và hiển thị kênh chat bạn bè.
 * - Bảng 'messages': Lưu trữ tin nhắn theo từng người dùng (Broadcast, Multicast phòng, Chat Riêng bạn bè, File/Ảnh/Video).
 * - Bảng 'rooms': Lưu danh sách phòng chat Multicast.
 * - Thư mục 'uploads': Lưu trữ vĩnh viễn các file, hình ảnh, video, audio voice note.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private final File dataDir;
    private final File uploadsDir;
    private final File dbFile;
    private Connection sqlConnection;
    private boolean isSqlAvailable = false;

    // Fallback cache trong bộ nhớ (dùng khi thiếu JDBC driver)
    private final List<MessageRecord> fallbackMessages = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, UserRecord> fallbackUsers = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> roomSet = Collections.synchronizedSet(new LinkedHashSet<>());
    // user1 -> set of accepted friends
    private final Map<String, Set<String>> fallbackFriends = Collections.synchronizedMap(new HashMap<>());
    // toUser -> set of fromUsers who sent request
    private final Map<String, Set<String>> fallbackPendingRequests = Collections.synchronizedMap(new HashMap<>());

    public static class UserRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        public String username;
        public String ipAddress;
        public int port;
        public long createdAt;
        public long lastLogin;

        public UserRecord() {}

        public UserRecord(String username, String ipAddress, int port, long createdAt, long lastLogin) {
            this.username = username;
            this.ipAddress = ipAddress;
            this.port = port;
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
        }
    }

    public static class MessageRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id;
        public long timestamp;
        public String type;
        public String sender;
        public String receiver;
        public String room;
        public String content;
        public String fileName;
        public long fileSize;

        public MessageRecord() {}

        public MessageRecord(Message msg) {
            this.id = System.currentTimeMillis();
            this.timestamp = msg.getTimestamp();
            this.type = msg.getType() != null ? msg.getType().name() : "TEXT";
            this.sender = msg.getSender();
            this.receiver = msg.getReceiver();
            this.room = msg.getRoom();
            this.content = msg.getContent();
            this.fileName = msg.getFileName();
            this.fileSize = msg.getFileSize();
        }

        public Message toMessage(File uploadsDir) {
            MessageType mType;
            try {
                mType = MessageType.valueOf(this.type);
            } catch (Exception e) {
                mType = MessageType.CHAT_BROADCAST;
            }
            Message msg = new Message(mType, this.sender, this.content);
            msg.setReceiver(this.receiver);
            msg.setRoom(this.room);
            msg.setFileName(this.fileName);
            msg.setFileSize(this.fileSize);
            msg.setTimestamp(this.timestamp);

            if (this.fileName != null && uploadsDir != null && uploadsDir.exists()) {
                File upFile = new File(uploadsDir, this.fileName);
                if (upFile.exists() && upFile.length() < 15 * 1024 * 1024) {
                    try {
                        msg.setFileData(Files.readAllBytes(upFile.toPath()));
                    } catch (IOException ignored) {}
                }
            }
            return msg;
        }
    }

    private DatabaseManager() {
        File dir = new File(Config.DATA_DIR);
        if (!dir.exists() && new File("lap-trinh-mang/chat-swing-app/chat_data").exists()) {
            dir = new File("lap-trinh-mang/chat-swing-app/chat_data");
        } else if (!dir.exists()) {
            dir.mkdirs();
        }
        dataDir = dir;
        uploadsDir = new File(dataDir, "uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }

        dbFile = new File(dataDir, "chat.db");

        // Nạp danh sách phòng mặc định
        for (String r : Config.DEFAULT_ROOMS) {
            roomSet.add(r);
        }

        initSqlDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Khởi tạo kết nối SQL Database (SQLite) và tạo bảng users, friendships, messages, rooms
     */
    private void initSqlDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            String dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            sqlConnection = DriverManager.getConnection(dbUrl);
            isSqlAvailable = true;

            try (Statement stmt = sqlConnection.createStatement()) {
                // 1. Bảng lưu trữ người dùng SQL (tạo tài khoản bằng tên đăng nhập)
                stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                        + "username TEXT PRIMARY KEY, "
                        + "ip_address TEXT, "
                        + "port INTEGER, "
                        + "created_at INTEGER, "
                        + "last_login INTEGER)");

                // Tương thích với db cũ nếu chưa có cột last_login
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN last_login INTEGER");
                } catch (SQLException ignored) {}

                // 2. Bảng quản lý bạn bè (Friendships)
                stmt.execute("CREATE TABLE IF NOT EXISTS friendships ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user1 TEXT, "
                        + "user2 TEXT, "
                        + "status TEXT, " // 'PENDING', 'ACCEPTED'
                        + "created_at INTEGER, "
                        + "UNIQUE(user1, user2))");

                // 3. Bảng lưu trữ tin nhắn SQL (lưu theo sender, receiver, room)
                stmt.execute("CREATE TABLE IF NOT EXISTS messages ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "type TEXT, "
                        + "sender TEXT, "
                        + "receiver TEXT, "
                        + "room TEXT, "
                        + "content TEXT, "
                        + "file_name TEXT, "
                        + "file_size INTEGER, "
                        + "created_at INTEGER)");

                // 4. Bảng lưu trữ phòng chat SQL
                stmt.execute("CREATE TABLE IF NOT EXISTS rooms ("
                        + "room_name TEXT PRIMARY KEY, "
                        + "created_at INTEGER)");

                // XÓA 3 KÊNH HỌC TẬP, CÔNG NGHỆ, GIẢI TRÍ THEO YÊU CẦU NGƯỜI DÙNG
                stmt.execute("DELETE FROM rooms WHERE room_name IN ('Học Tập (Study)', 'Công Nghệ (Tech)', 'Giải Trí (Fun)')");
            }

            roomSet.remove("Học Tập (Study)");
            roomSet.remove("Công Nghệ (Tech)");
            roomSet.remove("Giải Trí (Fun)");

            // Nạp các phòng từ SQL
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT room_name FROM rooms")) {
                while (rs.next()) {
                    String rname = rs.getString("room_name");
                    if (!"Học Tập (Study)".equals(rname) && !"Công Nghệ (Tech)".equals(rname) && !"Giải Trí (Fun)".equals(rname)) {
                        roomSet.add(rname);
                    }
                }
            }

            // Lưu phòng mặc định vào SQL
            for (String r : Config.DEFAULT_ROOMS) {
                try (PreparedStatement ps = sqlConnection.prepareStatement(
                        "INSERT OR IGNORE INTO rooms (room_name, created_at) VALUES (?, ?)")) {
                    ps.setString(1, r);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }

            System.out.println("[Database] Đã kết nối thành công Cơ Sở Dữ Liệu SQL (SQLite: " + dbFile.getName() + ")");
        } catch (Exception e) {
            System.err.println("[Database] Cảnh báo kết nối SQL JDBC: " + e.getMessage() + ". Sử dụng cơ chế Fallback File.");
            isSqlAvailable = false;
        }
    }

    /**
     * Đăng ký hoặc Đăng nhập User vào CSDL SQL
     */
    public synchronized boolean registerOrLoginUser(String username, String ip, int port) {
        long now = System.currentTimeMillis();
        boolean isNew = false;

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String checkSql = "SELECT created_at FROM users WHERE username = ?";
                try (PreparedStatement checkPs = sqlConnection.prepareStatement(checkSql)) {
                    checkPs.setString(1, username);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            // Tài khoản đã có -> Cập nhật đăng nhập
                            String updateSql = "UPDATE users SET ip_address = ?, port = ?, last_login = ? WHERE username = ?";
                            try (PreparedStatement upPs = sqlConnection.prepareStatement(updateSql)) {
                                upPs.setString(1, ip);
                                upPs.setInt(2, port);
                                upPs.setLong(3, now);
                                upPs.setString(4, username);
                                upPs.executeUpdate();
                            }
                            isNew = false;
                        } else {
                            // Tài khoản mới -> Tạo mới vào CSDL
                            String insertSql = "INSERT INTO users (username, ip_address, port, created_at, last_login) VALUES (?, ?, ?, ?, ?)";
                            try (PreparedStatement inPs = sqlConnection.prepareStatement(insertSql)) {
                                inPs.setString(1, username);
                                inPs.setString(2, ip);
                                inPs.setInt(3, port);
                                inPs.setLong(4, now);
                                inPs.setLong(5, now);
                                inPs.executeUpdate();
                            }
                            isNew = true;
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi registerOrLoginUser: " + e.getMessage());
            }
        }

        UserRecord ur = fallbackUsers.get(username);
        if (ur == null) {
            fallbackUsers.put(username, new UserRecord(username, ip, port, now, now));
            isNew = true;
        } else {
            ur.ipAddress = ip;
            ur.port = port;
            ur.lastLogin = now;
        }

        return isNew;
    }

    public synchronized void registerUser(String username) {
        registerOrLoginUser(username, "127.0.0.1", Config.DEFAULT_TCP_PORT);
    }

    // ==================== QUẢN LÝ BẠN BÈ (FRIENDSHIP) ====================

    /**
     * Gửi yêu cầu kết bạn từ fromUser -> toUser
     */
    public synchronized boolean sendFriendRequest(String fromUser, String toUser) {
        if (fromUser == null || toUser == null || fromUser.equalsIgnoreCase(toUser)) {
            return false;
        }
        if (areFriends(fromUser, toUser)) {
            return false; // Đã là bạn bè
        }

        long now = System.currentTimeMillis();
        if (isSqlAvailable && sqlConnection != null) {
            try {
                // Kiểm tra xem đã có bản ghi nào chưa
                String checkSql = "SELECT status FROM friendships WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)";
                try (PreparedStatement ps = sqlConnection.prepareStatement(checkSql)) {
                    ps.setString(1, fromUser);
                    ps.setString(2, toUser);
                    ps.setString(3, toUser);
                    ps.setString(4, fromUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String st = rs.getString("status");
                            if ("ACCEPTED".equalsIgnoreCase(st)) return false;
                        }
                    }
                }

                String insertSql = "INSERT OR REPLACE INTO friendships (user1, user2, status, created_at) VALUES (?, ?, 'PENDING', ?)";
                try (PreparedStatement ps = sqlConnection.prepareStatement(insertSql)) {
                    ps.setString(1, fromUser);
                    ps.setString(2, toUser);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi gửi kết bạn: " + e.getMessage());
            }
        }

        fallbackPendingRequests.computeIfAbsent(toUser, k -> new HashSet<>()).add(fromUser);
        return true;
    }

    /**
     * Chấp nhận lời mời kết bạn
     */
    public synchronized boolean acceptFriendRequest(String fromUser, String toUser) {
        if (fromUser == null || toUser == null) return false;
        long now = System.currentTimeMillis();

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "UPDATE friendships SET status = 'ACCEPTED', created_at = ? "
                        + "WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setLong(1, now);
                    ps.setString(2, fromUser);
                    ps.setString(3, toUser);
                    ps.setString(4, toUser);
                    ps.setString(5, fromUser);
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        // Nếu chưa có dòng nào thì chèn mới trạng thái ACCEPTED
                        String ins = "INSERT OR REPLACE INTO friendships (user1, user2, status, created_at) VALUES (?, ?, 'ACCEPTED', ?)";
                        try (PreparedStatement insPs = sqlConnection.prepareStatement(ins)) {
                            insPs.setString(1, fromUser);
                            insPs.setString(2, toUser);
                            insPs.setLong(3, now);
                            insPs.executeUpdate();
                        }
                    }
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi chấp nhận kết bạn: " + e.getMessage());
            }
        }

        // Fallback
        Set<String> reqs = fallbackPendingRequests.get(toUser);
        if (reqs != null) reqs.remove(fromUser);
        fallbackFriends.computeIfAbsent(fromUser, k -> new HashSet<>()).add(toUser);
        fallbackFriends.computeIfAbsent(toUser, k -> new HashSet<>()).add(fromUser);
        return true;
    }

    /**
     * Từ chối hoặc hủy kết bạn
     */
    public synchronized boolean declineFriendRequest(String fromUser, String toUser) {
        if (fromUser == null || toUser == null) return false;
        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "DELETE FROM friendships WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, fromUser);
                    ps.setString(2, toUser);
                    ps.setString(3, toUser);
                    ps.setString(4, fromUser);
                    ps.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi xóa quan hệ bạn bè: " + e.getMessage());
            }
        }

        Set<String> reqs = fallbackPendingRequests.get(toUser);
        if (reqs != null) reqs.remove(fromUser);
        Set<String> f1 = fallbackFriends.get(fromUser);
        if (f1 != null) f1.remove(toUser);
        Set<String> f2 = fallbackFriends.get(toUser);
        if (f2 != null) f2.remove(fromUser);
        return true;
    }

    /**
     * Lấy danh sách bạn bè đã được chấp nhận (ACCEPTED) của username
     */
    public synchronized List<String> getFriends(String username) {
        List<String> list = new ArrayList<>();
        if (username == null) return list;

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "SELECT user1, user2 FROM friendships WHERE (user1 = ? OR user2 = ?) AND status = 'ACCEPTED'";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String u1 = rs.getString("user1");
                            String u2 = rs.getString("user2");
                            String friend = username.equalsIgnoreCase(u1) ? u2 : u1;
                            if (!list.contains(friend)) {
                                list.add(friend);
                            }
                        }
                    }
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi getFriends: " + e.getMessage());
            }
        }

        Set<String> set = fallbackFriends.get(username);
        if (set != null) list.addAll(set);
        return list;
    }

    /**
     * Lấy danh sách những người đã gửi lời mời kết bạn tới username (chờ duyệt PENDING)
     */
    public synchronized List<String> getPendingRequests(String username) {
        List<String> list = new ArrayList<>();
        if (username == null) return list;

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "SELECT user1 FROM friendships WHERE user2 = ? AND status = 'PENDING'";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(rs.getString("user1"));
                        }
                    }
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi getPendingRequests: " + e.getMessage());
            }
        }

        Set<String> set = fallbackPendingRequests.get(username);
        if (set != null) list.addAll(set);
        return list;
    }

    /**
     * Lấy danh sách những người mà username đã gửi lời mời kết bạn (đang chờ họ duyệt)
     */
    public synchronized List<String> getSentRequests(String username) {
        List<String> list = new ArrayList<>();
        if (username == null) return list;

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "SELECT user2 FROM friendships WHERE user1 = ? AND status = 'PENDING'";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(rs.getString("user2"));
                        }
                    }
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi getSentRequests: " + e.getMessage());
            }
        }

        synchronized (fallbackPendingRequests) {
            for (Map.Entry<String, Set<String>> en : fallbackPendingRequests.entrySet()) {
                if (en.getValue().contains(username)) list.add(en.getKey());
            }
        }
        return list;
    }

    /**
     * Tìm tài khoản theo tên (không phân biệt hoa thường).
     * @return tên tài khoản chính xác trong CSDL, hoặc null nếu không tồn tại
     */
    public synchronized String findUser(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String q = name.trim();
        if (isSqlAvailable && sqlConnection != null) {
            try (PreparedStatement ps = sqlConnection.prepareStatement(
                    "SELECT username FROM users WHERE username = ? COLLATE NOCASE LIMIT 1")) {
                ps.setString(1, q);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("username") : null;
                }
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi findUser: " + e.getMessage());
            }
        }
        for (String u : fallbackUsers.keySet()) {
            if (u.equalsIgnoreCase(q)) return u;
        }
        return null;
    }

    /**
     * Kiểm tra xem 2 người dùng đã là bạn bè chưa
     */
    public synchronized boolean areFriends(String u1, String u2) {
        if (u1 == null || u2 == null) return false;
        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "SELECT id FROM friendships WHERE ((user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)) AND status = 'ACCEPTED'";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, u1);
                    ps.setString(2, u2);
                    ps.setString(3, u2);
                    ps.setString(4, u1);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException ignored) {}
        }
        Set<String> f = fallbackFriends.get(u1);
        return f != null && f.contains(u2);
    }

    /**
     * Kiểm tra xem fromUser đã gửi yêu cầu kết bạn đến toUser chưa
     */
    public synchronized boolean hasPendingRequest(String fromUser, String toUser) {
        if (fromUser == null || toUser == null) return false;
        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "SELECT id FROM friendships WHERE user1 = ? AND user2 = ? AND status = 'PENDING'";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, fromUser);
                    ps.setString(2, toUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException ignored) {}
        }
        Set<String> reqs = fallbackPendingRequests.get(toUser);
        return reqs != null && reqs.contains(fromUser);
    }

    // ==================== LƯU VÀ TRUY VẤN TIN NHẮN ====================

    /**
     * Lưu tin nhắn vào CSDL SQL (Bảng 'messages') và lưu file đính kèm vào 'uploads/'
     */
    public synchronized void saveMessage(Message msg) {
        if (msg == null || msg.getType() == MessageType.CONNECT 
                || msg.getType() == MessageType.CONNECT_ACK 
                || msg.getType() == MessageType.DISCONNECT
                || msg.getType() == MessageType.FRIEND_REQUEST
                || msg.getType() == MessageType.FRIEND_ACCEPT
                || msg.getType() == MessageType.FRIEND_DECLINE
                || msg.getType() == MessageType.FRIEND_LIST) {
            return;
        }

        // Lưu file đính kèm/ảnh/video nếu có
        if (msg.getFileData() != null && msg.getFileName() != null) {
            try {
                File target = new File(uploadsDir, msg.getFileName());
                if (!target.exists()) {
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        fos.write(msg.getFileData());
                    }
                }
            } catch (IOException e) {
                System.err.println("[Database] Lỗi lưu file đính kèm: " + e.getMessage());
            }
        }

        MessageRecord record = new MessageRecord(msg);
        fallbackMessages.add(record);

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "INSERT INTO messages (type, sender, receiver, room, content, file_name, file_size, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, record.type);
                    ps.setString(2, record.sender);
                    ps.setString(3, record.receiver);
                    ps.setString(4, record.room);
                    ps.setString(5, record.content);
                    ps.setString(6, record.fileName);
                    ps.setLong(7, record.fileSize);
                    ps.setLong(8, record.timestamp);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi chèn tin nhắn SQL: " + e.getMessage());
            }
        }
    }

    /**
     * Lấy toàn bộ lịch sử tin nhắn liên quan đến một User cụ thể
     */
    public synchronized List<Message> getMessagesForUser(String username, int limit) {
        List<Message> list = new ArrayList<>();
        if (username == null || username.trim().isEmpty()) {
            return list;
        }

        if (isSqlAvailable && sqlConnection != null) {
            try {
                String sql = "SELECT * FROM ("
                        + "  SELECT * FROM messages "
                        + "  WHERE receiver = 'ALL' "
                        + "     OR (room IS NOT NULL AND room != '') "
                        + "     OR sender = ? "
                        + "     OR receiver = ? "
                        + "  ORDER BY id DESC LIMIT ?"
                        + ") ORDER BY id ASC";
                try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, username);
                    ps.setInt(3, limit);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            MessageRecord r = new MessageRecord();
                            r.id = rs.getLong("id");
                            r.type = rs.getString("type");
                            r.sender = rs.getString("sender");
                            r.receiver = rs.getString("receiver");
                            r.room = rs.getString("room");
                            r.content = rs.getString("content");
                            r.fileName = rs.getString("file_name");
                            r.fileSize = rs.getLong("file_size");
                            r.timestamp = rs.getLong("created_at");

                            list.add(r.toMessage(uploadsDir));
                        }
                    }
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi truy vấn lịch sử cho user " + username + ": " + e.getMessage());
            }
        }

        // Fallback trong bộ nhớ
        synchronized (fallbackMessages) {
            for (MessageRecord r : fallbackMessages) {
                if ("ALL".equalsIgnoreCase(r.receiver)
                        || (r.room != null && !r.room.isEmpty())
                        || username.equalsIgnoreCase(r.sender)
                        || username.equalsIgnoreCase(r.receiver)) {
                    list.add(r.toMessage(uploadsDir));
                }
            }
        }
        if (list.size() > limit) {
            return list.subList(list.size() - limit, list.size());
        }
        return list;
    }

    public synchronized List<Message> getMessagesForUser(String username) {
        return getMessagesForUser(username, 300);
    }

    /**
     * Lấy lịch sử chat theo bộ lọc cho Server GUI
     */
    public synchronized List<MessageRecord> getHistoryFiltered(String userFilter, String scopeFilter, int limit) {
        List<MessageRecord> result = new ArrayList<>();

        if (isSqlAvailable && sqlConnection != null) {
            try {
                StringBuilder sql = new StringBuilder("SELECT * FROM messages WHERE 1=1 ");
                List<Object> params = new ArrayList<>();

                if (userFilter != null && !userFilter.isEmpty() && !"ALL".equalsIgnoreCase(userFilter)) {
                    sql.append("AND (sender = ? OR receiver = ?) ");
                    params.add(userFilter);
                    params.add(userFilter);
                }

                if (scopeFilter != null && !scopeFilter.isEmpty() && !"ALL".equalsIgnoreCase(scopeFilter)) {
                    if ("BROADCAST".equalsIgnoreCase(scopeFilter)) {
                        sql.append("AND receiver = 'ALL' ");
                    } else if ("ROOM".equalsIgnoreCase(scopeFilter)) {
                        sql.append("AND room IS NOT NULL AND room != '' ");
                    } else if ("PRIVATE".equalsIgnoreCase(scopeFilter)) {
                        sql.append("AND type = 'CHAT_PRIVATE' ");
                    } else if ("FILE".equalsIgnoreCase(scopeFilter)) {
                        sql.append("AND file_name IS NOT NULL AND file_name != '' ");
                    }
                }

                sql.append("ORDER BY id DESC LIMIT ?");
                params.add(limit);

                try (PreparedStatement ps = sqlConnection.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        Object p = params.get(i);
                        if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                        else ps.setString(i + 1, p.toString());
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            MessageRecord r = new MessageRecord();
                            r.id = rs.getLong("id");
                            r.type = rs.getString("type");
                            r.sender = rs.getString("sender");
                            r.receiver = rs.getString("receiver");
                            r.room = rs.getString("room");
                            r.content = rs.getString("content");
                            r.fileName = rs.getString("file_name");
                            r.fileSize = rs.getLong("file_size");
                            r.timestamp = rs.getLong("created_at");
                            result.add(r);
                        }
                    }
                }
                return result;
            } catch (SQLException e) {
                System.err.println("[Database SQL] Lỗi truy vấn getHistoryFiltered: " + e.getMessage());
            }
        }

        // Fallback
        synchronized (fallbackMessages) {
            for (int i = fallbackMessages.size() - 1; i >= 0 && result.size() < limit; i--) {
                MessageRecord r = fallbackMessages.get(i);
                boolean matchUser = (userFilter == null || "ALL".equalsIgnoreCase(userFilter)
                        || userFilter.equalsIgnoreCase(r.sender) || userFilter.equalsIgnoreCase(r.receiver));
                boolean matchScope = true;
                if ("BROADCAST".equalsIgnoreCase(scopeFilter)) matchScope = "ALL".equalsIgnoreCase(r.receiver);
                else if ("ROOM".equalsIgnoreCase(scopeFilter)) matchScope = r.room != null && !r.room.isEmpty();
                else if ("PRIVATE".equalsIgnoreCase(scopeFilter)) matchScope = "CHAT_PRIVATE".equalsIgnoreCase(r.type);
                else if ("FILE".equalsIgnoreCase(scopeFilter)) matchScope = r.fileName != null && !r.fileName.isEmpty();

                if (matchUser && matchScope) {
                    result.add(r);
                }
            }
        }
        return result;
    }

    public synchronized List<MessageRecord> getHistory(String target, int limit) {
        return getHistoryFiltered(target, "ALL", limit);
    }

    /**
     * Lấy toàn bộ danh sách UserRecord đã từng tạo trong CSDL SQL
     */
    public synchronized List<UserRecord> getAllUserRecords() {
        List<UserRecord> list = new ArrayList<>();
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement st = sqlConnection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT username, ip_address, port, created_at, last_login FROM users ORDER BY created_at DESC")) {
                while (rs.next()) {
                    list.add(new UserRecord(
                            rs.getString("username"),
                            rs.getString("ip_address"),
                            rs.getInt("port"),
                            rs.getLong("created_at"),
                            rs.getLong("last_login")
                    ));
                }
                return list;
            } catch (SQLException ignored) {}
        }
        return new ArrayList<>(fallbackUsers.values());
    }

    public synchronized Set<String> getAllUsers() {
        Set<String> set = new LinkedHashSet<>();
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement st = sqlConnection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT username FROM users ORDER BY username ASC")) {
                while (rs.next()) {
                    set.add(rs.getString("username"));
                }
                return set;
            } catch (SQLException ignored) {}
        }
        set.addAll(fallbackUsers.keySet());
        return set;
    }

    public synchronized int getTotalMessageCount() {
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement st = sqlConnection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM messages")) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            } catch (SQLException ignored) {}
        }
        return fallbackMessages.size();
    }

    public synchronized void addRoom(String roomName) {
        if (roomName != null && !roomName.trim().isEmpty()) {
            String r = roomName.trim();
            roomSet.add(r);
            if (isSqlAvailable && sqlConnection != null) {
                try {
                    String sql = "INSERT OR IGNORE INTO rooms (room_name, created_at) VALUES (?, ?)";
                    try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                        ps.setString(1, r);
                        ps.setLong(2, System.currentTimeMillis());
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    System.err.println("[Database SQL] Lỗi lưu phòng chat SQL: " + e.getMessage());
                }
            }
        }
    }

    public synchronized List<String> getAllRooms() {
        return new ArrayList<>(roomSet);
    }

    public boolean isSqlAvailable() {
        return isSqlAvailable;
    }

    public File getDataDir() {
        return dataDir;
    }

    public File getUploadsDir() {
        return uploadsDir;
    }
}
