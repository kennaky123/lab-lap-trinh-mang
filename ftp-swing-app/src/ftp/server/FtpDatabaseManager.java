package ftp.server;

import ftp.common.FtpConfig;
import ftp.common.FtpUser;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Quản lý Cơ Sở Dữ Liệu Máy Chủ FTP bằng SQLite (ftp_data/ftp.db):
 * - Bảng 'ftp_users': Lưu trữ danh sách người dùng và phân quyền (Read, Write, Delete)
 * - Bảng 'ftp_audit_logs': Lưu trữ lịch sử tải lên/tải xuống và thao tác lệnh (Audit Log)
 */
public class FtpDatabaseManager {
    private static FtpDatabaseManager instance;

    private final File dataDir;
    private final File dbFile;
    private final File usersFile;
    private final File auditLogFile;

    private Connection sqlConnection;
    private boolean isSqlAvailable = false;

    private final Map<String, FtpUser> userMap = new ConcurrentHashMap<>();
    private final List<AuditLogEntry> auditLogs = Collections.synchronizedList(new ArrayList<>());

    private static class DriverShim implements Driver {
        private final Driver driver;
        DriverShim(Driver d) { this.driver = d; }
        @Override public boolean acceptsURL(String u) throws SQLException { return driver.acceptsURL(u); }
        @Override public Connection connect(String u, Properties p) throws SQLException { return driver.connect(u, p); }
        @Override public int getMajorVersion() { return driver.getMajorVersion(); }
        @Override public int getMinorVersion() { return driver.getMinorVersion(); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException { return driver.getPropertyInfo(u, p); }
        @Override public boolean jdbcCompliant() { return driver.jdbcCompliant(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return driver.getParentLogger(); }
    }

    public static class AuditLogEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long timestamp;
        public String username;
        public String action;
        public String fileName;
        public long bytesTransferred;
        public String ipAddress;
        public boolean success;
        public String details;

        public AuditLogEntry(String username, String action, String fileName, long bytes, String ip, boolean success, String details) {
            this.timestamp = System.currentTimeMillis();
            this.username = username;
            this.action = action;
            this.fileName = fileName;
            this.bytesTransferred = bytes;
            this.ipAddress = ip;
            this.success = success;
            this.details = details;
        }

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(new Date(timestamp));
        }

        public String getFormattedSize() {
            if (bytesTransferred <= 0) return "-";
            if (bytesTransferred < 1024) return bytesTransferred + " B";
            if (bytesTransferred < 1024 * 1024) return String.format("%.1f KB", bytesTransferred / 1024.0);
            return String.format("%.1f MB", bytesTransferred / (1024.0 * 1024.0));
        }
    }

    private FtpDatabaseManager() {
        dataDir = new File(FtpConfig.DATA_DIR);
        if (!dataDir.exists()) dataDir.mkdirs();

        dbFile = new File(dataDir, "ftp.db");
        usersFile = new File(dataDir, "users.dat");
        auditLogFile = new File(dataDir, "audit_log.dat");

        initSqlDatabase();
        loadUsers();
        loadAuditLogs();

        if (userMap.isEmpty()) {
            FtpUser admin = new FtpUser("admin", "123456", FtpConfig.SERVER_ROOT_DIR);
            admin.setCanRead(true);
            admin.setCanWrite(true);
            admin.setCanDelete(true);
            addOrUpdateUser(admin);

            FtpUser student = new FtpUser("student", "123456", FtpConfig.SERVER_ROOT_DIR);
            student.setCanRead(true);
            student.setCanWrite(true);
            student.setCanDelete(false);
            addOrUpdateUser(student);

            FtpUser anon = new FtpUser("anonymous", "", FtpConfig.SERVER_ROOT_DIR);
            anon.setCanRead(true);
            anon.setCanWrite(false);
            anon.setCanDelete(false);
            addOrUpdateUser(anon);
        }
    }

    public static synchronized FtpDatabaseManager getInstance() {
        if (instance == null) {
            instance = new FtpDatabaseManager();
        }
        return instance;
    }

    private void ensureSqliteDriverLoaded() throws Exception {
        try {
            Class.forName("org.sqlite.JDBC");
            return;
        } catch (ClassNotFoundException ignored) {}

        String[] possibleJars = {
            "lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/ftp-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "../chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar"
        };
        for (String path : possibleJars) {
            File jar = new File(path);
            if (jar.exists()) {
                URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, FtpDatabaseManager.class.getClassLoader());
                Driver d = (Driver) Class.forName("org.sqlite.JDBC", true, loader).getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(new DriverShim(d));
                return;
            }
        }
        throw new ClassNotFoundException("Không tìm thấy sqlite-jdbc jar");
    }

    private void initSqlDatabase() {
        try {
            ensureSqliteDriverLoaded();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            sqlConnection = DriverManager.getConnection(url);
            isSqlAvailable = true;

            try (Statement stmt = sqlConnection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ftp_users ("
                        + "username TEXT PRIMARY KEY, "
                        + "password TEXT, "
                        + "home_directory TEXT, "
                        + "can_read INTEGER, "
                        + "can_write INTEGER, "
                        + "can_delete INTEGER, "
                        + "is_enabled INTEGER, "
                        + "created_at INTEGER)");

                stmt.execute("CREATE TABLE IF NOT EXISTS ftp_audit_logs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "timestamp INTEGER, "
                        + "username TEXT, "
                        + "action TEXT, "
                        + "file_name TEXT, "
                        + "bytes_transferred INTEGER, "
                        + "ip_address TEXT, "
                        + "success INTEGER, "
                        + "details TEXT)");
            }
            System.out.println("[FTP SQLite DB] Đã kết nối CSDL SQLite: " + dbFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[FTP DB] Cảnh báo SQLite: " + e.getMessage());
            isSqlAvailable = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadUsers() {
        if (usersFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(usersFile))) {
                Map<String, FtpUser> loaded = (Map<String, FtpUser>) ois.readObject();
                userMap.putAll(loaded);
            } catch (Exception ignored) {}
        }

        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM ftp_users")) {
                while (rs.next()) {
                    FtpUser u = new FtpUser(
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("home_directory")
                    );
                    u.setCanRead(rs.getInt("can_read") != 0);
                    u.setCanWrite(rs.getInt("can_write") != 0);
                    u.setCanDelete(rs.getInt("can_delete") != 0);
                    u.setEnabled(rs.getInt("is_enabled") != 0);
                    userMap.put(u.getUsername(), u);
                }
            } catch (SQLException e) {
                System.err.println("[FTP SQLite DB] Lỗi đọc ftp_users: " + e.getMessage());
            }

            for (FtpUser u : userMap.values()) {
                upsertUserSql(u);
            }
        }
    }

    private void upsertUserSql(FtpUser u) {
        if (!isSqlAvailable || sqlConnection == null || u == null) return;
        String sql = "INSERT OR REPLACE INTO ftp_users (username, password, home_directory, can_read, can_write, can_delete, is_enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPassword());
            ps.setString(3, u.getHomeDirectory());
            ps.setInt(4, u.isCanRead() ? 1 : 0);
            ps.setInt(5, u.isCanWrite() ? 1 : 0);
            ps.setInt(6, u.isCanDelete() ? 1 : 0);
            ps.setInt(7, u.isEnabled() ? 1 : 0);
            ps.setLong(8, u.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[FTP SQLite DB] Lỗi ghi user: " + e.getMessage());
        }
    }

    public synchronized void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(usersFile))) {
            oos.writeObject(new HashMap<>(userMap));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void loadAuditLogs() {
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM ftp_audit_logs ORDER BY id ASC")) {
                while (rs.next()) {
                    AuditLogEntry entry = new AuditLogEntry(
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("file_name"),
                            rs.getLong("bytes_transferred"),
                            rs.getString("ip_address"),
                            rs.getInt("success") != 0,
                            rs.getString("details")
                    );
                    entry.timestamp = rs.getLong("timestamp");
                    auditLogs.add(entry);
                }
            } catch (SQLException ignored) {}
        }

        if (auditLogs.isEmpty() && auditLogFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(auditLogFile))) {
                List<AuditLogEntry> loaded = (List<AuditLogEntry>) ois.readObject();
                auditLogs.addAll(loaded);
                for (AuditLogEntry e : loaded) {
                    insertAuditLogSql(e);
                }
            } catch (Exception ignored) {}
        }
    }

    private void insertAuditLogSql(AuditLogEntry entry) {
        if (!isSqlAvailable || sqlConnection == null || entry == null) return;
        String sql = "INSERT INTO ftp_audit_logs (timestamp, username, action, file_name, bytes_transferred, ip_address, success, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setLong(1, entry.timestamp);
            ps.setString(2, entry.username);
            ps.setString(3, entry.action);
            ps.setString(4, entry.fileName);
            ps.setLong(5, entry.bytesTransferred);
            ps.setString(6, entry.ipAddress);
            ps.setInt(7, entry.success ? 1 : 0);
            ps.setString(8, entry.details);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void saveAuditLogs() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(auditLogFile))) {
            oos.writeObject(new ArrayList<>(auditLogs));
        } catch (Exception ignored) {}
    }

    public FtpUser authenticate(String username, String password) {
        if (username == null) return null;
        FtpUser user = userMap.get(username);
        if (user != null && user.isEnabled()) {
            if ("anonymous".equalsIgnoreCase(username)) return user;
            if (user.getPassword().equals(password)) {
                return user;
            }
        }
        return null;
    }

    public FtpUser getUser(String username) {
        return userMap.get(username);
    }

    public void addOrUpdateUser(FtpUser user) {
        userMap.put(user.getUsername(), user);
        upsertUserSql(user);
        saveUsers();
    }

    public void deleteUser(String username) {
        userMap.remove(username);
        if (isSqlAvailable && sqlConnection != null) {
            try (PreparedStatement ps = sqlConnection.prepareStatement("DELETE FROM ftp_users WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
        saveUsers();
    }

    public List<FtpUser> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }

    public synchronized void logActivity(String username, String action, String fileName, long bytes, String ip, boolean success, String details) {
        AuditLogEntry entry = new AuditLogEntry(username, action, fileName, bytes, ip, success, details);
        auditLogs.add(entry);
        insertAuditLogSql(entry);
        saveAuditLogs();
    }

    public synchronized List<AuditLogEntry> getAuditLogs(int limit) {
        if (auditLogs.size() <= limit) {
            List<AuditLogEntry> reversed = new ArrayList<>(auditLogs);
            Collections.reverse(reversed);
            return reversed;
        }
        List<AuditLogEntry> sub = auditLogs.subList(auditLogs.size() - limit, auditLogs.size());
        List<AuditLogEntry> reversed = new ArrayList<>(sub);
        Collections.reverse(reversed);
        return reversed;
    }

    public static void main(String[] args) {
        FtpDatabaseManager db = FtpDatabaseManager.getInstance();
        System.out.println("FTP SQLite users: " + db.getAllUsers().size() + ", logs: " + db.getAuditLogs(1000).size());
    }
}
