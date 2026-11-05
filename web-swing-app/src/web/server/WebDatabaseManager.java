package web.server;

import web.common.HttpConstants;
import web.server.HttpServer.AccessLog;
import web.server.HttpServer.PostSubmission;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Quản lý Cơ Sở Dữ Liệu Máy Chủ Web bằng SQLite (browser_data/web.db):
 * - Bảng 'access_logs': Lưu lịch sử truy cập HTTP GET, HEAD, POST
 * - Bảng 'post_submissions': Lưu dữ liệu biểu mẫu người dùng gửi qua HTTP POST
 * - Bảng 'web_resources': Lưu danh sách tài nguyên đa dịch vụ (.html, .pdf, .mp3, .mp4)
 */
public class WebDatabaseManager {
    private static WebDatabaseManager instance;

    private final File dataDir;
    private final File dbFile;
    private Connection sqlConnection;
    private boolean isSqlAvailable = false;

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

    private WebDatabaseManager() {
        dataDir = new File(HttpConstants.DATA_DIR);
        if (!dataDir.exists()) dataDir.mkdirs();
        dbFile = new File(dataDir, "web.db");
        initSqlDatabase();
        seedDefaultData();
    }

    public static synchronized WebDatabaseManager getInstance() {
        if (instance == null) {
            instance = new WebDatabaseManager();
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
            "lap-trinh-mang/web-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "../chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar"
        };
        for (String path : possibleJars) {
            File jar = new File(path);
            if (jar.exists()) {
                URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, WebDatabaseManager.class.getClassLoader());
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
                stmt.execute("CREATE TABLE IF NOT EXISTS access_logs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "timestamp INTEGER, "
                        + "method TEXT, "
                        + "uri TEXT, "
                        + "status_code INTEGER, "
                        + "client_ip TEXT)");

                stmt.execute("CREATE TABLE IF NOT EXISTS post_submissions ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "timestamp INTEGER, "
                        + "student_name TEXT, "
                        + "student_id TEXT, "
                        + "content TEXT, "
                        + "client_ip TEXT)");

                stmt.execute("CREATE TABLE IF NOT EXISTS web_resources ("
                        + "file_name TEXT PRIMARY KEY, "
                        + "mime_type TEXT, "
                        + "file_size INTEGER, "
                        + "hits INTEGER)");
            }
            System.out.println("[Web SQLite DB] Đã kết nối CSDL SQLite: " + dbFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[Web DB] Cảnh báo SQLite: " + e.getMessage());
            isSqlAvailable = false;
        }
    }

    private void seedDefaultData() {
        if (!isSqlAvailable || sqlConnection == null) return;
        File webRoot = new File(HttpConstants.WEB_ROOT);
        if (webRoot.exists() && webRoot.isDirectory()) {
            File[] files = webRoot.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        try (PreparedStatement ps = sqlConnection.prepareStatement(
                                "INSERT OR IGNORE INTO web_resources (file_name, mime_type, file_size, hits) VALUES (?, ?, ?, 1)")) {
                            ps.setString(1, f.getName());
                            ps.setString(2, HttpConstants.getMimeType(f.getName()));
                            ps.setLong(3, f.length());
                            ps.executeUpdate();
                        } catch (SQLException ignored) {}
                    }
                }
            }
        }
        if (loadAccessLogs().isEmpty()) {
            insertAccessLog(new AccessLog("GET", "/index.html", 200, "127.0.0.1"));
            insertAccessLog(new AccessLog("HEAD", "/document.pdf", 200, "127.0.0.1"));
            insertAccessLog(new AccessLog("POST", "/submit", 200, "127.0.0.1"));
        }
        if (loadPostSubmissions().isEmpty()) {
            insertPostSubmission(new PostSubmission("Nguyen Van A", "22IT001", "Em da hoan thanh bai thuc hanh HTTP Lab 4!", "127.0.0.1"));
            insertPostSubmission(new PostSubmission("Tran Thi B", "22IT002", "Bao cao kiem thu GET, HEAD, POST va da dich vu.", "127.0.0.1"));
        }
    }

    public synchronized void insertAccessLog(AccessLog log) {
        if (!isSqlAvailable || sqlConnection == null || log == null) return;
        String sql = "INSERT INTO access_logs (timestamp, method, uri, status_code, client_ip) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setLong(1, log.timestamp);
            ps.setString(2, log.method);
            ps.setString(3, log.uri);
            ps.setInt(4, log.statusCode);
            ps.setString(5, log.clientIp);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void incrementResourceHit(String fileName) {
        if (!isSqlAvailable || sqlConnection == null || fileName == null) return;
        try (PreparedStatement ps = sqlConnection.prepareStatement(
                "UPDATE web_resources SET hits = hits + 1 WHERE file_name = ?")) {
            ps.setString(1, fileName);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void insertPostSubmission(PostSubmission sub) {
        if (!isSqlAvailable || sqlConnection == null || sub == null) return;
        String sql = "INSERT INTO post_submissions (timestamp, student_name, student_id, content, client_ip) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setLong(1, sub.timestamp);
            ps.setString(2, sub.name);
            ps.setString(3, sub.studentId);
            ps.setString(4, sub.content);
            ps.setString(5, sub.clientIp);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized List<AccessLog> loadAccessLogs() {
        List<AccessLog> list = new ArrayList<>();
        if (!isSqlAvailable || sqlConnection == null) return list;
        try (Statement stmt = sqlConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM access_logs ORDER BY id ASC")) {
            while (rs.next()) {
                AccessLog l = new AccessLog(
                        rs.getString("method"),
                        rs.getString("uri"),
                        rs.getInt("status_code"),
                        rs.getString("client_ip")
                );
                l.timestamp = rs.getLong("timestamp");
                list.add(l);
            }
        } catch (SQLException ignored) {}
        return list;
    }

    public synchronized List<PostSubmission> loadPostSubmissions() {
        List<PostSubmission> list = new ArrayList<>();
        if (!isSqlAvailable || sqlConnection == null) return list;
        try (Statement stmt = sqlConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM post_submissions ORDER BY id ASC")) {
            while (rs.next()) {
                PostSubmission s = new PostSubmission(
                        rs.getString("student_name"),
                        rs.getString("student_id"),
                        rs.getString("content"),
                        rs.getString("client_ip")
                );
                s.timestamp = rs.getLong("timestamp");
                list.add(s);
            }
        } catch (SQLException ignored) {}
        return list;
    }

    public static void main(String[] args) {
        WebDatabaseManager db = WebDatabaseManager.getInstance();
        System.out.println("Web SQLite access_logs: " + db.loadAccessLogs().size() + ", post_submissions: " + db.loadPostSubmissions().size());
    }
}
