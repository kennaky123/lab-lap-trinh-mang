package chatapp.common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Đại diện thông tin một người dùng kết nối vào hệ thống
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String ipAddress;
    private int port;
    private long connectedAt;
    private Set<String> rooms;

    public User(String username, String ipAddress, int port) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectedAt = System.currentTimeMillis();
        this.rooms = new HashSet<>();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getConnectedAt() { return connectedAt; }

    public Set<String> getRooms() { return rooms; }
    public void joinRoom(String room) { rooms.add(room); }
    public void leaveRoom(String room) { rooms.remove(room); }
    public boolean isInRoom(String room) { return rooms.contains(room); }

    @Override
    public String toString() {
        return username + " (" + ipAddress + ":" + port + ")";
    }
}

