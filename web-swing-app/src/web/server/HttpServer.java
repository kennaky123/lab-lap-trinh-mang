package web.server;

import web.common.HttpConstants;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp điều khiển chính của Máy Chủ Web Server (HTTP/1.1)
 */
public class HttpServer {
    public interface ServerEventListener {
        void onLog(String text);
        void onAccessRecorded(AccessLog log);
        void onStatsUpdated(int totalRequests, int pdfCount, int mp3Count, int mp4Count, long totalBytes);
        void onPostSubmitted(PostSubmission sub);
    }

    public static class AccessLog {
        public long timestamp = System.currentTimeMillis();
        public String method;
        public String uri;
        public int statusCode;
        public String clientIp;

        public AccessLog(String method, String uri, int statusCode, String clientIp) {
            this.method = method;
            this.uri = uri;
            this.statusCode = statusCode;
            this.clientIp = clientIp;
        }

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(new Date(timestamp));
        }
    }

    public static class PostSubmission {
        public long timestamp = System.currentTimeMillis();
        public String name;
        public String studentId;
        public String content;
        public String clientIp;

        public PostSubmission(String name, String id, String content, String ip) {
            this.name = name;
            this.studentId = id;
            this.content = content;
            this.clientIp = ip;
        }

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(new Date(timestamp));
        }
    }

    private final int port;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;

    private final List<AccessLog> accessLogs = Collections.synchronizedList(new ArrayList<>());
    private final List<PostSubmission> postSubmissions = Collections.synchronizedList(new ArrayList<>());
    private final List<ServerEventListener> listeners = new ArrayList<>();

    // Thống kê các dịch vụ đa phương tiện
    private int pdfHits = 0;
    private int mp3Hits = 0;
    private int mp4Hits = 0;
    private int totalRequests = 0;
    private long totalBytesTransferred = 0;

    public HttpServer(int port) {
        this.port = port;
    }

    public void addListener(ServerEventListener l) {
        listeners.add(l);
    }

    public synchronized boolean start() {
        if (isRunning) return true;

        try {
            File root = new File(HttpConstants.WEB_ROOT);
            if (!root.exists()) root.mkdirs();

            serverSocket = new ServerSocket(port);
            threadPool = Executors.newCachedThreadPool();
            isRunning = true;

            log(">>> [WEB SERVER HTTP/1.1 ĐÃ KHỞI ĐỘNG THÀNH CÔNG]");
            log(">>> Cổng dịch vụ: http://localhost:" + port + "/");
            log(">>> Thư mục gốc tài nguyên: " + root.getAbsolutePath());
            log(">>> Hỗ trợ đa dịch vụ: Trang Web (.html), Tài liệu (.pdf), Âm thanh (.mp3), Video (.mp4)");
            log(">>> Hỗ trợ các phương thức HTTP: GET, HEAD, POST");

            threadPool.submit(this::listenClients);
            notifyStats();
            return true;
        } catch (IOException e) {
            log("[ERROR] Không thể mở cổng HTTP " + port + ": " + e.getMessage());
            return false;
        }
    }

    private void listenClients() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                threadPool.submit(new HttpHandler(client, this));
            } catch (SocketException ignored) {
                break;
            } catch (IOException e) {
                if (isRunning) log("[LỖI SERVER]: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        log(">>> [WEB SERVER ĐANG DỪNG] Đóng cổng mạng...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        log(">>> [WEB SERVER ĐÃ DỪNG HOÀN TOÀN]");
    }

    public synchronized void recordAccess(String method, String uri, int statusCode, String clientIp) {
        totalRequests++;
        AccessLog logEntry = new AccessLog(method, uri, statusCode, clientIp);
        accessLogs.add(logEntry);

        for (ServerEventListener l : listeners) {
            l.onAccessRecorded(logEntry);
        }
        notifyStats();
    }

    public synchronized void recordServiceAccess(String fileName, long bytes) {
        totalBytesTransferred += bytes;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) pdfHits++;
        else if (lower.endsWith(".mp3")) mp3Hits++;
        else if (lower.endsWith(".mp4")) mp4Hits++;
        notifyStats();
    }

    public synchronized void savePostSubmission(String name, String id, String content, String ip) {
        PostSubmission sub = new PostSubmission(name, id, content, ip);
        postSubmissions.add(sub);
        for (ServerEventListener l : listeners) {
            l.onPostSubmitted(sub);
        }
    }

    public void log(String text) {
        System.out.println(text);
        for (ServerEventListener l : listeners) {
            l.onLog(text);
        }
    }

    private void notifyStats() {
        for (ServerEventListener l : listeners) {
            l.onStatsUpdated(totalRequests, pdfHits, mp3Hits, mp4Hits, totalBytesTransferred);
        }
    }

    public List<AccessLog> getAccessLogs() { return new ArrayList<>(accessLogs); }
    public List<PostSubmission> getPostSubmissions() { return new ArrayList<>(postSubmissions); }
    public boolean isRunning() { return isRunning; }
}

