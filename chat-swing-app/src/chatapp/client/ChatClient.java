package chatapp.client;

import chatapp.common.Config;
import chatapp.common.Message;
import chatapp.common.MessageType;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;

/**
 * Xử lý kết nối TCP phía Client:
 * - Trao đổi thông điệp với Máy chủ
 * - Nhận và gửi tệp tin, hình ảnh, video, âm thanh
 * - Hỗ trợ gửi và chấp nhận yêu cầu kết bạn
 * - Tự động tải tệp tin nhận được vào thư mục downloads/
 */
public class ChatClient {

    public interface ClientListener {
        void onMessageReceived(Message msg);
        void onUserListUpdated(List<String> onlineUsers);
        void onRoomListUpdated(List<String> rooms);
        void onFriendDataUpdated(List<String> friends, List<String> pendingRequests, List<String> sentRequests);
        void onFriendRequestReceived(String fromUser);
        void onSearchResult(String query, String foundUser, boolean online);
        void onConnected();
        void onConnectionFailed(String reason);
        void onDisconnected();
    }

    private final String host;
    private final int port;
    private final String username;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private final List<ClientListener> listeners = new ArrayList<>();
    private final Set<String> myRooms = new HashSet<>();

    public ChatClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.myRooms.add("Chung (General)");
    }

    public void addListener(ClientListener listener) {
        listeners.add(listener);
    }

    /**
     * Kết nối tới Server qua giao thức TCP
     */
    public boolean connect() {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Gửi thông điệp CONNECT đầu tiên
            Message login = new Message(MessageType.CONNECT, username, "Xin chào");
            out.writeObject(login);
            out.flush();

            // Đợi phản hồi CONNECT_ACK từ Server
            while (true) {
                Object resp = in.readObject();
                if (resp instanceof Message) {
                    Message ack = (Message) resp;
                    if (ack.getType() == MessageType.CONNECT_ACK) {
                        if ("SUCCESS".equals(ack.getContent())) {
                            isConnected = true;
                            for (ClientListener l : listeners) {
                                l.onConnected();
                            }

                            // Bắt đầu luồng lắng nghe tin nhắn TCP
                            Thread listenThread = new Thread(this::listenIncomingMessages);
                            listenThread.setDaemon(true);
                            listenThread.start();
                            return true;
                        } else {
                            String reason = ack.getContent();
                            for (ClientListener l : listeners) {
                                l.onConnectionFailed(reason);
                            }
                            disconnect();
                            return false;
                        }
                    } else {
                        handleReceivedMessage(ack);
                    }
                }
            }
        } catch (Exception e) {
            for (ClientListener l : listeners) {
                l.onConnectionFailed(e.getMessage());
            }
        }
        return false;
    }

    /**
     * Vòng lặp nhận dữ liệu từ Server
     */
    private void listenIncomingMessages() {
        try {
            while (isConnected && socket != null && !socket.isClosed()) {
                Object obj = in.readObject();
                if (obj instanceof Message) {
                    Message msg = (Message) obj;
                    handleReceivedMessage(msg);
                }
            }
        } catch (EOFException | java.net.SocketException ignored) {
        } catch (Exception e) {
            System.err.println("[Client " + username + "] Lỗi nhận tin: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleReceivedMessage(Message msg) {
        // Tự động lưu file/image/audio/video nhận được vào thư mục downloads
        if (msg.getFileData() != null && msg.getFileName() != null) {
            try {
                File dir = new File(Config.DOWNLOADS_DIR);
                if (!dir.exists() && new File("lap-trinh-mang/chat-swing-app/downloads").exists()) {
                    dir = new File("lap-trinh-mang/chat-swing-app/downloads");
                } else if (!dir.exists()) {
                    dir.mkdirs();
                }
                File target = new File(dir, msg.getFileName());
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    fos.write(msg.getFileData());
                }
            } catch (IOException e) {
                System.err.println("[Client] Không thể lưu file đính kèm: " + e.getMessage());
            }
        }

        if (msg.getType() == MessageType.USER_LIST) {
            String[] users = msg.getContent() != null ? msg.getContent().split(",") : new String[0];
            List<String> list = new ArrayList<>();
            for (String u : users) {
                if (!u.trim().isEmpty()) list.add(u.trim());
            }
            for (ClientListener l : listeners) {
                l.onUserListUpdated(list);
            }
        } else if (msg.getType() == MessageType.ROOM_LIST) {
            String[] rooms = msg.getContent() != null ? msg.getContent().split(",") : new String[0];
            List<String> list = new ArrayList<>();
            for (String r : rooms) {
                if (!r.trim().isEmpty()) list.add(r.trim());
            }
            for (ClientListener l : listeners) {
                l.onRoomListUpdated(list);
            }
        } else if (msg.getType() == MessageType.FRIEND_LIST) {
            String content = msg.getContent() != null ? msg.getContent() : "";
            List<String> friends = new ArrayList<>();
            List<String> pending = new ArrayList<>();
            List<String> sent = new ArrayList<>();
            for (String part : content.split(";")) {
                List<String> target = null;
                if (part.startsWith("FRIENDS:")) target = friends;
                else if (part.startsWith("PENDING:")) target = pending;
                else if (part.startsWith("SENT:")) target = sent;
                if (target == null) continue;
                String listStr = part.substring(part.indexOf(':') + 1);
                for (String f : listStr.split(",")) {
                    if (!f.trim().isEmpty()) target.add(f.trim());
                }
            }
            for (ClientListener l : listeners) {
                l.onFriendDataUpdated(friends, pending, sent);
            }
        } else if (msg.getType() == MessageType.SEARCH_RESULT) {
            String found = msg.getContent() == null || msg.getContent().isEmpty() ? null : msg.getContent();
            boolean online = "ONLINE".equals(msg.getReceiver());
            for (ClientListener l : listeners) {
                l.onSearchResult(msg.getRoom(), found, online);
            }
        } else if (msg.getType() == MessageType.FRIEND_REQUEST) {
            for (ClientListener l : listeners) {
                l.onFriendRequestReceived(msg.getSender());
            }
        } else {
            for (ClientListener l : listeners) {
                l.onMessageReceived(msg);
            }
        }
    }

    public synchronized boolean sendMessage(Message msg) {
        if (!isConnected || out == null) return false;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
            return true;
        } catch (IOException e) {
            System.err.println("[Client] Lỗi gửi gói tin: " + e.getMessage());
            return false;
        }
    }

    // Các phương thức bạn bè
    public void searchUser(String name) {
        sendMessage(new Message(MessageType.SEARCH_USER, username, name));
    }

    public void sendFriendRequest(String toUser) {
        Message msg = new Message(MessageType.FRIEND_REQUEST, username, "Yêu cầu kết bạn");
        msg.setReceiver(toUser);
        sendMessage(msg);
    }

    public void acceptFriendRequest(String fromUser) {
        Message msg = new Message(MessageType.FRIEND_ACCEPT, username, "Chấp nhận kết bạn");
        msg.setReceiver(fromUser);
        sendMessage(msg);
    }

    public void declineFriendRequest(String fromUser) {
        Message msg = new Message(MessageType.FRIEND_DECLINE, username, "Từ chối kết bạn");
        msg.setReceiver(fromUser);
        sendMessage(msg);
    }

    // Các phương thức gửi tin tiện ích
    public void sendBroadcast(String text) {
        sendMessage(Message.createBroadcast(username, text));
    }

    public void sendMulticast(String room, String text) {
        sendMessage(Message.createMulticast(username, room, text));
    }

    public void sendPrivate(String receiver, String text) {
        sendMessage(Message.createPrivate(username, receiver, text));
    }

    public boolean sendFileAttachment(MessageType type, String receiver, String room, File file) {
        if (file == null || !file.exists()) return false;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            Message msg = Message.createFile(type, username, receiver, room, file.getName(), data);
            return sendMessage(msg);
        } catch (IOException e) {
            System.err.println("[Client] Đọc file gửi thất bại: " + e.getMessage());
            return false;
        }
    }

    public boolean sendImageAttachment(String receiver, String room, File file) {
        return sendFileAttachment(MessageType.IMAGE_MESSAGE, receiver, room, file);
    }

    public boolean sendAudioVoiceNote(String receiver, String room, byte[] wavData) {
        if (wavData == null || wavData.length == 0) return false;
        String name = "VoiceNote_" + System.currentTimeMillis() + ".wav";
        Message msg = Message.createFile(MessageType.AUDIO_MESSAGE, username, receiver, room, name, wavData);
        return sendMessage(msg);
    }

    public void joinRoom(String room) {
        myRooms.add(room);
        Message msg = new Message(MessageType.JOIN_ROOM, username, room);
        sendMessage(msg);
    }

    public void leaveRoom(String room) {
        myRooms.remove(room);
        Message msg = new Message(MessageType.LEAVE_ROOM, username, room);
        sendMessage(msg);
    }

    public void disconnect() {
        if (!isConnected) return;
        isConnected = false;
        try {
            if (out != null) {
                out.writeObject(new Message(MessageType.DISCONNECT, username, "Tạm biệt"));
                out.flush();
            }
        } catch (Exception ignored) {}

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        for (ClientListener l : listeners) {
            l.onDisconnected();
        }
    }

    public String getUsername() { return username; }
    public boolean isConnected() { return isConnected; }
    public Set<String> getMyRooms() { return myRooms; }
}
