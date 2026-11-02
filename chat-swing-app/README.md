# ỨNG DỤNG CHAT CLIENT-SERVER / P2P BẰNG JAVA SWING (LẬP TRÌNH MẠNG)

Ứng dụng nhắn tin đa phương tiện hoàn chỉnh mô phỏng các ứng dụng hiện đại (Zalo, Messenger, WhatsApp) được xây dựng theo chuẩn môn học **Lập trình mạng** với giao thức **TCP và UDP**.

---

## 🌟 TÍNH NĂNG ĐÁP ỨNG ĐẦY ĐỦ YÊU CẦU ĐỀ BÀI

| Yêu cầu đề bài | Trạng thái | Mô tả chi tiết |
| :--- | :---: | :--- |
| **Giao diện thân thiện** |  Đạt | Giao diện Java Swing hiện đại phong cách Zalo/Messenger, bong bóng tin nhắn (Chat Bubble) bo góc, phân biệt màu sắc, danh sách phòng và người dùng trực tuyến. |
| **Multicast & Broadcast** |  Đạt | - **Broadcast:** Gửi tin nhắn và file đến toàn bộ client đang online.<br>- **Multicast TCP:** Quản lý phòng chat nhóm (`#General`, `#Học-Tập`, `#Công-Nghệ`...).<br>- **Multicast UDP Socket:** Socket `230.0.0.1:4446` phục vụ câu hỏi vấn đáp socket mạng. |
| **Icon biểu cảm (Emoji)** |  Đạt | Bảng chọn Emoji Picker popup với hơn 32 biểu cảm phổ biến (😊, 😂, ❤️, 👍, 🔥, 🎉, 🚀...), bấm chèn trực tiếp vào tin nhắn. |
| **Text - Audio - Video** |  Đạt | - **Text:** Nhắn tin tốc độ cao.<br>- **Audio:** Tích hợp bộ ghi âm micro trực tiếp (`AudioRecorder`), đóng gói WAV chuẩn và phát âm thanh ngay trên app (`AudioPlayer`).<br>- **Video:** Gửi tệp tin video, xem trước và mở video bằng trình phát mặc định của máy tính. |
| **Đính kèm (File Transfer)** |  Đạt | Đính kèm file bất kỳ, truyền tải qua luồng byte stream TCP, tự động lưu vào thư mục `downloads/` và có nút mở nhanh. |
| **Cơ sở dữ liệu (Database)** |  Đạt | Lớp `DatabaseManager` lưu trữ vĩnh viễn danh sách tài khoản, các phòng chat và toàn bộ lịch sử tin nhắn vào thư mục `chat_data/`. |
| **Chạy từ 5 đến 6 Client** |  Đạt | Có file batch `start-demo-6-clients.bat` tự động bật 1 Server và 6 Client (Alice, Bob, Charlie, David, Emma, Frank) đồng thời chỉ với 1 click! |
| **Hỏi code tình huống TCP - UDP**|  Đạt | Có kèm tài liệu `HUONG_DAN_TRA_LOI_VAN_DAP.md` soạn sẵn mọi câu trả lời mẫu cho giảng viên. |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN

```
chat-swing-app/
├── src/chatapp/
│   ├── common/             # Các lớp dùng chung
│   │   ├── Message.java          # Đối tượng gói tin truyền qua mạng
│   │   ├── MessageType.java      # Enum định nghĩa các loại gói tin
│   │   ├── User.java             # Quản lý thông tin người dùng
│   │   └── Config.java           # Cấu hình IP, Cổng (Port) TCP/UDP
│   ├── server/             # Mã nguồn Máy chủ (Server)
│   │   ├── ChatServer.java       # Bộ điều phối kết nối TCP & UDP
│   │   ├── ClientHandler.java    # Luồng xử lý từng client kết nối
│   │   ├── ServerGUI.java        # Giao diện quản trị Server trực quan
│   │   └── DatabaseManager.java  # Quản lý lưu trữ CSDL lịch sử tin nhắn
│   ├── client/             # Mã nguồn Máy khách (Client)
│   │   ├── ChatClient.java       # Quản lý Socket TCP kết nối server
│   │   ├── ClientGUI.java        # Giao diện người dùng Zalo/Messenger
│   │   ├── AudioRecorder.java    # Ghi âm giọng nói (Java Sound API)
│   │   ├── AudioPlayer.java      # Phát âm thanh WAV
│   │   └── UdpMulticastService.java # Socket UDP Multicast (230.0.0.1)
│   └── ui/                 # Các thành phần giao diện tùy chỉnh
│       ├── ChatBubblePanel.java  # Bong bóng chat tin nhắn
│       ├── EmojiPickerPopup.java # Bảng icon biểu cảm Emoji
│       └── ModernScrollBarUI.java# Thanh cuộn mượt mà
├── bin/                    # Thư mục chứa các tệp .class đã biên dịch
├── downloads/              # Nơi tự động lưu các file/audio/video nhận được
├── chat_data/              # Nơi lưu trữ cơ sở dữ liệu lịch sử chat
├── compile.bat             # File biên dịch nhanh bằng javac
├── run-server.bat          # File khởi động Server
├── run-client.bat          # File mở 1 Client (có hộp thoại đăng nhập)
├── start-demo-6-clients.bat# File chạy 1 Server + 6 Client cùng lúc để chấm bài
├── HUONG_DAN_TRA_LOI_VAN_DAP.md # Câu hỏi & Trả lời vấn đáp với giảng viên
└── README.md               # Hướng dẫn này
```

---

## 🚀 HƯỚNG DẪN CHẠY VÀ DEMO NHANH

### Cách 1: Chạy Demo 6 Client cùng lúc (Khuyên dùng khi chấm bài)
1. Nhấp đúp vào file **`start-demo-6-clients.bat`**.
2. Hệ thống sẽ tự động biên dịch và mở:
   - 01 Cửa sổ **Máy chủ Server Dashboard**.
   - 06 Cửa sổ **Client** với các tên: `Alice`, `Bob`, `Charlie`, `David`, `Emma`, `Frank`.
3. Bạn có thể kéo các cửa sổ sang hai bên màn hình để gửi tin nhắn Broadcast, tham gia các phòng Multicast, gửi ảnh/file/ghi âm và kiểm tra kết quả tức thì.

---

### Cách 2: Chạy thủ công từng thành phần
1. **Biên dịch:** Nhấp đúp vào file `compile.bat` hoặc gõ lệnh:
   ```powershell
   javac -encoding UTF-8 -d bin src/chatapp/common/*.java src/chatapp/server/*.java src/chatapp/client/*.java src/chatapp/ui/*.java
   ```
2. **Khởi động Server:** Nhấp đúp vào file `run-server.bat` hoặc gõ:
   ```powershell
   java -cp bin chatapp.server.ServerGUI
   ```
3. **Mở Client:** Nhấp đúp vào file `run-client.bat` (sẽ hiện form đăng nhập) hoặc gõ:
   ```powershell
   java -cp bin chatapp.client.ClientGUI [TênNgườiDùng] [ServerIP] [Port]
   ```

