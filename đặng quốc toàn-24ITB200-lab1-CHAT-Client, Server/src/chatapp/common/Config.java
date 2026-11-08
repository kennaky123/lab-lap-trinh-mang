package chatapp.common;

/**
 * Cấu hình chung cho toàn bộ ứng dụng Chat (TCP, UDP, Multicast, Broadcast)
 */
public class Config {
    // TCP Server Port
    public static final int DEFAULT_TCP_PORT = 8888;
    
    // UDP Multicast Group & Port (Dành cho chức năng Multicast qua UDP Socket)
    public static final String UDP_MULTICAST_GROUP = "230.0.0.1";
    public static final int UDP_MULTICAST_PORT = 4446;
    
    // UDP Broadcast Port (Dành cho UDP Broadcast Discovery)
    public static final int UDP_BROADCAST_PORT = 8889;
    
    // Danh sách các phòng Multicast mặc định trên Server (Đã loại bỏ 3 phòng theo yêu cầu)
    public static final String[] DEFAULT_ROOMS = {
        "Chung (General)"
    };
    
    // Đường dẫn thư mục lưu file tải về
    public static final String DOWNLOADS_DIR = "downloads";
    
    // Đường dẫn lưu trữ cơ sở dữ liệu
    public static final String DATA_DIR = "chat_data";
}
