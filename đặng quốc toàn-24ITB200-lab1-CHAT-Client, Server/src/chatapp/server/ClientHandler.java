package chatapp.server;

import chatapp.common.Message;
import chatapp.common.MessageType;
import chatapp.common.User;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Luồng (Thread) quản lý một kết nối TCP từ một Client cụ thể:
 * - Tiếp nhận đăng nhập bằng tên tài khoản (Username)
 * - Tự động tạo hoặc cập nhật tài khoản người dùng trong CSDL SQL (SQLite)
 * - Khôi phục và gửi toàn bộ lịch sử tin nhắn của User từ CSDL SQL khi đăng nhập
 * - Xử lý gửi/chấp nhận/từ chối lời mời kết bạn
 * - Định tuyến tin nhắn Broadcast, Multicast theo phòng, và Chat Riêng bạn bè 1-1
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User user;
    private boolean running = true;
    private final Set<String> subscribedRooms = new HashSet<>();

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Chờ tin nhắn đăng nhập đầu tiên (CONNECT)
            Object firstObj = in.readObject();
            if (firstObj instanceof Message) {
                Message loginMsg = (Message) firstObj;
                if (loginMsg.getType() == MessageType.CONNECT) {
                    String username = loginMsg.getSender() != null ? loginMsg.getSender().trim() : "";
                    if (username.isEmpty()) {
                        Message reject = new Message(MessageType.CONNECT_ACK, "SERVER", "Tên đăng nhập không được để trống!");
                        sendMessage(reject);
                        close();
                        return;
                    }

                    if (server.isUsernameTaken(username)) {
                        Message reject = new Message(MessageType.CONNECT_ACK, "SERVER", "Tài khoản '" + username + "' hiện đang trực tuyến tại một cửa sổ khác!");
                        sendMessage(reject);
                        close();
                        return;
                    }

                    this.user = new User(username, socket.getInetAddress().getHostAddress(), socket.getPort());
                    
                    // Mặc định tự động tham gia phòng General (Multicast)
                    user.joinRoom("Chung (General)");
                    subscribedRooms.add("Chung (General)");

                    // 1. GỬI XÁC NHẬN CONNECT_ACK TRƯỚC HẾT để Client kết nối thành công
                    Message ack = new Message(MessageType.CONNECT_ACK, "SERVER", "SUCCESS");
                    sendMessage(ack);

                    // 2. Ghi nhận tài khoản vào CSDL SQL (Tự động tạo mới nếu chưa có, hoặc cập nhật lần đăng nhập)
                    boolean isNewUser = DatabaseManager.getInstance().registerOrLoginUser(
                            username, socket.getInetAddress().getHostAddress(), socket.getPort()
                    );
                    if (isNewUser) {
                        server.log("[SQL DATABASE] Đã tạo mới tài khoản người dùng: " + username);
                    } else {
                        server.log("[SQL DATABASE] Người dùng đăng nhập: " + username);
                    }

                    // 3. Đăng ký vào danh sách Server và phát thông báo Broadcast
                    boolean registered = server.registerClient(username, this);
                    if (!registered) {
                        close();
                        return;
                    }

                    server.log("[TCP CONNECT] " + username + " đã kết nối từ " + socket.getRemoteSocketAddress());

                    // 4. KHÔI PHỤC LỊCH SỬ TIN NHẮN TỪ CSDL SQL CHO USER NÀY
                    sendUserChatHistory(username);

                    // 5. GỬI DANH SÁCH BẠN BÈ VÀ YÊU CẦU KẾT BẠN
                    server.sendFriendData(username);
                }
            }

            // Vòng lặp nhận tin nhắn từ Client
            while (running) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof Message) {
                        Message msg = (Message) obj;
                        processMessage(msg);
                    }
                } catch (ClassNotFoundException e) {
                    server.log("[ERROR] Lỗi định dạng gói tin từ " + getUsername() + ": " + e.getMessage());
                }
            }
        } catch (EOFException | java.net.SocketException e) {
            // Client ngắt kết nối bình thường hoặc đột ngột
        } catch (Exception e) {
            server.log("[EXCEPTION] Lỗi xử lý client " + (user != null ? user.getUsername() : "Unknown") + ": " + e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Nạp và gửi lịch sử tin nhắn từ CSDL SQL cho User này
     */
    private void sendUserChatHistory(String username) {
        List<Message> history = DatabaseManager.getInstance().getMessagesForUser(username);
        if (!history.isEmpty()) {
            server.log("[SQL HISTORY] Đang khôi phục " + history.size() + " tin nhắn từ CSDL cho tài khoản " + username);
            
            // Gửi thông báo hệ thống nhỏ thông báo đã đồng bộ từ CSDL
            Message notice = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG", 
                    "--- Đã khôi phục " + history.size() + " tin nhắn của bạn từ CSDL SQL ---");
            sendMessage(notice);

            // Gửi từng tin nhắn lịch sử tới client này
            for (Message m : history) {
                sendMessage(m);
            }
        }
    }

    /**
     * Tin nhắn riêng 1-1 (text hoặc file gửi tới một người cụ thể)
     */
    private boolean isPrivateMessage(Message msg) {
        switch (msg.getType()) {
            case CHAT_PRIVATE:
                return true;
            case FILE_TRANSFER:
            case IMAGE_MESSAGE:
            case AUDIO_MESSAGE:
            case VIDEO_MESSAGE:
                return !"ALL".equalsIgnoreCase(msg.getReceiver())
                        && (msg.getRoom() == null || msg.getRoom().isEmpty());
            default:
                return false;
        }
    }

    private boolean isChatContent(Message msg) {
        switch (msg.getType()) {
            case CHAT_BROADCAST:
            case CHAT_MULTICAST:
            case CHAT_PRIVATE:
            case FILE_TRANSFER:
            case IMAGE_MESSAGE:
            case AUDIO_MESSAGE:
            case VIDEO_MESSAGE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Chấp nhận lời mời kết bạn của requester (người đã gửi lời mời tới user hiện tại)
     */
    private void acceptFriend(String requester) {
        String me = getUsername();
        DatabaseManager db = DatabaseManager.getInstance();
        if (!db.hasPendingRequest(requester, me)) {
            server.sendFriendData(me);
            return;
        }
        db.acceptFriendRequest(requester, me);
        server.log("[FRIEND ACCEPT] " + me + " đã chấp nhận kết bạn với " + requester);
        server.sendFriendData(requester);
        server.sendFriendData(me);

        ClientHandler other = server.getClient(requester);
        if (other != null) {
            Message n1 = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG",
                    me + " đã chấp nhận lời mời kết bạn. Hai bạn có thể nhắn tin với nhau!");
            n1.setReceiver(me);
            other.sendMessage(n1);
        }
        Message n2 = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG",
                "Bạn và " + requester + " đã trở thành bạn bè. Hãy bắt đầu trò chuyện!");
        n2.setReceiver(requester);
        sendMessage(n2);
    }

    /**
     * Xử lý gói tin nhận được từ Client
     */
    private void processMessage(Message msg) {
        if (msg == null) return;
        DatabaseManager db = DatabaseManager.getInstance();
        String me = getUsername();

        if (isChatContent(msg)) {
            msg.setSender(me); // Không cho phép giả mạo người gửi

            // CHẶN NHẮN TIN VỚI NGƯỜI CHƯA KẾT BẠN
            if (isPrivateMessage(msg) && !db.areFriends(me, msg.getReceiver())) {
                Message deny = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG",
                        "Không thể gửi: bạn và " + msg.getReceiver() + " chưa là bạn bè.");
                deny.setReceiver(msg.getReceiver());
                sendMessage(deny);
                server.log("[CHẶN] " + me + " -> " + msg.getReceiver() + " (chưa kết bạn)");
                return;
            }

            // Lưu vào CSDL SQL theo từng User (Bảng messages)
            db.saveMessage(msg);
        }

        switch (msg.getType()) {
            case SEARCH_USER: {
                String found = db.findUser(msg.getContent());
                Message res = new Message(MessageType.SEARCH_RESULT, "SERVER", found != null ? found : "");
                res.setReceiver(found != null && server.isUsernameTaken(found) ? "ONLINE" : "OFFLINE");
                res.setRoom(msg.getContent()); // trả lại từ khóa đã tìm
                sendMessage(res);
                server.log("[TÌM KIẾM] " + me + " tìm '" + msg.getContent() + "' -> " + (found != null ? found : "không thấy"));
                break;
            }

            case FRIEND_REQUEST: {
                String target = db.findUser(msg.getReceiver());
                if (target == null || target.equalsIgnoreCase(me)) break;
                if (db.areFriends(me, target)) {
                    server.sendFriendData(me);
                    break;
                }
                // Nếu người kia đã gửi lời mời cho mình trước -> tự động thành bạn bè
                if (db.hasPendingRequest(target, me)) {
                    acceptFriend(target);
                    break;
                }
                if (db.sendFriendRequest(me, target)) {
                    server.log("[FRIEND REQUEST] " + me + " -> " + target);
                    server.sendFriendData(target);
                    server.sendFriendData(me);
                    ClientHandler targetHandler = server.getClient(target);
                    if (targetHandler != null) {
                        Message reqNotice = new Message(MessageType.FRIEND_REQUEST, me, "Lời mời kết bạn");
                        reqNotice.setReceiver(target);
                        targetHandler.sendMessage(reqNotice);
                    }
                }
                break;
            }

            case FRIEND_ACCEPT:
                acceptFriend(msg.getReceiver());
                break;

            case FRIEND_DECLINE: {
                // Dùng cho cả "Từ chối" (lời mời nhận được) và "Thu hồi" (lời mời đã gửi)
                String other = msg.getReceiver();
                if (db.areFriends(me, other)) break; // không xóa bạn bè qua đường này
                db.declineFriendRequest(other, me);
                server.log("[FRIEND DECLINE] " + me + " hủy/từ chối lời mời với " + other);
                server.sendFriendData(other);
                server.sendFriendData(me);
                break;
            }

            case CHAT_BROADCAST:
                server.log("[BROADCAST] " + msg.getSender() + ": " + msg.getContent());
                server.broadcast(msg);
                break;

            case CHAT_MULTICAST:
                server.log("[MULTICAST - Room: " + msg.getRoom() + "] " + msg.getSender() + ": " + msg.getContent());
                server.multicastToRoom(msg.getRoom(), msg);
                break;

            case CHAT_PRIVATE:
                server.log("[CHAT RIÊNG 1-1] " + msg.getSender() + " -> " + msg.getReceiver() + ": " + msg.getContent());
                server.sendPrivate(msg.getReceiver(), msg, this);
                break;

            case FILE_TRANSFER:
            case IMAGE_MESSAGE:
            case AUDIO_MESSAGE:
            case VIDEO_MESSAGE:
                server.log("[" + msg.getType() + "] " + msg.getSender() + " gửi " + msg.getFileName() + " (" + msg.getFormattedFileSize() + ") đến " + msg.getReceiver());
                if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
                    server.broadcast(msg);
                } else if (msg.getRoom() != null && !msg.getRoom().isEmpty()) {
                    server.multicastToRoom(msg.getRoom(), msg);
                } else {
                    server.sendPrivate(msg.getReceiver(), msg, this);
                }
                break;

            case JOIN_ROOM:
                String roomToJoin = msg.getContent();
                if (roomToJoin != null && !roomToJoin.isEmpty()) {
                    subscribedRooms.add(roomToJoin);
                    if (user != null) user.joinRoom(roomToJoin);
                    server.log("[ROOM JOIN] " + getUsername() + " tham gia phòng " + roomToJoin);
                    
                    Message joinNotice = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG", getUsername() + " đã tham gia phòng!");
                    joinNotice.setRoom(roomToJoin);
                    server.multicastToRoom(roomToJoin, joinNotice);
                }
                break;

            case LEAVE_ROOM:
                String roomToLeave = msg.getContent();
                if (roomToLeave != null && subscribedRooms.contains(roomToLeave)) {
                    subscribedRooms.remove(roomToLeave);
                    if (user != null) user.leaveRoom(roomToLeave);
                    server.log("[ROOM LEAVE] " + getUsername() + " rời khỏi phòng " + roomToLeave);
                    
                    Message leaveNotice = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG", getUsername() + " đã rời phòng!");
                    leaveNotice.setRoom(roomToLeave);
                    server.multicastToRoom(roomToLeave, leaveNotice);
                }
                break;

            case DISCONNECT:
                close();
                break;

            default:
                break;
        }
    }

    /**
     * Gửi một gói tin đến client này qua TCP
     */
    public synchronized boolean sendMessage(Message msg) {
        if (!running || out == null) return false;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // Tránh lưu cache đối tượng cũ của Java Serialization
            return true;
        } catch (IOException e) {
            server.log("[ERROR] Gửi tin thất bại tới " + getUsername() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isInRoom(String room) {
        return subscribedRooms.contains(room);
    }

    public Set<String> getSubscribedRooms() {
        return subscribedRooms;
    }

    public String getUsername() {
        return user != null ? user.getUsername() : "Anonymous";
    }

    public User getUser() {
        return user;
    }

    public Socket getSocket() {
        return socket;
    }

    /**
     * Đóng kết nối
     */
    public void close() {
        running = false;
        try {
            if (user != null) {
                server.unregisterClient(user.getUsername());
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
