package mail.common;

import java.io.File;

/**
 * Cấu hình hệ thống Mail Server và Mail Client
 */
public class MailConfig {
    // Cổng dịch vụ Mail Server cục bộ / mạng LAN
    public static final int LOCAL_SMTP_PORT = 2525;
    public static final int LOCAL_POP3_PORT = 1110;
    public static final int FALLBACK_POP3_PORT = 2110;
    public static final int UDP_DISCOVERY_PORT = 2527;

    // Cấu hình máy chủ Google Gmail
    public static final String GMAIL_SMTP_HOST = "smtp.gmail.com";
    public static final int GMAIL_SMTP_PORT = 587; // Cổng STARTTLS chuẩn

    public static final String GMAIL_POP3_HOST = "pop.gmail.com";
    public static final int GMAIL_POP3_PORT = 995; // Cổng SSL chuẩn

    // Đường dẫn lưu trữ
    public static final String DATA_DIR = resolvePath("mail_data");
    public static final String ATTACHMENTS_DIR = resolvePath("attachments");

    private static String resolvePath(String relativePath) {
        File direct = new File(relativePath);
        if (direct.exists()) return direct.getPath();
        File nested = new File("lap-trinh-mang/mail-swing-app/" + relativePath);
        if (nested.exists() || new File("lap-trinh-mang/mail-swing-app/src").exists()) {
            return nested.getPath();
        }
        return direct.getPath();
    }
}


