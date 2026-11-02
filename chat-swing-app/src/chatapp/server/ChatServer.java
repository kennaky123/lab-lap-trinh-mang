package chatapp.server;

import chatapp.common.Config;
import chatapp.common.Message;
import chatapp.common.MessageType;
import chatapp.common.User;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp điều khiển chính của Server:
 * - Lắng nghe kết nối TCP trên port 8888
 * - Quản lý đăng ký người dùng và kết nối Clients
 * - Điều phối tin nhắn Broadcast, Multicast (phòng), Private (1-1 / Bạn bè)
 * - Quản lý kết bạn và đồng bộ danh sách bạn bè
 * - Quản lý truyền tải File, Audio, Video
 * - Phát tín hiệu UDP Beacon để Client tự động tìm thấy Server trong mạng LAN
 */
public class ChatServer {

    public interface ServerListener {
        void onLog(String text);
        void onClientConnected(User user);
        void onClientDisconnected(String username);
        void onStatsUpdated(int onlineCount, int totalMessages);
    }

    private final int port;
    private ServerSocket tcpServerSocket;
    private DatagramSocket udpBroadcastSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final List<ServerListener> listeners = new ArrayList<>();
    private ExecutorService threadPool;
    private boolean isRunning = false;
    private int totalMessagesCount = 0;

    public ChatServer(int port) {
        this.port = port;
    }

    public void addListener(ServerListener listener) {
        listeners.add(listener);
    }

    /**
     * Bắt đầu Server (Cả TCP và UDP)
     */
    public synchronized boolean start() {
        if (isRunning) return true;

        try {
            tcpServerSocket = new ServerSocket(port);
            threadPool = Executors.newCachedThreadPool();
            isRunning = true;

            log(">>> [SERVER KHỞI ĐỘNG THÀNH CÔNG] Lắng nghe TCP tại cổng: " + port);
            log(">>> Địa chỉ IP LAN (nhập vào máy thứ 2 cùng Wi-Fi): " + getLocalLanIp() + " (Cổng: " + port + ")");
            log(">>> Hỗ trợ tính năng: Broadcast (All), Multicast (Phòng), Private 1-1, Kết Bạn, File/Audio/Video.");
            log(">>> Tự động tạo tài khoản theo Username và lưu trữ lịch sử chat vào SQLite CSDL (chat.db).");

            // Luồng chấp nhận kết nối TCP từ các Client
            threadPool.submit(this::listenTcpClients);

            // Luồng phụ UDP Broadcast Discovery (giúp client tìm server tự động)
            startUdpDiscoveryResponder();

            updateStats();
            return true;
        } catch (IOException e) {
            log("[ERROR] Không thể mở cổng TCP " + port + ": " + e.getMessage());
            return false;
        }
    }

    private String getLocalLanIp() {
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
            return "localhost";
        }
    }

    /**
     * Vòng lặp lắng nghe các kết nối TCP
     */
    private void listenTcpClients() {
        while (isRunning && !tcpServerSocket.isClosed()) {
            try {
                Socket clientSocket = tcpServerSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.submit(handler);
            } catch (SocketException e) {
                // Socket đóng khi server dừng
                break;
            } catch (IOException e) {
                if (isRunning) {
                    log("[ERROR] Lỗi khi nhận kết nối client: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Dịch vụ phản hồi UDP Broadcast Discovery
     */
    private void startUdpDiscoveryResponder() {
        threadPool.submit(() -> {
            try {
                udpBroadcastSocket = new DatagramSocket(Config.UDP_BROADCAST_PORT);
                byte[] buffer = new byte[1024];
                while (isRunning && !udpBroadcastSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpBroadcastSocket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength()).trim();
                    if ("DISCOVER_CHAT_SERVER_REQUEST".equals(received)) {
                        byte[] response = "CHAT_SERVER_DISCOVERED".getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(
                                response, response.length, packet.getAddress(), packet.getPort()
                        );
                        udpBroadcastSocket.send(responsePacket);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Dừng Server
     */
    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        log(">>> [SERVER ĐANG DỪNG] Đóng toàn bộ kết nối...");

        // Thông báo tới tất cả client
        Message shutdownNotice = new Message(MessageType.SYSTEM_NOTIFICATION, "SERVER", "Máy chủ đang tắt.");
        broadcast(shutdownNotice);

        // Đóng các handler
        for (ClientHandler handler : clients.values()) {
            handler.close();
        }
        clients.clear();

        try {
            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                tcpServerSocket.close();
            }
            if (udpBroadcastSocket != null && !udpBroadcastSocket.isClosed()) {
                udpBroadcastSocket.close();
            }
        } catch (IOException ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        log(">>> [SERVER ĐÃ DỪNG HOÀN TOÀN]");
        updateStats();
    }

    /**
     * Đăng ký một Client mới vào hệ thống
     */
    public synchronized boolean registerClient(String username, ClientHandler handler) {
        if (clients.containsKey(username)) {
            return false;
        }
        clients.put(username, handler);
        
        // Thông báo giao diện server
        for (ServerListener l : listeners) {
            l.onClientConnected(handler.getUser());
        }

        // Thông báo hệ thống tới các client khác (Broadcast)
        Message notify = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG", username + " đã tham gia phòng chat.");
        broadcast(notify);

        // Cập nhật danh sách Người Dùng và Phòng cho toàn bộ Client
        broadcastUserList();
        broadcastRoomList();
        sendFriendData(username);
        updateStats();
        return true;
    }

    /**
     * Hủy đăng ký một Client khi ngắt kết nối
     */
    public synchronized void unregisterClient(String username) {
        ClientHandler removed = clients.remove(username);
        if (removed != null) {
            log("[TCP DISCONNECT] " + username + " đã ngắt kết nối.");
            for (ServerListener l : listeners) {
                l.onClientDisconnected(username);
            }

            // Thông báo mọi người
            Message notify = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG", username + " đã rời phòng chat.");
            broadcast(notify);

            broadcastUserList();
            updateStats();
        }
    }

    public boolean isUsernameTaken(String username) {
        return clients.containsKey(username);
    }

    /**
     * Gửi tin nhắn Broadcast (Tới TOÀN BỘ Client kết nối)
     */
    public void broadcast(Message msg) {
        totalMessagesCount++;
        for (ClientHandler ch : clients.values()) {
            ch.sendMessage(msg);
        }
        updateStats();
    }

    /**
     * Gửi tin nhắn Multicast theo Phòng (Chỉ tới các Client đã tham gia phòng đó)
     */
    public void multicastToRoom(String room, Message msg) {
        totalMessagesCount++;
        int count = 0;
        for (ClientHandler ch : clients.values()) {
            if (ch.isInRoom(room)) {
                ch.sendMessage(msg);
                count++;
            }
        }
        log("[MULTICAST ROUTING] Đã phân phát tới " + count + " thành viên trong phòng " + room);
        updateStats();
    }

    /**
     * Gửi tin nhắn Private 1-1 (Giữa 2 người dùng):
     * - Luôn gửi lại 1 bản cho người gửi để hiển thị tin nhắn trên giao diện của họ
     * - Gửi tới người nhận nếu đang Online
     * - Nếu người nhận đang Offline, thông báo tin nhắn đã được lưu vào CSDL SQL
     */
    public void sendPrivate(String receiver, Message msg, ClientHandler sender) {
        totalMessagesCount++;
        
        // 1. Gửi lại 1 bản cho người gửi để hiển thị ngay trong bong bóng chat
        if (sender != null) {
            sender.sendMessage(msg);
        }

        // 2. Chuyển tiếp tới người nhận nếu đang Online
        ClientHandler target = clients.get(receiver);
        if (target != null) {
            if (target != sender) {
                target.sendMessage(msg);
            }
        } else {
            // Người nhận đang ngoại tuyến: Thông báo cho người gửi biết tin nhắn đã được lưu trong CSDL SQL
            if (sender != null) {
                Set<String> allUsers = DatabaseManager.getInstance().getAllUsers();
                if (allUsers.contains(receiver)) {
                    Message offlineNotice = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG",
                            "[" + receiver + " đang ngoại tuyến. Tin nhắn đã được lưu vào CSDL SQL và sẽ hiển thị khi họ đăng nhập]");
                    offlineNotice.setReceiver(receiver);
                    sender.sendMessage(offlineNotice);
                } else {
                    Message err = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG",
                            "[Tài khoản '" + receiver + "' chưa từng được tạo trên hệ thống]");
                    err.setReceiver(receiver);
                    sender.sendMessage(err);
                }
            }
        }
        updateStats();
    }

    /**
     * Đồng bộ dữ liệu bạn bè (Danh sách bạn bè đã chấp nhận + Yêu cầu kết bạn chờ duyệt)
     */
    public void sendFriendData(String username) {
        ClientHandler ch = clients.get(username);
        if (ch != null) {
            List<String> friends = DatabaseManager.getInstance().getFriends(username);
            List<String> pending = DatabaseManager.getInstance().getPendingRequests(username);
            List<String> sent = DatabaseManager.getInstance().getSentRequests(username);
            String content = "FRIENDS:" + String.join(",", friends)
                    + ";PENDING:" + String.join(",", pending)
                    + ";SENT:" + String.join(",", sent);
            Message msg = new Message(MessageType.FRIEND_LIST, "SERVER", content);
            ch.sendMessage(msg);
        }
    }

    /**
     * Gửi danh sách toàn bộ người dùng (kèm trạng thái Online / Offline) tới tất cả Client
     */
    public void broadcastUserList() {
        Set<String> allRegisteredUsers = DatabaseManager.getInstance().getAllUsers();
        StringBuilder sb = new StringBuilder();

        // 1. Trước hết thêm các user đang online
        for (String u : clients.keySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(u).append(":ONLINE");
        }

        // 2. Sau đó thêm các user đã từng tạo tài khoản nhưng đang offline
        for (String u : allRegisteredUsers) {
            if (!clients.containsKey(u)) {
                if (sb.length() > 0) sb.append(",");
                sb.append(u).append(":OFFLINE");
            }
        }

        Message msg = new Message(MessageType.USER_LIST, "SERVER", sb.toString());
        for (ClientHandler ch : clients.values()) {
            ch.sendMessage(msg);
        }
    }

    /**
     * Gửi danh sách phòng chat Multicast tới tất cả Client
     */
    public void broadcastRoomList() {
        List<String> rooms = DatabaseManager.getInstance().getAllRooms();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rooms.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(rooms.get(i));
        }
        Message msg = new Message(MessageType.ROOM_LIST, "SERVER", sb.toString());
        for (ClientHandler ch : clients.values()) {
            ch.sendMessage(msg);
        }
    }

    public ClientHandler getClient(String username) {
        return clients.get(username);
    }

    public void log(String text) {
        System.out.println(text);
        for (ServerListener l : listeners) {
            l.onLog(text);
        }
    }

    private void updateStats() {
        for (ServerListener l : listeners) {
            l.onStatsUpdated(clients.size(), totalMessagesCount);
        }
    }

    public Collection<ClientHandler> getActiveClients() {
        return clients.values();
    }

    public boolean isRunning() {
        return isRunning;
    }
}
