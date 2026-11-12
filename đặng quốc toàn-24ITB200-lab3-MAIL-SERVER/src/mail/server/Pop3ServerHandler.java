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

/**
 * Xử lý phiên nhận thư theo giao thức POP3 (RFC 1939, RFC 2449, RFC 5034)
 * Hỗ trợ đầy đủ cả Java Mail Client lẫn Mozilla Thunderbird (CAPA, UIDL, TOP, AUTH PLAIN/LOGIN)
 */
public class Pop3ServerHandler implements Runnable {
    private final Socket socket;
    private final MailServer server;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String tempUser = "";
    private MailAccount loggedInAccount = null;
    private List<EmailMessage> userEmails = new ArrayList<>();
    private final String clientIp;

    public Pop3ServerHandler(Socket socket, MailServer server) {
        this.socket = socket;
        this.server = server;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            sendResponse("+OK POP3 Server Ready (RFC 1939 Ready)");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String upperLine = line.toUpperCase();
                String logLine = (upperLine.startsWith("PASS") || upperLine.startsWith("AUTH "))
                        ? commandPrefix(line) + " ********"
                        : line;
                server.log("[POP3 CMD từ " + clientIp + "]: " + logLine);
                if (handleCommand(line)) {
                    break;
                }
            }
        } catch (SocketException ignored) {
        } catch (Exception e) {
            server.log("[POP3 ERROR] " + clientIp + ": " + e.getMessage());
        } finally {
            close();
        }
    }

    private boolean handleCommand(String line) throws IOException {
        String command = line;
        String argument = "";
        int space = line.indexOf(' ');
        if (space != -1) {
            command = line.substring(0, space).toUpperCase();
            argument = line.substring(space + 1).trim();
        } else {
            command = line.toUpperCase();
        }

        switch (command) {
            case "CAPA":
                sendResponse("+OK Capability list follows");
                writer.write("USER\r\n");
                writer.write("UIDL\r\n");
                writer.write("TOP\r\n");
                writer.write("SASL PLAIN LOGIN\r\n");
                writer.write("RESP-CODES\r\n");
                writer.write("EXPIRE NEVER\r\n");
                writer.write("IMPLEMENTATION JavaMailServer\r\n");
                writer.write(".\r\n");
                writer.flush();
                break;

            case "AUTH":
                handleAuth(argument);
                break;

            case "USER":
                tempUser = argument;
                sendResponse("+OK User accepted, please send password");
                break;

            case "PASS":
                handlePass(argument);
                break;

            case "STAT":
                if (!checkAuth()) return false;
                long totalBytes = 0;
                for (EmailMessage m : userEmails) {
                    if (!m.isDeleted()) totalBytes += m.getRfc822ByteSize();
                }
                int activeCount = getActiveMessageCount();
                sendResponse("+OK " + activeCount + " " + totalBytes);
                break;

            case "LIST":
                if (!checkAuth()) return false;
                if (argument.isEmpty()) {
                    sendResponse("+OK " + getActiveMessageCount() + " messages");
                    for (int i = 0; i < userEmails.size(); i++) {
                        EmailMessage m = userEmails.get(i);
                        if (!m.isDeleted()) {
                            writer.write((i + 1) + " " + m.getRfc822ByteSize() + "\r\n");
                        }
                    }
                    writer.write(".\r\n");
                    writer.flush();
                } else {
                    int idx = parseIndex(argument);
                    if (idx >= 0 && idx < userEmails.size() && !userEmails.get(idx).isDeleted()) {
                        sendResponse("+OK " + (idx + 1) + " " + userEmails.get(idx).getRfc822ByteSize());
                    } else {
                        sendResponse("-ERR No such message");
                    }
                }
                break;

            case "UIDL":
                if (!checkAuth()) return false;
                if (argument.isEmpty()) {
                    sendResponse("+OK");
                    for (int i = 0; i < userEmails.size(); i++) {
                        EmailMessage m = userEmails.get(i);
                        if (!m.isDeleted()) {
                            writer.write((i + 1) + " " + m.getId() + "\r\n");
                        }
                    }
                    writer.write(".\r\n");
                    writer.flush();
                } else {
                    int uidlIdx = parseIndex(argument);
                    if (uidlIdx >= 0 && uidlIdx < userEmails.size() && !userEmails.get(uidlIdx).isDeleted()) {
                        sendResponse("+OK " + (uidlIdx + 1) + " " + userEmails.get(uidlIdx).getId());
                    } else {
                        sendResponse("-ERR No such message");
                    }
                }
                break;

            case "TOP":
                if (!checkAuth()) return false;
                handleTop(argument);
                break;

            case "RETR":
                if (!checkAuth()) return false;
                int retrIdx = parseIndex(argument);
                if (retrIdx >= 0 && retrIdx < userEmails.size() && !userEmails.get(retrIdx).isDeleted()) {
                    EmailMessage m = userEmails.get(retrIdx);
                    String rfc822 = m.toRfc822String();
                    sendResponse("+OK " + rfc822.getBytes(StandardCharsets.UTF_8).length + " octets");
                    writeDotStuffedLines(rfc822.split("\r?\n", -1));
                    writer.write(".\r\n");
                    writer.flush();
                    m.setRead(true);
                    MailDatabaseManager.getInstance().logActivity("POP3", loggedInAccount.getEmail(), "READ_EMAIL", "Đọc thư: " + m.getSubject(), true);
                } else {
                    sendResponse("-ERR No such message");
                }
                break;

            case "DELE":
                if (!checkAuth()) return false;
                int delIdx = parseIndex(argument);
                if (delIdx >= 0 && delIdx < userEmails.size() && !userEmails.get(delIdx).isDeleted()) {
                    userEmails.get(delIdx).setDeleted(true);
                    sendResponse("+OK Message " + (delIdx + 1) + " marked as deleted");
                } else {
                    sendResponse("-ERR Message already deleted or not found");
                }
                break;

            case "RSET":
                if (!checkAuth()) return false;
                for (EmailMessage m : userEmails) m.setDeleted(false);
                sendResponse("+OK Reset state");
                break;

            case "NOOP":
                sendResponse("+OK");
                break;

            case "QUIT":
                if (loggedInAccount != null) {
                    for (EmailMessage m : userEmails) {
                        if (m.isDeleted()) {
                            MailDatabaseManager.getInstance().deleteEmail(loggedInAccount.getEmail(), m.getId());
                        }
                    }
                    server.notifyStats();
                }
                sendResponse("+OK POP3 Server signing off");
                return true;

            default:
                sendResponse("-ERR Unknown command (" + command + ")");
                break;
        }
        return false;
    }

    private void handlePass(String pass) throws IOException {
        if (tempUser.isEmpty()) {
            sendResponse("-ERR USER first");
            return;
        }
        authenticateAndOpenMailbox(tempUser, pass);
    }

    private void handleAuth(String argument) throws IOException {
        if (argument.isEmpty()) {
            sendResponse("+OK List of supported mechanisms");
            writer.write("PLAIN\r\n");
            writer.write("LOGIN\r\n");
            writer.write(".\r\n");
            writer.flush();
            return;
        }

        String[] parts = argument.split("\\s+", 2);
        String mech = parts[0].toUpperCase();
        String initial = parts.length > 1 ? parts[1].trim() : "";

        if ("PLAIN".equals(mech)) {
            if (initial.isEmpty()) {
                sendResponse("+ ");
                String next = reader.readLine();
                if (next == null || "*".equals(next.trim())) {
                    sendResponse("-ERR Authentication cancelled");
                    return;
                }
                initial = next.trim();
            }
            try {
                String payload = new String(Base64.getDecoder().decode(initial), StandardCharsets.UTF_8);
                String[] authParts = payload.split("\0", -1);
                String user = authParts.length >= 2 ? authParts[1] : "";
                if (user.isEmpty() && authParts.length >= 1) user = authParts[0];
                String pass = authParts.length >= 3 ? authParts[2] : (authParts.length == 2 ? authParts[1] : "");
                authenticateAndOpenMailbox(user, pass);
            } catch (Exception e) {
                sendResponse("-ERR [AUTH] Invalid credentials");
            }
        } else if ("LOGIN".equals(mech)) {
            try {
                String userB64 = initial;
                if (userB64.isEmpty()) {
                    sendResponse("+ VXNlcm5hbWU6");
                    String next = reader.readLine();
                    if (next == null || "*".equals(next.trim())) {
                        sendResponse("-ERR Authentication cancelled");
                        return;
                    }
                    userB64 = next.trim();
                }
                sendResponse("+ UGFzc3dvcmQ6");
                String passB64 = reader.readLine();
                if (passB64 == null || "*".equals(passB64.trim())) {
                    sendResponse("-ERR Authentication cancelled");
                    return;
                }
                String user = new String(Base64.getDecoder().decode(userB64), StandardCharsets.UTF_8);
                String pass = new String(Base64.getDecoder().decode(passB64.trim()), StandardCharsets.UTF_8);
                authenticateAndOpenMailbox(user, pass);
            } catch (Exception e) {
                sendResponse("-ERR [AUTH] Invalid credentials");
            }
        } else {
            sendResponse("-ERR Unsupported authentication mechanism");
        }
    }

    private void authenticateAndOpenMailbox(String user, String pass) throws IOException {
        MailAccount acc = MailDatabaseManager.getInstance().authenticate(user, pass);
        if (acc != null) {
            loggedInAccount = acc;
            userEmails = MailDatabaseManager.getInstance().getEmailsForRecipient(acc.getEmail());
            MailDatabaseManager.getInstance().logActivity("POP3", acc.getEmail(), "LOGIN", "Đăng nhập POP3 thành công", true);
            server.notifyStats();
            sendResponse("+OK Mailbox open, " + userEmails.size() + " messages");
        } else {
            MailDatabaseManager.getInstance().logActivity("POP3", user, "LOGIN_FAILED", "Sai mật khẩu", false);
            sendResponse("-ERR [AUTH] Authentication failed");
        }
    }

    private void handleTop(String argument) throws IOException {
        String[] parts = argument.trim().split("\\s+");
        if (parts.length < 2) {
            sendResponse("-ERR Usage: TOP <msg> <lines>");
            return;
        }
        int idx = parseIndex(parts[0]);
        int maxBodyLines;
        try {
            maxBodyLines = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendResponse("-ERR Invalid line count");
            return;
        }
        if (maxBodyLines < 0 || idx < 0 || idx >= userEmails.size() || userEmails.get(idx).isDeleted()) {
            sendResponse("-ERR No such message");
            return;
        }

        String rfc822 = userEmails.get(idx).toRfc822String();
        int sep = rfc822.indexOf("\r\n\r\n");
        sendResponse("+OK top of message follows");
        if (sep == -1) {
            writeDotStuffedLines(rfc822.split("\r?\n", -1));
        } else {
            String headers = rfc822.substring(0, sep);
            String body = rfc822.substring(sep + 4);
            writeDotStuffedLines(headers.split("\r?\n", -1));
            writer.write("\r\n");
            if (maxBodyLines > 0 && !body.isEmpty()) {
                String[] bodyLines = body.split("\r?\n", -1);
                int limit = Math.min(maxBodyLines, bodyLines.length);
                for (int i = 0; i < limit; i++) {
                    String l = bodyLines[i];
                    writer.write((l.startsWith(".") ? "." + l : l) + "\r\n");
                }
            }
        }
        writer.write(".\r\n");
        writer.flush();
    }

    private void writeDotStuffedLines(String[] lines) throws IOException {
        for (String l : lines) {
            if (l.startsWith(".")) {
                writer.write("." + l + "\r\n");
            } else {
                writer.write(l + "\r\n");
            }
        }
    }

    private String commandPrefix(String line) {
        int sp = line.indexOf(' ');
        return sp == -1 ? line.toUpperCase() : line.substring(0, sp).toUpperCase();
    }

    private boolean checkAuth() throws IOException {
        if (loggedInAccount == null) {
            sendResponse("-ERR Please authenticate with USER and PASS");
            return false;
        }
        return true;
    }

    private int getActiveMessageCount() {
        int c = 0;
        for (EmailMessage m : userEmails) {
            if (!m.isDeleted()) c++;
        }
        return c;
    }

    private int parseIndex(String arg) {
        try {
            return Integer.parseInt(arg) - 1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void sendResponse(String resp) throws IOException {
        writer.write(resp + "\r\n");
        writer.flush();
        server.log("[POP3 RESP]: " + resp);
    }

    private void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}

