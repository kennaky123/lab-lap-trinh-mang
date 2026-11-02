package mail.server;

import mail.common.EmailMessage;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xử lý phiên kết nối gửi thư theo giao thức SMTP (RFC 5321)
 */
public class SmtpServerHandler implements Runnable {
    private final Socket socket;
    private final MailServer server;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String mailFrom = "";
    private String rcptTo = "";
    private final String clientIp;

    public SmtpServerHandler(Socket socket, MailServer server) {
        this.socket = socket;
        this.server = server;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            sendResponse("220 mailserver.local Simple Mail Transfer Service Ready (SMTP RFC 5321)");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                server.log("[SMTP CMD từ " + clientIp + "]: " + line);
                if (handleCommand(line)) {
                    break;
                }
            }
        } catch (SocketException ignored) {
        } catch (Exception e) {
            server.log("[SMTP ERROR] " + clientIp + ": " + e.getMessage());
        } finally {
            close();
        }
    }

    private boolean handleCommand(String line) throws IOException {
        String upper = line.toUpperCase();

        if (upper.startsWith("EHLO")) {
            sendResponse("250-mailserver.local greets " + clientIp + "\r\n250-8BITMIME\r\n250 OK");
        } else if (upper.startsWith("HELO")) {
            sendResponse("250 mailserver.local greets " + clientIp);
        } else if (upper.startsWith("AUTH LOGIN")) {
            sendResponse("334 VXNlcm5hbWU6");
            reader.readLine();
            sendResponse("334 UGFzc3dvcmQ6");
            reader.readLine();
            sendResponse("235 2.7.0 Authentication successful");
        } else if (upper.startsWith("MAIL FROM:")) {
            mailFrom = normalizeAddress(extractEmail(line.substring(10).trim()));
            sendResponse("250 2.1.0 Sender <" + mailFrom + "> OK");
        } else if (upper.startsWith("RCPT TO:")) {
            rcptTo = normalizeAddress(extractEmail(line.substring(8).trim()));
            sendResponse("250 2.1.5 Recipient <" + rcptTo + "> OK");
        } else if (upper.equals("DATA")) {
            if (mailFrom.isEmpty() || rcptTo.isEmpty()) {
                sendResponse("503 Cần chỉ định MAIL FROM và RCPT TO trước khi gửi DATA");
            } else {
                sendResponse("354 Kết thúc thư bằng dòng chứa một dấu chấm duy nhất <CRLF>.<CRLF>");
                readDataContent();
            }
        } else if (upper.equals("RSET")) {
            mailFrom = "";
            rcptTo = "";
            sendResponse("250 OK");
        } else if (upper.equals("NOOP")) {
            sendResponse("250 OK");
        } else if (upper.equals("QUIT")) {
            sendResponse("221 2.0.0 mailserver.local Service closing transmission channel");
            return true;
        } else {
            sendResponse("500 Command unrecognized (" + line + ")");
        }
        return false;
    }

    private void readDataContent() throws IOException {
        StringBuilder raw = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) {
                break;
            }
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            raw.append(line).append("\r\n");
        }

        EmailMessage msg = EmailMessage.fromRfc822String(raw.toString(), mailFrom, rcptTo);
        msg.setFrom(mailFrom);
        msg.setTo(rcptTo);

        MailDatabaseManager.getInstance().saveEmail(msg);
        MailDatabaseManager.getInstance().logActivity("SMTP", mailFrom, "SEND_EMAIL",
                "Gửi tới " + rcptTo + " (" + msg.getSubject() + ")", true);
        server.notifyStats();

        server.log("[SMTP THÀNH CÔNG] Đã lưu thư từ " + mailFrom + " gửi tới " + rcptTo + " vào CSDL!");
        sendResponse("250 2.0.0 OK: Đã tiếp nhận và lưu thư thành công vào CSDL Server");
    }

    private String extractEmail(String input) {
        Pattern p = Pattern.compile("<(.*?)>");
        Matcher m = p.matcher(input);
        if (m.find()) {
            return m.group(1).trim();
        }
        return input.replace("<", "").replace(">", "").trim();
    }

    private String normalizeAddress(String addr) {
        if (addr == null || addr.isEmpty()) return "";
        String clean = addr.trim();
        if (!clean.contains("@")) {
            return clean + "@local.com";
        }
        return clean;
    }

    private void sendResponse(String resp) throws IOException {
        writer.write(resp + "\r\n");
        writer.flush();
        server.log("[SMTP RESP]: " + resp);
    }

    private void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
