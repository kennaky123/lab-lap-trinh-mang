package mail.client;

import mail.common.EmailMessage;
import mail.common.MailConfig;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Động cơ nhận thư theo chuẩn giao thức POP3 (RFC 1939):
 * - Hỗ trợ nhận thư từ Localhost Mail Server (Cổng 1110)
 * - Hỗ trợ nhận thư từ Google Gmail thật (pop.gmail.com cổng 995 SSL)
 */
public class Pop3Client {
    public interface Pop3LogListener {
        void onCommand(String cmd);
        void onResponse(String resp);
    }

    private Pop3LogListener listener;

    public void setLogListener(Pop3LogListener listener) {
        this.listener = listener;
    }

    /**
     * Lấy danh sách thư từ Localhost Mail Server
     */
    public List<EmailMessage> fetchLocalEmails(String host, int port, String user, String pass) {
        List<EmailMessage> emails = new ArrayList<>();
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(15000);

            String resp = readLine(reader);
            if (!resp.startsWith("+OK")) return emails;

            sendCmd(writer, "USER " + user);
            resp = readLine(reader);
            if (!resp.startsWith("+OK")) return emails;

            sendCmd(writer, "PASS " + pass);
            resp = readLine(reader);
            if (!resp.startsWith("+OK")) return emails;

            // Đếm số lượng thư (STAT)
            sendCmd(writer, "STAT");
            resp = readLine(reader);
            String[] parts = resp.split(" ");
            int count = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;

            // Lấy nội dung từng thư (RETR 1, RETR 2, ...)
            for (int i = 1; i <= count; i++) {
                sendCmd(writer, "RETR " + i);
                resp = readLine(reader);
                if (resp.startsWith("+OK")) {
                    EmailMessage msg = parseEmailContent(reader);
                    if (msg != null) {
                        emails.add(msg);
                    }
                }
            }

            sendCmd(writer, "QUIT");
            readLine(reader);
        } catch (Exception e) {
            logResp("Lỗi nhận thư Localhost: " + e.getMessage());
        }
        return emails;
    }

    /**
     * Lấy danh sách thư từ Google Gmail thật (pop.gmail.com:995 SSL)
     */
    public List<EmailMessage> fetchGmailEmails(String user, String appPassword, int limit) {
        List<EmailMessage> emails = new ArrayList<>();
        String cleanAppPass = appPassword != null ? appPassword.replace(" ", "").trim() : "";

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(MailConfig.GMAIL_POP3_HOST, MailConfig.GMAIL_POP3_PORT);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                socket.setSoTimeout(15000);

                String resp = readLine(reader);
                if (!resp.startsWith("+OK")) return emails;

                sendCmd(writer, "USER " + user);
                resp = readLine(reader);

                sendCmd(writer, "PASS " + cleanAppPass);
                resp = readLine(reader);
                if (!resp.startsWith("+OK")) {
                    logResp("Gmail từ chối đăng nhập POP3! Kiểm tra App Password 16 ký tự và bật POP trong cài đặt Gmail.");
                    return emails;
                }

                sendCmd(writer, "STAT");
                resp = readLine(reader);
                String[] parts = resp.split(" ");
                int total = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;

                int start = Math.max(1, total - limit + 1);
                for (int i = start; i <= total; i++) {
                    sendCmd(writer, "RETR " + i);
                    resp = readLine(reader);
                    if (resp.startsWith("+OK")) {
                        EmailMessage msg = parseEmailContent(reader);
                        if (msg != null) emails.add(msg);
                    }
                }

                sendCmd(writer, "QUIT");
                readLine(reader);
            }
        } catch (Exception e) {
            logResp("Lỗi nhận thư Gmail: " + e.getMessage());
        }
        return emails;
    }

    private EmailMessage parseEmailContent(BufferedReader r) throws IOException {
        StringBuilder raw = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            if (".".equals(line)) break;
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            raw.append(line).append("\r\n");
        }
        return EmailMessage.fromRfc822String(raw.toString(), "Unknown", "Unknown");
    }

    private void sendCmd(BufferedWriter w, String cmd) throws IOException {
        w.write(cmd + "\r\n");
        w.flush();
        if (listener != null) {
            listener.onCommand(cmd.startsWith("PASS") ? "PASS ********" : cmd);
        }
    }

    private String readLine(BufferedReader r) throws IOException {
        String l = r.readLine();
        if (l != null && listener != null) {
            listener.onResponse(l);
        }
        return l != null ? l : "";
    }

    private void logResp(String msg) {
        if (listener != null) listener.onResponse(msg);
    }
}
