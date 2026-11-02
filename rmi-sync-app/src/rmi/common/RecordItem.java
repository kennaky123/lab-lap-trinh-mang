package rmi.common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Đối tượng dữ liệu được quản lý và đồng bộ hóa giữa 2 Server qua RMI
 */
public class RecordItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;              // Mã định danh duy nhất (VD: SV001)
    private String name;            // Tên sinh viên / Tên bản ghi
    private String category;        // Ngành học / Phân loại
    private double value;           // Điểm số / Giá trị
    private long lastModified;      // Thời gian sửa đổi lần cuối (phục vụ xử lý xung đột timestamp)
    private String lastUpdatedByServer; // Tên máy chủ cập nhật (Server 1 hoặc Server 2)

    public RecordItem() {
        this.lastModified = System.currentTimeMillis();
    }

    public RecordItem(String id, String name, String category, double value, String serverName) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.value = value;
        this.lastModified = System.currentTimeMillis();
        this.lastUpdatedByServer = serverName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public String getLastUpdatedByServer() { return lastUpdatedByServer; }
    public void setLastUpdatedByServer(String lastUpdatedByServer) { this.lastUpdatedByServer = lastUpdatedByServer; }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(lastModified));
    }

    @Override
    public String toString() {
        return "[" + id + "] " + name + " (" + category + ") - " + value + " [Server: " + lastUpdatedByServer + "]";
    }
}

