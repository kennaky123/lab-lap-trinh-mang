package ftp.server;

import ftp.common.FtpConfig;
import ftp.common.FtpUser;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý Cơ Sở Dữ Liệu Máy Chủ FTP:
 * - Lưu trữ danh sách người dùng và phân quyền (Users Database)
 * - Lưu trữ lịch sử tải lên/tải xuống và thao tác lệnh (Audit Log Database)
 * - Tự động duy trì bền vững trên đĩa mà không cần cài đặt thêm MySQL/PostgreSQL
 */
public class FtpDatabaseManager {
    private static FtpDatabaseManager instance;

    private final File dataDir;
    private final File usersFile;
    private final File auditLogFile;

    private final Map<String, FtpUser> userMap = new ConcurrentHashMap<>();
    private final List<AuditLogEntry> auditLogs = Collections.synchronizedList(new ArrayList<>());

    public static class AuditLogEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long timestamp;
        public String username;
        public String action; // LOGIN, RETR (Download), STOR (Upload), DELE, MKD, RMD
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

        usersFile = new File(dataDir, "users.dat");
        auditLogFile = new File(dataDir, "audit_log.dat");

        loadUsers();
        loadAuditLogs();

        // Tạo tài khoản mẫu mặc định nếu CSDL trống
        if (userMap.isEmpty()) {
            FtpUser admin = new FtpUser("admin", "123456", FtpConfig.SERVER_ROOT_DIR);
            admin.setCanRead(true);
            admin.setCanWrite(true);
            admin.setCanDelete(true);
            userMap.put(admin.getUsername(), admin);

            FtpUser student = new FtpUser("student", "123456", FtpConfig.SERVER_ROOT_DIR);
            student.setCanRead(true);
            student.setCanWrite(true);
            student.setCanDelete(false); // Sinh viên chỉ đọc và ghi, không xóa
            userMap.put(student.getUsername(), student);

            FtpUser anon = new FtpUser("anonymous", "", FtpConfig.SERVER_ROOT_DIR);
            anon.setCanRead(true);
            anon.setCanWrite(false);
            anon.setCanDelete(false);
            userMap.put(anon.getUsername(), anon);

            saveUsers();
        }
    }

    public static synchronized FtpDatabaseManager getInstance() {
        if (instance == null) {
            instance = new FtpDatabaseManager();
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private void loadUsers() {
        if (usersFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(usersFile))) {
                Map<String, FtpUser> loaded = (Map<String, FtpUser>) ois.readObject();
                userMap.putAll(loaded);
            } catch (Exception e) {
                System.err.println("[FTP DB] Lỗi nạp dữ liệu người dùng: " + e.getMessage());
            }
        }
    }

    public synchronized void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(usersFile))) {
            oos.writeObject(new HashMap<>(userMap));
        } catch (Exception e) {
            System.err.println("[FTP DB] Lưu dữ liệu người dùng thất bại: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAuditLogs() {
        if (auditLogFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(auditLogFile))) {
                List<AuditLogEntry> loaded = (List<AuditLogEntry>) ois.readObject();
                auditLogs.addAll(loaded);
            } catch (Exception e) {
                System.err.println("[FTP DB] Lỗi nạp nhật ký kiểm toán: " + e.getMessage());
            }
        }
    }

    public synchronized void saveAuditLogs() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(auditLogFile))) {
            oos.writeObject(new ArrayList<>(auditLogs));
        } catch (Exception e) {
            System.err.println("[FTP DB] Lưu nhật ký kiểm toán thất bại: " + e.getMessage());
        }
    }

    public FtpUser authenticate(String username, String password) {
        if (username == null) return null;
        FtpUser user = userMap.get(username);
        if (user != null && user.isEnabled()) {
            // Cho phép anonymous không cần pass hoặc pass bất kỳ
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
        saveUsers();
    }

    public void deleteUser(String username) {
        userMap.remove(username);
        saveUsers();
    }

    public List<FtpUser> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }

    public synchronized void logActivity(String username, String action, String fileName, long bytes, String ip, boolean success, String details) {
        AuditLogEntry entry = new AuditLogEntry(username, action, fileName, bytes, ip, success, details);
        auditLogs.add(entry);
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
}

