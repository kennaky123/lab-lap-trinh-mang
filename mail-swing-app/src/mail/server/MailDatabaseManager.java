package mail.server;

import mail.common.EmailMessage;
import mail.common.MailAccount;
import mail.common.MailConfig;

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
 * Quản lý Cơ Sở Dữ Liệu Mail Server bằng SQLite (mail_data/mail.db):
 * - Bảng 'mail_accounts': Lưu trữ danh sách tài khoản hộp thư
 * - Bảng 'emails': Lưu trữ các bức thư gửi/nhận vĩnh viễn kèm tệp đính kèm
 * - Bảng 'mail_logs': Lưu trữ nhật ký giao dịch SMTP/POP3
 */
public class MailDatabaseManager {
    private static MailDatabaseManager instance;

    private final File dataDir;
    private final File dbFile;
    private final File accountsFile;
    private final File emailsFile;
    private final File logsFile;

    private Connection sqlConnection;
    private boolean isSqlAvailable = false;

    private final Map<String, MailAccount> accountsMap = new ConcurrentHashMap<>();
    private final List<EmailMessage> emailList = Collections.synchronizedList(new ArrayList<>());
    private final List<MailLogEntry> logList = Collections.synchronizedList(new ArrayList<>());

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

    public static class MailLogEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long timestamp;
        public String protocol;
        public String user;
        public String action;
        public String detail;
        public boolean success;

        public MailLogEntry(String protocol, String user, String action, String detail, boolean success) {
            this.timestamp = System.currentTimeMillis();
            this.protocol = protocol;
            this.user = user;
            this.action = action;
            this.detail = detail;
            this.success = success;
        }

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(new Date(timestamp));
        }
    }

    private MailDatabaseManager() {
        File dir = new File(MailConfig.DATA_DIR);
        if (!dir.exists() && new File("lap-trinh-mang/mail-swing-app/mail_data").exists()) {
            dir = new File("lap-trinh-mang/mail-swing-app/mail_data");
        } else if (!dir.exists()) {
            dir.mkdirs();
        }
        dataDir = dir;

        dbFile = new File(dataDir, "mail.db");
        accountsFile = new File(dataDir, "accounts.dat");
        emailsFile = new File(dataDir, "emails.dat");
        logsFile = new File(dataDir, "logs.dat");

        initSqlDatabase();
        loadAccounts();
        loadEmails();
        loadLogs();

        ensureDefaultAccounts();
    }

    private void ensureSqliteDriverLoaded() throws Exception {
        try {
            Class.forName("org.sqlite.JDBC");
            return;
        } catch (ClassNotFoundException ignored) {}

        String[] possibleJars = {
            "lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/mail-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "../chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar",
            "lap-trinh-mang/chat-swing-app/lib/sqlite-jdbc-3.36.0.3.jar"
        };
        for (String path : possibleJars) {
            File jar = new File(path);
            if (jar.exists()) {
                URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, MailDatabaseManager.class.getClassLoader());
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
                stmt.execute("CREATE TABLE IF NOT EXISTS mail_accounts ("
                        + "email TEXT PRIMARY KEY, "
                        + "password TEXT, "
                        + "display_name TEXT, "
                        + "is_enabled INTEGER, "
                        + "created_at INTEGER)");

                stmt.execute("CREATE TABLE IF NOT EXISTS emails ("
                        + "id TEXT PRIMARY KEY, "
                        + "sender TEXT, "
                        + "recipient TEXT, "
                        + "subject TEXT, "
                        + "body TEXT, "
                        + "timestamp INTEGER, "
                        + "is_read INTEGER, "
                        + "is_deleted INTEGER, "
                        + "attachment_name TEXT, "
                        + "attachment_data BLOB)");

                stmt.execute("CREATE TABLE IF NOT EXISTS mail_logs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "timestamp INTEGER, "
                        + "protocol TEXT, "
                        + "user_email TEXT, "
                        + "action TEXT, "
                        + "detail TEXT, "
                        + "success INTEGER)");
            }
            System.out.println("[Mail SQLite DB] Đã kết nối CSDL SQLite: " + dbFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[Mail DB] Cảnh báo SQLite: " + e.getMessage());
            isSqlAvailable = false;
        }
    }

    private void ensureDefaultAccounts() {
        boolean wasEmpty = accountsMap.isEmpty();
        if (!accountsMap.containsKey("alice@local.com")) {
            addAccount(new MailAccount("alice@local.com", "123456", "Alice Nguyễn"));
        }
        if (!accountsMap.containsKey("bob@local.com")) {
            addAccount(new MailAccount("bob@local.com", "123456", "Bob Trần"));
        }
        if (!accountsMap.containsKey("admin@local.com")) {
            addAccount(new MailAccount("admin@local.com", "123456", "Administrator"));
        }

        if (wasEmpty && emailList.isEmpty()) {
            EmailMessage welcome = new EmailMessage(
                    "system@local.com",
                    "alice@local.com",
                    "Chào mừng bạn đến với Hệ thống Mail Server & Gmail Client!",
                    "Xin chào Alice,\n\nĐây là thư điện tử mẫu được tạo tự động từ Mail Server.\nHệ thống hỗ trợ cả giao thức SMTP (Gửi thư) và POP3 (Nhận thư).\nBạn có thể gửi thư nội bộ giữa các tài khoản @local.com hoặc gửi thư thực tế qua Gmail!"
            );
            saveEmail(welcome);
        }
    }

    public static synchronized MailDatabaseManager getInstance() {
        if (instance == null) {
            instance = new MailDatabaseManager();
        }
        return instance;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) return "";
        String clean = email.toLowerCase().trim();
        if (!clean.contains("@")) {
            clean = clean + "@local.com";
        }
        return clean;
    }

    @SuppressWarnings("unchecked")
    private void loadAccounts() {
        if (accountsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(accountsFile))) {
                Map<String, MailAccount> map = (Map<String, MailAccount>) ois.readObject();
                for (MailAccount acc : map.values()) {
                    if (acc != null && acc.getEmail() != null) {
                        accountsMap.put(normalizeEmail(acc.getEmail()), acc);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM mail_accounts")) {
                while (rs.next()) {
                    MailAccount acc = new MailAccount(
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("display_name")
                    );
                    acc.setEnabled(rs.getInt("is_enabled") != 0);
                    accountsMap.put(normalizeEmail(acc.getEmail()), acc);
                }
            } catch (SQLException ignored) {}

            for (MailAccount acc : accountsMap.values()) {
                upsertAccountSql(acc);
            }
        }
    }

    private void upsertAccountSql(MailAccount acc) {
        if (!isSqlAvailable || sqlConnection == null || acc == null) return;
        String sql = "INSERT OR REPLACE INTO mail_accounts (email, password, display_name, is_enabled, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setString(1, normalizeEmail(acc.getEmail()));
            ps.setString(2, acc.getPassword());
            ps.setString(3, acc.getDisplayName());
            ps.setInt(4, acc.isEnabled() ? 1 : 0);
            ps.setLong(5, acc.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void saveAccounts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(accountsFile))) {
            oos.writeObject(new HashMap<>(accountsMap));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void loadEmails() {
        Map<String, EmailMessage> loadedById = new LinkedHashMap<>();
        if (emailsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(emailsFile))) {
                List<EmailMessage> list = (List<EmailMessage>) ois.readObject();
                for (EmailMessage m : list) {
                    if (m != null && m.getId() != null) loadedById.put(m.getId(), m);
                }
            } catch (Exception ignored) {}
        }

        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM emails ORDER BY timestamp ASC")) {
                while (rs.next()) {
                    EmailMessage m = new EmailMessage(
                            rs.getString("sender"),
                            rs.getString("recipient"),
                            rs.getString("subject"),
                            rs.getString("body")
                    );
                    m.setId(rs.getString("id"));
                    m.setTimestamp(rs.getLong("timestamp"));
                    m.setRead(rs.getInt("is_read") != 0);
                    m.setDeleted(rs.getInt("is_deleted") != 0);
                    m.setAttachmentName(rs.getString("attachment_name"));
                    m.setAttachmentData(rs.getBytes("attachment_data"));
                    loadedById.put(m.getId(), m);
                }
            } catch (SQLException ignored) {}

            for (EmailMessage m : loadedById.values()) {
                upsertEmailSql(m);
            }
        }
        emailList.addAll(loadedById.values());
    }

    private void upsertEmailSql(EmailMessage m) {
        if (!isSqlAvailable || sqlConnection == null || m == null) return;
        String sql = "INSERT OR REPLACE INTO emails (id, sender, recipient, subject, body, timestamp, is_read, is_deleted, attachment_name, attachment_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setString(1, m.getId());
            ps.setString(2, m.getFrom());
            ps.setString(3, m.getTo());
            ps.setString(4, m.getSubject());
            ps.setString(5, m.getBody());
            ps.setLong(6, m.getTimestamp());
            ps.setInt(7, m.isRead() ? 1 : 0);
            ps.setInt(8, m.isDeleted() ? 1 : 0);
            ps.setString(9, m.getAttachmentName());
            ps.setBytes(10, m.getAttachmentData());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void saveEmails() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(emailsFile))) {
            oos.writeObject(new ArrayList<>(emailList));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void loadLogs() {
        if (isSqlAvailable && sqlConnection != null) {
            try (Statement stmt = sqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM mail_logs ORDER BY id ASC")) {
                while (rs.next()) {
                    MailLogEntry e = new MailLogEntry(
                            rs.getString("protocol"),
                            rs.getString("user_email"),
                            rs.getString("action"),
                            rs.getString("detail"),
                            rs.getInt("success") != 0
                    );
                    e.timestamp = rs.getLong("timestamp");
                    logList.add(e);
                }
            } catch (SQLException ignored) {}
        }

        if (logList.isEmpty() && logsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(logsFile))) {
                List<MailLogEntry> list = (List<MailLogEntry>) ois.readObject();
                logList.addAll(list);
                for (MailLogEntry e : list) {
                    insertLogSql(e);
                }
            } catch (Exception ignored) {}
        }
    }

    private void insertLogSql(MailLogEntry e) {
        if (!isSqlAvailable || sqlConnection == null || e == null) return;
        String sql = "INSERT INTO mail_logs (timestamp, protocol, user_email, action, detail, success) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = sqlConnection.prepareStatement(sql)) {
            ps.setLong(1, e.timestamp);
            ps.setString(2, e.protocol);
            ps.setString(3, e.user);
            ps.setString(4, e.action);
            ps.setString(5, e.detail);
            ps.setInt(6, e.success ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void saveLogs() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(logsFile))) {
            oos.writeObject(new ArrayList<>(logList));
        } catch (Exception ignored) {}
    }

    public synchronized MailAccount authenticate(String email, String password) {
        if (email == null || email.trim().isEmpty()) return null;
        String key = normalizeEmail(email);
        String pass = password != null ? password.trim() : "";

        MailAccount acc = accountsMap.get(key);
        if (acc == null && key.endsWith("@local.com") && !pass.isEmpty()) {
            String display = key.substring(0, key.indexOf('@'));
            acc = new MailAccount(key, pass, display);
            addAccount(acc);
            return acc;
        }
        if (acc != null && acc.isEnabled() && acc.getPassword().equals(pass)) {
            return acc;
        }
        return null;
    }

    public synchronized void saveEmail(EmailMessage msg) {
        if (msg == null) return;
        String rcpt = normalizeEmail(msg.getTo());
        if (rcpt.endsWith("@local.com") && !accountsMap.containsKey(rcpt)) {
            String display = rcpt.substring(0, rcpt.indexOf('@'));
            addAccount(new MailAccount(rcpt, "123456", display));
        }
        emailList.add(msg);
        upsertEmailSql(msg);
        saveEmails();
    }

    public synchronized List<EmailMessage> getEmailsForRecipient(String recipientEmail) {
        List<EmailMessage> result = new ArrayList<>();
        String target = normalizeEmail(recipientEmail);
        for (EmailMessage m : emailList) {
            if (!m.isDeleted() && m.getTo() != null && normalizeEmail(m.getTo()).equals(target)) {
                result.add(m);
            }
        }
        return result;
    }

    public synchronized List<EmailMessage> getSentEmailsForSender(String senderEmail) {
        List<EmailMessage> result = new ArrayList<>();
        String target = normalizeEmail(senderEmail);
        for (EmailMessage m : emailList) {
            if (m.getFrom() != null && normalizeEmail(m.getFrom()).equals(target)) {
                result.add(m);
            }
        }
        return result;
    }

    public synchronized boolean deleteEmail(String email, String messageId) {
        for (EmailMessage m : emailList) {
            if (m.getId().equals(messageId)) {
                m.setDeleted(true);
                upsertEmailSql(m);
                saveEmails();
                return true;
            }
        }
        return false;
    }

    public synchronized void addAccount(MailAccount acc) {
        if (acc == null || acc.getEmail() == null) return;
        String key = normalizeEmail(acc.getEmail());
        acc.setEmail(key);
        accountsMap.put(key, acc);
        upsertAccountSql(acc);
        saveAccounts();
    }

    public synchronized void deleteAccount(String email) {
        String key = normalizeEmail(email);
        accountsMap.remove(key);
        if (isSqlAvailable && sqlConnection != null) {
            try (PreparedStatement ps = sqlConnection.prepareStatement("DELETE FROM mail_accounts WHERE email = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
        saveAccounts();
    }

    public synchronized List<MailAccount> getAllAccounts() {
        return new ArrayList<>(accountsMap.values());
    }

    public synchronized void logActivity(String protocol, String user, String action, String detail, boolean success) {
        MailLogEntry entry = new MailLogEntry(protocol, user, action, detail, success);
        logList.add(entry);
        insertLogSql(entry);
        saveLogs();
    }

    public synchronized List<MailLogEntry> getLogs(int limit) {
        if (logList.size() <= limit) {
            List<MailLogEntry> rev = new ArrayList<>(logList);
            Collections.reverse(rev);
            return rev;
        }
        List<MailLogEntry> sub = logList.subList(logList.size() - limit, logList.size());
        List<MailLogEntry> rev = new ArrayList<>(sub);
        Collections.reverse(rev);
        return rev;
    }

    public synchronized int getTotalEmailCount() {
        int c = 0;
        for (EmailMessage m : emailList) {
            if (!m.isDeleted()) c++;
        }
        return c;
    }

    public static void main(String[] args) {
        MailDatabaseManager db = MailDatabaseManager.getInstance();
        System.out.println("Mail SQLite accounts: " + db.getAllAccounts().size() + ", emails: " + db.getTotalEmailCount());
    }
}
