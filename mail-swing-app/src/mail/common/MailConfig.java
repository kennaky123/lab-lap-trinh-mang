package mail.common;

/**
 * Cấu hình hệ thống Mail Server và Mail Client
 */
public class MailConfig {
    // Cổng dịch vụ Mail Server cục bộ (Localhost)
    public static final int LOCAL_SMTP_PORT = 2525;
    public static final int LOCAL_POP3_PORT = 1110;

    // Cấu hình máy chủ Google Gmail
    public static final String GMAIL_SMTP_HOST = "smtp.gmail.com";
    public static final int GMAIL_SMTP_PORT = 587; // Cổng STARTTLS chuẩn

    public static final String GMAIL_POP3_HOST = "pop.gmail.com";
    public static final int GMAIL_POP3_PORT = 995; // Cổng SSL chuẩn

    // Đường dẫn lưu trữ
    public static final String DATA_DIR = "mail_data";
    public static final String ATTACHMENTS_DIR = "attachments";
}

