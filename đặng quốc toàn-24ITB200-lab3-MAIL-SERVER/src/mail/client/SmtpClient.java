package mail.client;

import mail.common.EmailMessage;
import mail.common.MailConfig;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Động cơ gửi thư theo chuẩn giao thức SMTP (RFC 5321):
 * - Hỗ trợ gửi thư qua Localhost Mail Server (Cổng 2525)
 * - Hỗ trợ gửi thư qua Google Gmail thật (smtp.gmail.com cổng 465 SSL)
 */
public class SmtpClient {
    public interface SmtpLogListener {
        void onCommand(String cmd);
        void onResponse(String resp);
    }

    private SmtpLogListener listener;

    public void setLogListener(SmtpLogListener listener) {
        this.listener = listener;
    }

    /**
     * Gửi thư qua Localhost Mail Server
     */
    public boolean sendLocalMail(String host, int port, String from, String to, String subject, String body, String attachName, byte[] attachData) {
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(15000);

            String resp = readMultiline(reader);
            if (!resp.startsWith("220")) return false;

            sendCmd(writer, "EHLO localhost");
            resp = readMultiline(reader);
            if (!resp.startsWith("250")) return false;

            sendCmd(writer, "MAIL FROM:<" + from + ">");
            resp = readMultiline(reader);
            if (!resp.startsWith("250")) return false;

            sendCmd(writer, "RCPT TO:<" + to + ">");
            resp = readMultiline(reader);
            if (!resp.startsWith("250")) return false;

            sendCmd(writer, "DATA");
            resp = readMultiline(reader);
            if (!resp.startsWith("354")) return false;

            // Xây dựng nội dung thư theo chuẩn RFC 5322 / MIME
            EmailMessage msg = new EmailMessage(from, to, subject, body);
            msg.setAttachmentName(attachName);
            msg.setAttachmentData(attachData);
            writeDotStuffed(writer, msg.toRfc822String());
            writer.write("\r\n.\r\n");
            writer.flush();

            resp = readMultiline(reader);
            boolean ok = resp.startsWith("250");
            sendCmd(writer, "QUIT");
            readMultiline(reader);
            return ok;
        } catch (Exception e) {
            logResp("Lỗi gửi thư Localhost: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gửi thư qua máy chủ Gmail thật (Hỗ trợ SSL Port 465)
     * Yêu cầu: Sử dụng Mật khẩu ứng dụng (App Password) 16 ký tự của Google
     */
    public boolean sendGmail(String username, String appPassword, String to, String subject, String body, String attachName, byte[] attachData) {
        String host = MailConfig.GMAIL_SMTP_HOST;
        int port = 465; // Cổng SSL trực tiếp của Gmail
        String cleanAppPass = appPassword != null ? appPassword.replace(" ", "").trim() : "";

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                socket.setSoTimeout(15000);

                String resp = readMultiline(reader);
                if (!resp.startsWith("220")) return false;

                sendCmd(writer, "EHLO " + host);
                resp = readMultiline(reader);
                if (!resp.startsWith("250")) return false;

                // Xác thực AUTH LOGIN
                sendCmd(writer, "AUTH LOGIN");
                resp = readMultiline(reader);
                if (!resp.startsWith("334")) return false;

                // Gửi base64 Username
                sendCmd(writer, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
                resp = readMultiline(reader);
                if (!resp.startsWith("334")) return false;

                // Gửi base64 Password (đã loại bỏ khoảng trắng trong App Password 16 ký tự)
                sendCmd(writer, Base64.getEncoder().encodeToString(cleanAppPass.getBytes(StandardCharsets.UTF_8)));
                resp = readMultiline(reader);
                if (!resp.startsWith("235")) {
                    logResp("Gmail từ chối xác thực! Vui lòng dùng Mật khẩu ứng dụng (App Password 16 ký tự).");
                    return false;
                }

                // Gửi MAIL FROM & RCPT TO
                sendCmd(writer, "MAIL FROM:<" + username + ">");
                resp = readMultiline(reader);
                if (!resp.startsWith("250")) return false;

                sendCmd(writer, "RCPT TO:<" + to + ">");
                resp = readMultiline(reader);
                if (!resp.startsWith("250")) return false;

                sendCmd(writer, "DATA");
                resp = readMultiline(reader);
                if (!resp.startsWith("354")) return false;

                EmailMessage msg = new EmailMessage(username, to, subject, body);
                msg.setAttachmentName(attachName);
                msg.setAttachmentData(attachData);
                writeDotStuffed(writer, msg.toRfc822String());
                writer.write("\r\n.\r\n");
                writer.flush();

                resp = readMultiline(reader);
                boolean ok = resp.startsWith("250");
                sendCmd(writer, "QUIT");
                readMultiline(reader);
                return ok;
            }
        } catch (Exception e) {
            logResp("Lỗi gửi Gmail: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ghi dữ liệu theo chuẩn dot-stuffing của RFC 5321 (dòng bắt đầu bằng '.' sẽ được nhân đôi thành '..')
     */
    private void writeDotStuffed(BufferedWriter w, String content) throws IOException {
        String[] lines = content.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(".")) {
                w.write(".");
            }
            w.write(line);
            if (i < lines.length - 1) {
                w.write("\r\n");
            }
        }
    }

    private void sendCmd(BufferedWriter w, String cmd) throws IOException {
        w.write(cmd + "\r\n");
        w.flush();
        if (listener != null) {
            if (cmd.startsWith("AUTH") || cmd.length() > 40) {
                listener.onCommand(cmd.substring(0, Math.min(cmd.length(), 28)) + "...");
            } else {
                listener.onCommand(cmd);
            }
        }
    }

    /**
     * Đọc phản hồi đơn dòng hoặc đa dòng chuẩn RFC 5321 (ví dụ 250-... tiếp nối bởi 250 OK)
     */
    private String readMultiline(BufferedReader r) throws IOException {
        String line;
        String last = "";
        while ((line = r.readLine()) != null) {
            last = line;
            if (listener != null) listener.onResponse(line);
            if (line.length() < 4 || line.charAt(3) != '-') break;
        }
        return last;
    }

    private void logResp(String msg) {
        if (listener != null) listener.onResponse(msg);
    }
}
