package mail.server;

import mail.common.EmailMessage;
import mail.common.MailAccount;
import mail.common.MailConfig;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý Cơ Sở Dữ Liệu Mail Server:
 * - Lưu trữ danh sách tài khoản hộp thư (Accounts Database)
 * - Lưu trữ các bức thư gửi/nhận vĩnh viễn (Emails Database)
 * - Lưu trữ nhật ký giao dịch SMTP/POP3 (Audit Transaction Log)
 */
public class MailDatabaseManager {
    private static MailDatabaseManager instance;

    private final File dataDir;
    private final File accountsFile;
    private final File emailsFile;
    private final File logsFile;

    private final Map<String, MailAccount> accountsMap = new ConcurrentHashMap<>();
    private final List<EmailMessage> emailList = Collections.synchronizedList(new ArrayList<>());
    private final List<MailLogEntry> logList = Collections.synchronizedList(new ArrayList<>());

    public static class MailLogEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long timestamp;
        public String protocol; // SMTP hoặc POP3
        public String user;
        public String action;   // SEND, RECEIVE, AUTH, DELETE
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

        accountsFile = new File(dataDir, "accounts.dat");
        emailsFile = new File(dataDir, "emails.dat");
        logsFile = new File(dataDir, "logs.dat");

        loadAccounts();
        loadEmails();
        loadLogs();

        // Đảm bảo 3 tài khoản mặc định luôn tồn tại
        ensureDefaultAccounts();
    }

    private void ensureDefaultAccounts() {
        boolean wasEmpty = accountsMap.isEmpty();
        if (!accountsMap.containsKey("alice@local.com")) {
            accountsMap.put("alice@local.com", new MailAccount("alice@local.com", "123456", "Alice Nguyễn"));
        }
        if (!accountsMap.containsKey("bob@local.com")) {
            accountsMap.put("bob@local.com", new MailAccount("bob@local.com", "123456", "Bob Trần"));
        }
        if (!accountsMap.containsKey("admin@local.com")) {
            accountsMap.put("admin@local.com", new MailAccount("admin@local.com", "123456", "Administrator"));
        }
        saveAccounts();

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
            } catch (Exception e) {
                System.err.println("[Mail DB] Lỗi nạp tài khoản: " + e.getMessage());
            }
        }
    }

    public synchronized void saveAccounts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(accountsFile))) {
            oos.writeObject(new HashMap<>(accountsMap));
        } catch (Exception e) {
            System.err.println("[Mail DB] Lưu tài khoản thất bại: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadEmails() {
        if (emailsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(emailsFile))) {
                List<EmailMessage> list = (List<EmailMessage>) ois.readObject();
                emailList.addAll(list);
            } catch (Exception e) {
                System.err.println("[Mail DB] Lỗi nạp thư điện tử: " + e.getMessage());
            }
        }
    }

    public synchronized void saveEmails() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(emailsFile))) {
            oos.writeObject(new ArrayList<>(emailList));
        } catch (Exception e) {
            System.err.println("[Mail DB] Lưu thư điện tử thất bại: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLogs() {
        if (logsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(logsFile))) {
                List<MailLogEntry> list = (List<MailLogEntry>) ois.readObject();
                logList.addAll(list);
            } catch (Exception e) {
                System.err.println("[Mail DB] Lỗi nạp nhật ký: " + e.getMessage());
            }
        }
    }

    public synchronized void saveLogs() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(logsFile))) {
            oos.writeObject(new ArrayList<>(logList));
        } catch (Exception e) {
            System.err.println("[Mail DB] Lưu nhật ký thất bại: " + e.getMessage());
        }
    }

    public synchronized MailAccount authenticate(String email, String password) {
        if (email == null || email.trim().isEmpty()) return null;
        String key = normalizeEmail(email);
        String pass = password != null ? password.trim() : "";

        MailAccount acc = accountsMap.get(key);
        if (acc == null && key.endsWith("@local.com") && !pass.isEmpty()) {
            // Tự động tạo hộp thư nội bộ mới nếu chưa tồn tại
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
        // Tự động tạo hộp thư người nhận nếu là @local.com mà chưa có trong CSDL
        String rcpt = normalizeEmail(msg.getTo());
        if (rcpt.endsWith("@local.com") && !accountsMap.containsKey(rcpt)) {
            String display = rcpt.substring(0, rcpt.indexOf('@'));
            addAccount(new MailAccount(rcpt, "123456", display));
        }
        emailList.add(msg);
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
        saveAccounts();
    }

    public synchronized void deleteAccount(String email) {
        accountsMap.remove(normalizeEmail(email));
        saveAccounts();
    }

    public synchronized List<MailAccount> getAllAccounts() {
        return new ArrayList<>(accountsMap.values());
    }

    public synchronized void logActivity(String protocol, String user, String action, String detail, boolean success) {
        MailLogEntry entry = new MailLogEntry(protocol, user, action, detail, success);
        logList.add(entry);
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
}
