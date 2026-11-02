package ftp.server;

import ftp.common.FtpConfig;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp điều khiển chính của FTP Server:
 * - Lắng nghe kết nối TCP tại Control Port (2121)
 * - Khởi tạo luồng xử lý riêng cho từng Client kết nối
 * - Thống kê lưu lượng truyền tải và cập nhật giao diện Server
 */
public class FtpServer {
    public interface ServerEventListener {
        void onLog(String text);
        void onStatsChanged(int activeCount, long totalUploads, long totalDownloads, long totalBytes);
        void onClientsChanged(List<FtpClientHandler> activeClients);
    }

    private final int port;
    private ServerSocket serverSocket;
    private DatagramSocket udpDiscoverySocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;

    private final List<FtpClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private final List<ServerEventListener> listeners = new ArrayList<>();

    private long totalUploadCount = 0;
    private long totalDownloadCount = 0;
    private long totalBytesTransferred = 0;

    public FtpServer(int port) {
        this.port = port;
    }

    public void addListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    public synchronized boolean start() {
        if (isRunning) return true;
        try {
            serverSocket = new ServerSocket(port);
            threadPool = Executors.newCachedThreadPool();
            isRunning = true;

            log(">>> [MÁY CHỦ FTP ĐÃ KHỞI ĐỘNG THÀNH CÔNG]");
            log(">>> Địa chỉ IP LAN (nhập vào máy thứ 2 cùng Wi-Fi): " + getLocalLanIp() + " (Cổng: " + port + ")");
            log(">>> Cổng điều khiển (Control Port): " + port);
            log(">>> Chuẩn giao thức: RFC 959 (Hỗ trợ FileZilla, WinSCP, Windows Explorer)");
            log(">>> Dải cổng Passive (PASV): " + FtpConfig.PASV_PORT_MIN + " -> " + FtpConfig.PASV_PORT_MAX);

            threadPool.submit(this::listenClients);
            startUdpDiscoveryResponder();
            notifyStats();
            return true;
        } catch (IOException e) {
            log("[ERROR] Không thể mở cổng FTP " + port + ": " + e.getMessage());
            return false;
        }
    }

    private void listenClients() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                FtpClientHandler handler = new FtpClientHandler(clientSocket, this);
                activeClients.add(handler);
                notifyClients();
                notifyStats();
                threadPool.submit(handler);
            } catch (SocketException ignored) {
                break;
            } catch (IOException e) {
                if (isRunning) {
                    log("[ERROR] Lỗi chấp nhận client: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        log(">>> [MÁY CHỦ FTP ĐANG DỪNG] Đóng toàn bộ phiên...");

        for (FtpClientHandler handler : activeClients) {
            handler.closeSession();
        }
        activeClients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (udpDiscoverySocket != null && !udpDiscoverySocket.isClosed()) {
                udpDiscoverySocket.close();
            }
        } catch (IOException ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        log(">>> [MÁY CHỦ FTP ĐÃ DỪNG HOÀN TOÀN]");
        notifyClients();
        notifyStats();
    }

    public void onUserLogin(FtpClientHandler handler) {
        log("[XÁC THỰC] " + handler.getUsername() + " đã đăng nhập từ " + handler.getClientIp());
        notifyClients();
        notifyStats();
    }

    public void onUserDisconnect(FtpClientHandler handler) {
        activeClients.remove(handler);
        log("[NGẮT KẾT NỐI] Client " + handler.getClientIp() + " đã rời khỏi máy chủ");
        notifyClients();
        notifyStats();
    }

    public synchronized void onFileTransferred(String type, String fileName, long bytes, String username) {
        if ("UPLOAD".equalsIgnoreCase(type)) {
            totalUploadCount++;
        } else {
            totalDownloadCount++;
        }
        totalBytesTransferred += bytes;
        notifyStats();
    }

    public void log(String text) {
        System.out.println(text);
        for (ServerEventListener l : listeners) {
            l.onLog(text);
        }
    }

    private void notifyStats() {
        for (ServerEventListener l : listeners) {
            l.onStatsChanged(activeClients.size(), totalUploadCount, totalDownloadCount, totalBytesTransferred);
        }
    }

    private void notifyClients() {
        for (ServerEventListener l : listeners) {
            l.onClientsChanged(new ArrayList<>(activeClients));
        }
    }

    private void startUdpDiscoveryResponder() {
        threadPool.submit(() -> {
            try {
                udpDiscoverySocket = new DatagramSocket(FtpConfig.UDP_DISCOVERY_PORT);
                byte[] buf = new byte[256];
                while (isRunning && !udpDiscoverySocket.isClosed()) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    udpDiscoverySocket.receive(pkt);
                    String req = new String(pkt.getData(), 0, pkt.getLength()).trim();
                    if ("DISCOVER_FTP_SERVER_REQUEST".equals(req)) {
                        byte[] resp = ("FTP_SERVER_DISCOVERED:" + port).getBytes();
                        DatagramPacket reply = new DatagramPacket(resp, resp.length, pkt.getAddress(), pkt.getPort());
                        udpDiscoverySocket.send(reply);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}

