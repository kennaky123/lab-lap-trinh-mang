package mail.common;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Đại diện một bức thư điện tử (Email Message) theo chuẩn RFC 5322
 */
public class EmailMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String from;
    private String to;
    private String subject;
    private String body;
    private long timestamp;
    private boolean isRead;
    private boolean isDeleted; // Đánh dấu xóa (POP3 DELE)

    // Đính kèm tệp tin
    private String attachmentName;
    private byte[] attachmentData;

    public EmailMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.isDeleted = false;
    }

    public EmailMessage(String from, String to, String subject, String body) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.isDeleted = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public byte[] getAttachmentData() { return attachmentData; }
    public void setAttachmentData(byte[] attachmentData) { this.attachmentData = attachmentData; }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    public long getApproximateSize() {
        long size = (subject != null ? subject.length() : 0) + (body != null ? body.length() : 0) + 128;
        if (attachmentData != null) {
            size += attachmentData.length;
        }
        return size;
    }

    /**
     * Xuất ra chuỗi định dạng MIME / RFC 5322 chuẩn phục vụ giao thức SMTP & POP3
     */
    public String toRfc822String() {
        SimpleDateFormat rfcDate = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from != null ? from : "").append("\r\n");
        sb.append("To: ").append(to != null ? to : "").append("\r\n");
        sb.append("Date: ").append(rfcDate.format(new Date(timestamp))).append("\r\n");
        sb.append("Subject: ").append(subject != null ? subject : "").append("\r\n");
        sb.append("Message-ID: <").append(id).append("@mailserver.local>\r\n");
        sb.append("MIME-Version: 1.0\r\n");

        if (attachmentName != null && attachmentData != null) {
            String boundary = "----=_Part_" + id;
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");

            // Text Part
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
            sb.append(body != null ? body : "").append("\r\n\r\n");

            // Attachment Part
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Type: application/octet-stream; name=\"").append(attachmentName).append("\"\r\n");
            sb.append("Content-Disposition: attachment; filename=\"").append(attachmentName).append("\"\r\n");
            sb.append("Content-Transfer-Encoding: base64\r\n\r\n");
            sb.append(Base64.getEncoder().encodeToString(attachmentData)).append("\r\n\r\n");
            sb.append("--").append(boundary).append("--");
        } else {
            sb.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
            sb.append(body != null ? body : "");
        }
        return sb.toString();
    }

    /**
     * Phân tích chuỗi RFC 5322 / MIME (từ lệnh SMTP DATA hoặc POP3 RETR) thành đối tượng EmailMessage,
     * hỗ trợ bóc tách cả nội dung văn bản và tệp đính kèm Base64.
     */
    public static EmailMessage fromRfc822String(String raw, String defaultFrom, String defaultTo) {
        String from = defaultFrom != null ? defaultFrom : "Unknown";
        String to = defaultTo != null ? defaultTo : "Unknown";
        String subject = "(Không có tiêu đề)";
        String body = raw != null ? raw : "";
        String attachName = null;
        byte[] attachData = null;

        if (raw != null) {
            int headerEnd = raw.indexOf("\r\n\r\n");
            if (headerEnd != -1) {
                String headers = raw.substring(0, headerEnd);
                String rawBody = raw.substring(headerEnd + 4);
                String boundary = null;

                for (String h : headers.split("\r\n")) {
                    String lower = h.toLowerCase();
                    if (lower.startsWith("from:")) {
                        from = h.substring(5).trim();
                    } else if (lower.startsWith("to:")) {
                        to = h.substring(3).trim();
                    } else if (lower.startsWith("subject:")) {
                        subject = h.substring(8).trim();
                    } else if (lower.contains("boundary=")) {
                        int idx = h.indexOf("boundary=");
                        boundary = h.substring(idx + 9).trim();
                        if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                            boundary = boundary.substring(1, boundary.length() - 1);
                        }
                    }
                }

                if (boundary != null && !boundary.isEmpty()) {
                    String[] parts = rawBody.split("--" + java.util.regex.Pattern.quote(boundary));
                    StringBuilder textBuilder = new StringBuilder();
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (trimmed.isEmpty() || "--".equals(trimmed)) continue;

                        int partHeaderEnd = part.indexOf("\r\n\r\n");
                        if (partHeaderEnd == -1) continue;

                        String partHeaders = part.substring(0, partHeaderEnd);
                        String partContent = part.substring(partHeaderEnd + 4).trim();
                        String partHeadersLower = partHeaders.toLowerCase();

                        if (partHeadersLower.contains("content-disposition: attachment") || partHeadersLower.contains("filename=")) {
                            // Trích xuất tên file
                            String fn = extractParam(partHeaders, "filename=");
                            if (fn == null) fn = extractParam(partHeaders, "name=");
                            attachName = fn != null ? fn : "attachment.bin";
                            try {
                                String cleanB64 = partContent.replaceAll("\\s+", "");
                                attachData = Base64.getDecoder().decode(cleanB64.getBytes(StandardCharsets.UTF_8));
                            } catch (Exception ignored) {}
                        } else if (partHeadersLower.contains("text/plain")) {
                            if (textBuilder.length() > 0) textBuilder.append("\n");
                            textBuilder.append(partContent);
                        }
                    }
                    body = textBuilder.toString();
                } else {
                    body = rawBody.trim();
                }
            }
        }

        EmailMessage msg = new EmailMessage(from, to, subject, body);
        msg.setAttachmentName(attachName);
        msg.setAttachmentData(attachData);
        return msg;
    }

    private static String extractParam(String headers, String key) {
        int idx = headers.toLowerCase().indexOf(key.toLowerCase());
        if (idx == -1) return null;
        String sub = headers.substring(idx + key.length()).trim();
        if (sub.startsWith("\"")) {
            int end = sub.indexOf('"', 1);
            if (end != -1) return sub.substring(1, end);
        }
        int end = sub.indexOf(';');
        if (end == -1) end = sub.indexOf('\r');
        if (end == -1) end = sub.indexOf('\n');
        return (end != -1 ? sub.substring(0, end) : sub).trim();
    }
}
