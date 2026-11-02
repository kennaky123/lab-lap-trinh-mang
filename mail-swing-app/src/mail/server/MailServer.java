package mail.server;

import mail.common.MailConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bộ điều khiển Mail Server tích hợp cả hai dịch vụ SMTP (Gửi thư) và POP3 (Nhận thư)
 */
public class MailServer {
    public interface MailServerListener {
        void onLog(String text);
        void onStatsUpdated(int totalAccounts, int totalEmails);
    }

    private final int smtpPort;
    private final int pop3Port;

    private ServerSocket smtpSocket;
    private ServerSocket pop3Socket;
    private boolean isRunning = false;
    private ExecutorService threadPool;

    private final List<MailServerListener> listeners = new ArrayList<>();

    public MailServer(int smtpPort, int pop3Port) {
        this.smtpPort = smtpPort;
        this.pop3Port = pop3Port;
    }

    public void addListener(MailServerListener listener) {
        listeners.add(listener);
    }

    public synchronized boolean start() {
        if (isRunning) return true;

        try {
            smtpSocket = new ServerSocket(smtpPort);
            pop3Socket = new ServerSocket(pop3Port);
            threadPool = Executors.newCachedThreadPool();
            isRunning = true;

            log(">>> [MAIL SERVER KHỞI ĐỘNG THÀNH CÔNG]");
            log(">>> Dịch vụ gửi thư SMTP (RFC 5321) lắng nghe tại cổng: " + smtpPort);
            log(">>> Dịch vụ nhận thư POP3 (RFC 1939) lắng nghe tại cổng: " + pop3Port);
            log(">>> Cơ Sở Dữ Liệu Mail: Đã nạp thành công (" + MailDatabaseManager.getInstance().getTotalEmailCount() + " thư)");

            threadPool.submit(this::listenSmtp);
            threadPool.submit(this::listenPop3);

            notifyStats();
            return true;
        } catch (IOException e) {
            log("[ERROR] Không thể khởi động Mail Server: " + e.getMessage());
            return false;
        }
    }

    private void listenSmtp() {
        while (isRunning && smtpSocket != null && !smtpSocket.isClosed()) {
            try {
                Socket client = smtpSocket.accept();
                threadPool.submit(new SmtpServerHandler(client, this));
            } catch (SocketException ignored) {
                break;
            } catch (IOException e) {
                if (isRunning) log("[SMTP ERROR] " + e.getMessage());
            }
        }
    }

    private void listenPop3() {
        while (isRunning && pop3Socket != null && !pop3Socket.isClosed()) {
            try {
                Socket client = pop3Socket.accept();
                threadPool.submit(new Pop3ServerHandler(client, this));
            } catch (SocketException ignored) {
                break;
            } catch (IOException e) {
                if (isRunning) log("[POP3 ERROR] " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        log(">>> [MAIL SERVER ĐANG DỪNG] Đóng toàn bộ socket...");

        try {
            if (smtpSocket != null && !smtpSocket.isClosed()) smtpSocket.close();
            if (pop3Socket != null && !pop3Socket.isClosed()) pop3Socket.close();
        } catch (IOException ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        log(">>> [MAIL SERVER ĐÃ DỪNG HOÀN TOÀN]");
    }

    public void log(String text) {
        System.out.println(text);
        for (MailServerListener l : listeners) {
            l.onLog(text);
        }
    }

    public void notifyStats() {
        int accCount = MailDatabaseManager.getInstance().getAllAccounts().size();
        int emailCount = MailDatabaseManager.getInstance().getTotalEmailCount();
        for (MailServerListener l : listeners) {
            l.onStatsUpdated(accCount, emailCount);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}

