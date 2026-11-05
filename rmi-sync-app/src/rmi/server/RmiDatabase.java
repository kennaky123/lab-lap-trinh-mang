package rmi.server;

import rmi.common.RecordItem;
import rmi.common.RmiUser;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Quản lý cơ sở dữ liệu lưu trữ bền vững bằng SQLite (records.db) kèm Fallback (records.dat)
 * cho từng RMI Server:
 * - Bảng 'records': Lưu dữ liệu bản ghi đồng bộ
 * - Bảng 'users': Lưu tài khoản đăng nhập và phân quyền (ADMIN, EDITOR, VIEWER)
 */
public class RmiDatabase {
    private final String dataDir;
    private final File dbFile;
    private final File legacyDatFile;
    private Connection sqlConnection;
    private boolean isSqlAvailable = false;

    private final Map<String, RecordItem> recordMap = new ConcurrentHashMap<>();
    private final Map<String, RmiUser> userMap = new ConcurrentHashMap<>();

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

    public RmiDatabase(String dataDir) {
        File dir = resolveDataDir(dataDir);
        if (!dir.exists()) dir.mkdirs();
        this.dataDir = dir.getPath();
        this.dbFile = new File(dir, "records.db");
        this.legacyDatFile = new File(dir, "records.dat");

        initSqlDatabase();
        loadData();
        loadUsers();
    }

    private File resolveDataDir(String subDir) {
        File local = new File(subDir);
        if (local.exists()) return local;
        File nested = new File("lap-trinh-mang/rmi-sync-app/" + subDir);
        if (nested.exists() || new File("lap-trinh-mang/rmi-sync-app/src").exists()) {
            return nested;
        }
        return local;
    }

    private void ensureSqliteDriverLoaded() throws Exception {
        try {
            Class.forName("org.sqlite.JDBC");
            return;
        } catch (ClassNotFoundException ignored) {}

        String[] possibleJars = {
            "lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/rmi-sync-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "../chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar"
        };
        for (String path : possibleJars) {
            File jar = new File(path);
            if (jar.exists()) {
                URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, RmiDatabase.class.getClassLoader());
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
                // 1. Bảng lưu trữ bản ghi đồng bộ
                stmt.execute("CREATE TABLE IF NOT EXISTS records ("
                        + "id TEXT PRIMARY KEY, "
                        + "name TEXT, "
                        + "category TEXT, "
                        + "value REAL, "
                        + "last_modified INTEGER, "
                        + "last_updated_by_server TEXT)");

                // 2. Bảng lưu trữ tài khoản và phân quyền đăng nhập
                stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                        + "username TEXT PRIMARY KEY, "
                        + "password TEXT, "
                        + "role TEXT, "
                        + "can_add INTEGER, "
                        + "can_update INTEGER, "
                        + "can_delete INTEGER)");
            }
            System.out.println("[RMI SQLite DB] Đã kết nối CSDL SQLite: " + dbFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[RMI DB] Cảnh báo SQLite: " + e.getMessage() + " -> Sử dụng records.dat");
            isSqlAvailable = false;
        }
    }

    // ==================== QUẢN LÝ TÀI KHOẢN & PHÂN QUYỀN (BẢNG USERS) ====================

    private synchronized void loadUsers() {
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, password, role, can_add, can_update, can_delete FROM users")) {
                while (rs.next()) {
                    RmiUser u = new RmiUser(
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("role"),
                            rs.getInt("can_add") != 0,
                            rs.getInt("can_update") != 0,
                            rs.getInt("can_delete") != 0
                    );
                    userMap.put(u.getUsername().toLowerCase(), u);
                }
            } catch (SQLException e) {
                System.err.println("[RMI SQLite DB] Lỗi đọc bảng users: " + e.getMessage());
            }
        }

        // Khởi tạo 3 tài khoản phân quyền mẫu mặc định nếu chưa có
        if (!userMap.containsKey("admin")) {
            putUser(RmiUser.createByRole("admin", "123456", "ADMIN"));
        }
        if (!userMap.containsKey("editor")) {
            putUser(RmiUser.createByRole("editor", "123456", "EDITOR"));
        }
        if (!userMap.containsKey("sinhvien")) {
            putUser(RmiUser.createByRole("sinhvien", "123456", "VIEWER"));
        }
    }

    public synchronized RmiUser authenticate(String username, String password) {
        if (username == null || password == null) return null;
        RmiUser u = userMap.get(username.trim().toLowerCase());
        if (u != null && u.getPassword().equals(password.trim())) {
            return u;
        }
        return null;
    }

    public synchronized List<RmiUser> getAllUsers() {
        List<RmiUser> list = new ArrayList<>(userMap.values());
        list.sort(Comparator.comparing(RmiUser::getUsername));
        return list;
    }

    public synchronized boolean putUser(RmiUser user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) return false;
        String key = user.getUsername().trim().toLowerCase();
        user.setUsername(key);
        userMap.put(key, user);

        if (isSqlAvailable && sqlConnection != null) {
            String sql = "INSERT OR REPLACE INTO users (username, password, role, can_add, can_update, can_delete) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPassword());
                ps.setString(3, user.getRole());
                ps.setInt(4, user.isCanAdd() ? 1 : 0);
                ps.setInt(5, user.isCanUpdate() ? 1 : 0);
                ps.setInt(6, user.isCanDelete() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[RMI SQLite DB] Lỗi ghi user " + key + ": " + e.getMessage());
            }
        }
        return true;
    }

    public synchronized boolean removeUser(String username) {
        if (username == null) return false;
        String key = username.trim().toLowerCase();
        if ("admin".equals(key)) return false; // Không xóa tài khoản gốc admin
        RmiUser removed = userMap.remove(key);
        if (removed != null) {
            if (isSqlAvailable && sqlConnection != null) {
                try (PreparedStatement ps = sqlConnection.prepareStatement("DELETE FROM users WHERE username = ?")) {
                    ps.setString(1, key);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("[RMI SQLite DB] Lỗi xóa user " + key + ": " + e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    // ==================== QUẢN LÝ DỮ LIỆU ĐỒNG BỘ (BẢNG RECORDS) ====================

    @SuppressWarnings("unchecked")
    private synchronized void loadData() {
        if (legacyDatFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(legacyDatFile))) {
                Map<String, RecordItem> map = (Map<String, RecordItem>) ois.readObject();
                recordMap.putAll(map);
            } catch (Exception ignored) {}
        }

        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, category, value, last_modified, last_updated_by_server FROM records")) {
                while (rs.next()) {
                    RecordItem item = new RecordItem(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getDouble("value"),
                            rs.getString("last_updated_by_server")
                    );
                    item.setLastModified(rs.getLong("last_modified"));
                    recordMap.put(item.getId(), item);
                }
            } catch (SQLException e) {
                System.err.println("[RMI SQLite DB] Lỗi đọc bảng records: " + e.getMessage());
            }

            for (RecordItem item : recordMap.values()) {
                upsertSql(item);
            }
        }
    }

    private void upsertSql(RecordItem item) {
        if (!isSqlAvailable || sqlConnection == null || item == null) return;
        String sql = "INSERT OR REPLACE INTO records (id, name, category, value, last_modified, last_updated_by_server) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getCategory());
            ps.setDouble(4, item.getValue());
            ps.setLong(5, item.getLastModified());
            ps.setString(6, item.getLastUpdatedByServer());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RMI SQLite DB] Lỗi ghi bản ghi " + item.getId() + ": " + e.getMessage());
        }
    }

    public synchronized void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(legacyDatFile))) {
            oos.writeObject(new HashMap<>(recordMap));
        } catch (Exception ignored) {}
    }

    public synchronized List<RecordItem> getAll() {
        List<RecordItem> list = new ArrayList<>(recordMap.values());
        list.sort(Comparator.comparing(RecordItem::getId));
        return list;
    }

    public synchronized RecordItem get(String id) {
        return recordMap.get(id);
    }

    public synchronized boolean put(RecordItem item) {
        if (item == null || item.getId() == null) return false;
        recordMap.put(item.getId(), item);
        upsertSql(item);
        saveData();
        return true;
    }

    public synchronized boolean remove(String id) {
        if (id == null) return false;
        RecordItem removed = recordMap.remove(id);
        if (removed != null) {
            if (isSqlAvailable && sqlConnection != null) {
                try (PreparedStatement ps = sqlConnection.prepareStatement("DELETE FROM records WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("[RMI SQLite DB] Lỗi xóa bản ghi " + id + ": " + e.getMessage());
                }
            }
            saveData();
            return true;
        }
        return false;
    }

    public synchronized void replaceAll(List<RecordItem> items) {
        recordMap.clear();
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement st = sqlConnection.createStatement()) {
                st.executeUpdate("DELETE FROM records");
            } catch (SQLException ignored) {}
        }
        for (RecordItem it : items) {
            recordMap.put(it.getId(), it);
            upsertSql(it);
        }
        saveData();
    }

    public int count() {
        return recordMap.size();
    }

    public static void main(String[] args) {
        RmiDatabase db1 = new RmiDatabase("server1_data");
        RmiDatabase db2 = new RmiDatabase("server2_data");
        System.out.println("Server 1 records: " + db1.count() + ", users: " + db1.getAllUsers().size());
        System.out.println("Server 2 records: " + db2.count() + ", users: " + db2.getAllUsers().size());
    }
}
