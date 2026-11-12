package mail.server;

import mail.common.EmailMessage;
import mail.common.MailAccount;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xử lý phiên kết nối gửi thư theo giao thức SMTP (RFC 5321 & RFC 4954)
 * Hỗ trợ đầy đủ cả Java Mail Client lẫn Mozilla Thunderbird (AUTH PLAIN / LOGIN, nhiều người nhận)
 */
public class SmtpServerHandler implements Runnable {
    private final Socket socket;
    private final MailServer server;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String mailFrom = "";
    private final List<String> rcptToList = new ArrayList<>();
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

                String logLine = line.toUpperCase().startsWith("AUTH ") ? "AUTH ********" : line;
                server.log("[SMTP CMD từ " + clientIp + "]: " + logLine);
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
            sendResponse("250-mailserver.local greets " + clientIp + "\r\n"
                    + "250-SIZE 26214400\r\n"
                    + "250-8BITMIME\r\n"
                    + "250-SMTPUTF8\r\n"
                    + "250-AUTH PLAIN LOGIN\r\n"
                    + "250 OK");
        } else if (upper.startsWith("HELO")) {
            sendResponse("250 mailserver.local greets " + clientIp);
        } else if (upper.startsWith("AUTH PLAIN")) {
            handleAuthPlain(line.length() > 10 ? line.substring(10).trim() : "");
        } else if (upper.startsWith("AUTH LOGIN")) {
            handleAuthLogin(line.length() > 10 ? line.substring(10).trim() : "");
        } else if (upper.startsWith("MAIL FROM:")) {
            mailFrom = normalizeAddress(extractEmail(line.substring(10).trim()));
            rcptToList.clear();
            sendResponse("250 2.1.0 Sender <" + mailFrom + "> OK");
        } else if (upper.startsWith("RCPT TO:")) {
            String rcpt = normalizeAddress(extractEmail(line.substring(8).trim()));
            if (!rcpt.isEmpty() && !rcptToList.contains(rcpt)) {
                rcptToList.add(rcpt);
            }
            sendResponse("250 2.1.5 Recipient <" + rcpt + "> OK");
        } else if (upper.equals("DATA")) {
            if (mailFrom.isEmpty() || rcptToList.isEmpty()) {
                sendResponse("503 Cần chỉ định MAIL FROM và RCPT TO trước khi gửi DATA");
            } else {
                sendResponse("354 Kết thúc thư bằng dòng chứa một dấu chấm duy nhất <CRLF>.<CRLF>");
                readDataContent();
            }
        } else if (upper.equals("RSET")) {
            mailFrom = "";
            rcptToList.clear();
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

    private void handleAuthPlain(String initialArg) throws IOException {
        String b64 = initialArg;
        if (b64.isEmpty()) {
            sendResponse("334 ");
            String next = reader.readLine();
            if (next == null || "*".equals(next.trim())) {
                sendResponse("501 Authentication cancelled");
                return;
            }
            b64 = next.trim();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(b64);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split("\0", -1);
            String user = parts.length >= 2 ? parts[1] : "";
            if (user.isEmpty() && parts.length >= 1) user = parts[0];
            String pass = parts.length >= 3 ? parts[2] : (parts.length == 2 ? parts[1] : "");
            verifyAndRespondAuth(user, pass);
        } catch (Exception e) {
            sendResponse("535 5.7.8 Authentication credentials invalid");
        }
    }

    private void handleAuthLogin(String initialArg) throws IOException {
        try {
            String userB64 = initialArg;
            if (userB64.isEmpty()) {
                sendResponse("334 VXNlcm5hbWU6");
                String next = reader.readLine();
                if (next == null || "*".equals(next.trim())) {
                    sendResponse("501 Authentication cancelled");
                    return;
                }
                userB64 = next.trim();
            }
            sendResponse("334 UGFzc3dvcmQ6");
            String passB64 = reader.readLine();
            if (passB64 == null || "*".equals(passB64.trim())) {
                sendResponse("501 Authentication cancelled");
                return;
            }
            String user = new String(Base64.getDecoder().decode(userB64), StandardCharsets.UTF_8);
            String pass = new String(Base64.getDecoder().decode(passB64.trim()), StandardCharsets.UTF_8);
            verifyAndRespondAuth(user, pass);
        } catch (Exception e) {
            sendResponse("535 5.7.8 Authentication credentials invalid");
        }
    }

    private void verifyAndRespondAuth(String user, String pass) throws IOException {
        MailAccount acc = MailDatabaseManager.getInstance().authenticate(user, pass);
        if (acc != null) {
            MailDatabaseManager.getInstance().logActivity("SMTP", acc.getEmail(), "LOGIN",
                    "Đăng nhập SMTP thành công (" + clientIp + ")", true);
            server.notifyStats();
            sendResponse("235 2.7.0 Authentication successful");
        } else {
            MailDatabaseManager.getInstance().logActivity("SMTP", user, "LOGIN_FAILED",
                    "Sai mật khẩu SMTP (" + clientIp + ")", false);
            sendResponse("535 5.7.8 Authentication credentials invalid");
        }
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

        String primaryRcpt = rcptToList.get(0);
        EmailMessage parsed = EmailMessage.fromRfc822String(raw.toString(), mailFrom, primaryRcpt);

        for (String rcpt : rcptToList) {
            EmailMessage msg = new EmailMessage(mailFrom, rcpt, parsed.getSubject(), parsed.getBody());
            msg.setId(UUID.randomUUID().toString());
            msg.setAttachmentName(parsed.getAttachmentName());
            msg.setAttachmentData(parsed.getAttachmentData());

            MailDatabaseManager.getInstance().saveEmail(msg);
            MailDatabaseManager.getInstance().logActivity("SMTP", mailFrom, "SEND_EMAIL",
                    "Gửi tới " + rcpt + " (" + msg.getSubject() + ")", true);
            server.log("[SMTP THÀNH CÔNG] Đã lưu thư từ " + mailFrom + " gửi tới " + rcpt + " vào CSDL!");
        }
        server.notifyStats();
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
