package mail.server;

import mail.common.MailConfig;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
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

    private int smtpPort;
    private int pop3Port;

    private ServerSocket smtpSocket;
    private ServerSocket pop3Socket;
    private DatagramSocket discoverySocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;

    private final List<MailServerListener> listeners = new ArrayList<>();

    public MailServer(int smtpPort, int pop3Port) {
        this.smtpPort = smtpPort;
        this.pop3Port = pop3Port;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public int getPop3Port() {
        return pop3Port;
    }

    public void addListener(MailServerListener listener) {
        listeners.add(listener);
    }

    public synchronized boolean start() {
        if (isRunning) return true;

        try {
            smtpSocket = new ServerSocket();
            smtpSocket.setReuseAddress(true);
            smtpSocket.bind(new InetSocketAddress(smtpPort));

            try {
                pop3Socket = new ServerSocket();
                pop3Socket.setReuseAddress(true);
                pop3Socket.bind(new InetSocketAddress(pop3Port));
            } catch (BindException be) {
                if (pop3Socket != null) {
                    try { pop3Socket.close(); } catch (IOException ignored) {}
                }
                int fallbackPort = (pop3Port == MailConfig.LOCAL_POP3_PORT)
                        ? MailConfig.FALLBACK_POP3_PORT
                        : pop3Port + 1000;
                log(">>> [LƯU Ý] Cổng POP3 " + pop3Port + " đang bị ứng dụng khác trên Windows sử dụng -> Tự động chuyển POP3 sang cổng " + fallbackPort);
                pop3Port = fallbackPort;
                pop3Socket = new ServerSocket();
                pop3Socket.setReuseAddress(true);
                pop3Socket.bind(new InetSocketAddress(pop3Port));
            }

            threadPool = Executors.newCachedThreadPool();
            isRunning = true;

            String lanIp = getLocalLanIp();
            log(">>> [MAIL SERVER KHỞI ĐỘNG THÀNH CÔNG]");
            log(">>> Dịch vụ gửi thư SMTP (RFC 5321) lắng nghe tại cổng: " + smtpPort);
            log(">>> Dịch vụ nhận thư POP3 (RFC 1939) lắng nghe tại cổng: " + pop3Port);
            log(">>> Địa chỉ IP LAN (nhập vào máy thứ 2 cùng Wi-Fi): " + lanIp + " (SMTP: " + smtpPort + " / POP3: " + pop3Port + ")");
            log(">>> Cơ Sở Dữ Liệu Mail: Đã nạp thành công (" + MailDatabaseManager.getInstance().getTotalEmailCount() + " thư)");

            threadPool.submit(this::listenSmtp);
            threadPool.submit(this::listenPop3);
            startUdpDiscoveryResponder();

            notifyStats();
            return true;
        } catch (IOException e) {
            if (smtpSocket != null) {
                try { smtpSocket.close(); } catch (IOException ignored) {}
                smtpSocket = null;
            }
            if (pop3Socket != null) {
                try { pop3Socket.close(); } catch (IOException ignored) {}
                pop3Socket = null;
            }
            log("[ERROR] Không thể khởi động Mail Server: " + e.getMessage());
            return false;
        }
    }

    private void startUdpDiscoveryResponder() {
        Thread t = new Thread(() -> {
            try {
                discoverySocket = new DatagramSocket(null);
                discoverySocket.setReuseAddress(true);
                discoverySocket.bind(new InetSocketAddress(MailConfig.UDP_DISCOVERY_PORT));
                byte[] buf = new byte[256];
                while (isRunning && discoverySocket != null && !discoverySocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    discoverySocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    if ("DISCOVER_MAIL_SERVER_REQUEST".equals(msg)) {
                        String reply = "MAIL_SERVER_DISCOVERED:" + smtpPort + ":" + pop3Port;
                        byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket resp = new DatagramPacket(out, out.length, packet.getAddress(), packet.getPort());
                        discoverySocket.send(resp);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "Mail-UDP-Discovery-Responder");
        t.setDaemon(true);
        t.start();
    }

    public static String getLocalLanIp() {
        try {
            String fallbackIp = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = (ni.getDisplayName() + " " + ni.getName()).toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") || name.contains("vbox")
                        || name.contains("wsl") || name.contains("hyper-v") || name.contains("loopback")) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (name.contains("wi-fi") || name.contains("wlan") || name.contains("wireless")) {
                            return ip;
                        }
                        if (fallbackIp == null) fallbackIp = ip;
                    }
                }
            }
            if (fallbackIp != null) return fallbackIp;
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
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
            if (discoverySocket != null && !discoverySocket.isClosed()) discoverySocket.close();
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

