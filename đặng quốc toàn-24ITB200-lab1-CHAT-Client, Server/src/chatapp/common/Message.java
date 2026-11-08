package chatapp.common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Gói tin dữ liệu truyền tải qua mạng giữa Client và Server
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String sender;
    private String receiver; // "ALL" nếu Broadcast, tên phòng nếu Multicast, username nếu Private
    private String content;
    private long timestamp;
    
    // Thuộc tính dành cho đính kèm File / Audio / Video
    private String fileName;
    private long fileSize;
    private byte[] fileData;
    
    // Phòng chat (đối với Multicast)
    private String room;

    public Message() {
        this.timestamp = System.currentTimeMillis();
    }

    public Message(MessageType type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // Tiện ích tạo tin nhắn Broadcast
    public static Message createBroadcast(String sender, String text) {
        Message msg = new Message(MessageType.CHAT_BROADCAST, sender, text);
        msg.setReceiver("ALL");
        return msg;
    }

    // Tiện ích tạo tin nhắn Multicast theo phòng
    public static Message createMulticast(String sender, String room, String text) {
        Message msg = new Message(MessageType.CHAT_MULTICAST, sender, text);
        msg.setRoom(room);
        msg.setReceiver(room);
        return msg;
    }

    // Tiện ích tạo tin nhắn riêng Private 1-1
    public static Message createPrivate(String sender, String receiver, String text) {
        Message msg = new Message(MessageType.CHAT_PRIVATE, sender, text);
        msg.setReceiver(receiver);
        return msg;
    }

    // Tiện ích tạo tin gửi File đính kèm
    public static Message createFile(MessageType fileType, String sender, String receiver, String room, String fileName, byte[] data) {
        Message msg = new Message(fileType, sender, "[" + fileType.name() + "] " + fileName);
        msg.setReceiver(receiver);
        msg.setRoom(room);
        msg.setFileName(fileName);
        msg.setFileData(data);
        msg.setFileSize(data != null ? data.length : 0);
        return msg;
    }

    // Getter và Setter
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getFormattedFileSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return "[" + getFormattedTime() + "] (" + type + ") " + sender + " -> " + receiver + ": " + content;
    }
}

