package ftp.common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Đại diện thông tin một tệp tin hoặc thư mục trên FTP Server / Client
 */
public class FtpFileItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private long size;
    private boolean isDirectory;
    private long lastModified;
    private String permissions;

    public FtpFileItem(String name, long size, boolean isDirectory, long lastModified) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.lastModified = lastModified;
        this.permissions = isDirectory ? "drwxr-xr-x" : "-rw-r--r--";
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public boolean isDirectory() { return isDirectory; }
    public long getLastModified() { return lastModified; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }

    public String getFormattedSize() {
        if (isDirectory) return "<THƯ MỤC>";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(lastModified));
    }

    /**
     * Xuất chuỗi định dạng Unix Listing tương thích chuẩn lệnh LIST (RFC 959)
     */
    public String toFtpListFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US);
        String dateStr = sdf.format(new Date(lastModified));
        return String.format("%s 1 owner group %10d %s %s\r\n",
                permissions, isDirectory ? 0 : size, dateStr, name);
    }
}

