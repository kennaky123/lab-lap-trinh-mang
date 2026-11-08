package mail.server;

import mail.common.EmailMessage;
import mail.common.MailAccount;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý phiên nhận thư theo giao thức POP3 (RFC 1939)
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

                server.log("[POP3 CMD từ " + clientIp + "]: " + (line.toUpperCase().startsWith("PASS") ? "PASS ********" : line));
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
                    if (!m.isDeleted()) totalBytes += m.getApproximateSize();
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
                            writer.write((i + 1) + " " + m.getApproximateSize() + "\r\n");
                        }
                    }
                    writer.write(".\r\n");
                    writer.flush();
                } else {
                    int idx = parseIndex(argument);
                    if (idx >= 0 && idx < userEmails.size() && !userEmails.get(idx).isDeleted()) {
                        sendResponse("+OK " + (idx + 1) + " " + userEmails.get(idx).getApproximateSize());
                    } else {
                        sendResponse("-ERR No such message");
                    }
                }
                break;

            case "RETR":
                if (!checkAuth()) return false;
                int retrIdx = parseIndex(argument);
                if (retrIdx >= 0 && retrIdx < userEmails.size() && !userEmails.get(retrIdx).isDeleted()) {
                    EmailMessage m = userEmails.get(retrIdx);
                    String rfc822 = m.toRfc822String();
                    sendResponse("+OK " + rfc822.getBytes(StandardCharsets.UTF_8).length + " octets");
                    String[] rfcLines = rfc822.split("\r?\n", -1);
                    for (String rfcLine : rfcLines) {
                        if (rfcLine.startsWith(".")) {
                            writer.write("." + rfcLine + "\r\n");
                        } else {
                            writer.write(rfcLine + "\r\n");
                        }
                    }
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

        MailAccount acc = MailDatabaseManager.getInstance().authenticate(tempUser, pass);
        if (acc != null) {
            loggedInAccount = acc;
            userEmails = MailDatabaseManager.getInstance().getEmailsForRecipient(acc.getEmail());
            MailDatabaseManager.getInstance().logActivity("POP3", acc.getEmail(), "LOGIN", "Đăng nhập POP3 thành công", true);
            server.notifyStats();
            sendResponse("+OK Mailbox open, " + userEmails.size() + " messages");
        } else {
            MailDatabaseManager.getInstance().logActivity("POP3", tempUser, "LOGIN_FAILED", "Sai mật khẩu", false);
            sendResponse("-ERR Authentication failed");
        }
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

