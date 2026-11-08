package chatapp.common;

import java.io.Serializable;

/**
 * Định nghĩa các loại thông điệp trao đổi giữa Client và Server
 */
public enum MessageType implements Serializable {
    CONNECT,           // Yêu cầu kết nối / đăng nhập
    CONNECT_ACK,       // Phản hồi kết nối thành công/thất bại
    DISCONNECT,        // Ngắt kết nối
    USER_LIST,         // Cập nhật danh sách người dùng online/offline
    ROOM_LIST,         // Danh sách các phòng Multicast
    JOIN_ROOM,         // Tham gia phòng Multicast
    LEAVE_ROOM,        // Rời phòng Multicast
    
    // Hệ thống Bạn bè (Friend System)
    FRIEND_REQUEST,    // Gửi lời mời kết bạn (sender -> receiver)
    FRIEND_ACCEPT,     // Chấp nhận kết bạn (sender -> receiver)
    FRIEND_DECLINE,    // Từ chối kết bạn (sender -> receiver)
    FRIEND_LIST,       // Đồng bộ danh sách bạn bè và lời mời kết bạn
    SEARCH_USER,       // Client tìm kiếm tài khoản theo tên
    SEARCH_RESULT,     // Server trả kết quả tìm kiếm (content = tên tìm thấy hoặc rỗng)
    
    CHAT_BROADCAST,    // Tin nhắn Broadcast (tới toàn bộ mọi người)
    CHAT_MULTICAST,    // Tin nhắn Multicast (tới 1 nhóm/phòng cụ thể)
    CHAT_PRIVATE,      // Tin nhắn riêng P2P/Private (1-1 / Kênh bạn bè)
    
    FILE_TRANSFER,     // Truyền file tài liệu đính kèm
    IMAGE_MESSAGE,     // Gửi hình ảnh trực tiếp (xem trước thumbnail như Zalo/Messenger)
    AUDIO_MESSAGE,     // Tin nhắn âm thanh / Ghi âm voice note
    VIDEO_MESSAGE,     // File video đính kèm
    
    SYSTEM_NOTIFICATION // Thông báo hệ thống
}
