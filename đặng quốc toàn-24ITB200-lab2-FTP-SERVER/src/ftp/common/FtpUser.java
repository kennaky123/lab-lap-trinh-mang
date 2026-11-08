package ftp.common;

import java.io.Serializable;

/**
 * Đại diện tài khoản người dùng FTP với phân quyền chi tiết
 */
public class FtpUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String homeDirectory;
    private boolean canRead;    // Quyền tải xuống (RETR)
    private boolean canWrite;   // Quyền tải lên (STOR, MKD)
    private boolean canDelete;  // Quyền xóa (DELE, RMD)
    private boolean isEnabled;  // Trạng thái kích hoạt
    private long createdAt;

    public FtpUser(String username, String password, String homeDirectory) {
        this.username = username;
        this.password = password;
        this.homeDirectory = homeDirectory;
        this.canRead = true;
        this.canWrite = true;
        this.canDelete = true;
        this.isEnabled = true;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getHomeDirectory() { return homeDirectory; }
    public void setHomeDirectory(String homeDirectory) { this.homeDirectory = homeDirectory; }

    public boolean isCanRead() { return canRead; }
    public void setCanRead(boolean canRead) { this.canRead = canRead; }

    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean canWrite) { this.canWrite = canWrite; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }

    public long getCreatedAt() { return createdAt; }

    public String getPermissionsSummary() {
        return (canRead ? "R" : "-") + (canWrite ? "W" : "-") + (canDelete ? "D" : "-");
    }
}

