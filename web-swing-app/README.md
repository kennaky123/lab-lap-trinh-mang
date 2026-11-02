# MÁY CHỦ WEB SERVER & TRÌNH DUYỆT WEB BẰNG JAVA SWING (HTTP/1.1)

Ứng dụng hoàn chỉnh gồm **Máy chủ Web Server (đa dịch vụ tài liệu .pdf, âm thanh .mp3, video .mp4, web tĩnh và xử lý form POST)** cùng **Trình duyệt Web Browser (phong cách Chrome / Cốc Cốc)**, được xây dựng bằng **Java Swing thuần túy** phục vụ môn học **Lập trình mạng**.

---

## 🌟 ĐÁP ỨNG TOÀN DIỆN TIÊU CHÍ ĐỀ BÀI

| Tiêu chí đề bài | Đánh giá | Chi tiết giải pháp hiện thực |
| :--- | :---: | :--- |
| **Giao thức HTTP** |  **Đạt** | Tuân thủ chuẩn giao thức **HTTP/1.1** (RFC 7230-7235). Hỗ trợ đầy đủ các mã trạng thái phản hồi: `200 OK`, `206 Partial Content`, `400 Bad Request`, `404 Not Found`, `405 Method Not Allowed`, `500 Server Error`. |
| **Hơn 1 dịch vụ của Server (.pdf, .mp3, .mp4...)** |  **Đạt** | - **Dịch vụ Web Tĩnh:** Trang chủ `index.html` và css.<br>- **Dịch vụ Tài Liệu PDF:** Tệp `TaiLieu_LapTrinhMang.pdf` phục vụ tải hoặc xem tài liệu.<br>- **Dịch vụ Âm Thanh MP3:** Tệp `sample_audio.mp3` hỗ trợ phát và tải.<br>- **Dịch vụ Video MP4:** Tệp `sample_video.mp4` hỗ trợ truyền tải video và streaming.<br>- **Dịch vụ Xử Lý Form POST:** Endpoint `/submit-feedback` tiếp nhận dữ liệu sinh viên. |
| **Browser có GET, HEAD, POST** |  **Đạt** | Tích hợp tab **⚡ Công Cụ HTTP (GET / HEAD / POST Inspector)** cho phép chọn method, nhập URL, gửi dữ liệu Body và xem trực tiếp **Raw HTTP Response Headers** và **Response Body** để kiểm tra và đối chiếu. |
| **Browser có History** |  **Đạt** | Tab **📜 Lịch Sử Duyệt Web** ghi lại thời gian, tiêu đề trang và URL đã ghé thăm. Có nút mở lại trang và nút xóa lịch sử, dữ liệu lưu vĩnh viễn trên đĩa. |
| **Browser có Download** |  **Đạt** | Tab **⬇ Quản Lý Tải Xuống (Downloads)** theo dõi tiến độ tải tệp tin (.pdf, .mp3, .mp4...) thời gian thực với thanh phần trăm và nút mở trực tiếp file tải về. |
| **Giao diện tương tự Chrome / Cốc Cốc** |  **Đạt** | Thiết kế phong cách Chrome: Thanh Omnibox nhập địa chỉ, các nút điều hướng ◀ Back, ▶ Forward (Stack history), 🔄 Reload, 🏠 Home, các tab nội dung mượt mà. |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN

```
web-swing-app/
├── src/web/
│   ├── common/
│   │   ├── HttpConstants.java         # Cổng mặc định 8080, bảng ánh xạ MIME types
│   │   ├── HttpRequest.java           # Phân tích cú pháp HTTP Request (Headers, Form POST)
│   │   └── HttpResponse.java          # Tạo gói tin HTTP Response chuẩn (Status, Headers, Body)
│   ├── server/
│   │   ├── HttpServer.java            # Socket điều khiển TCP, lắng nghe và ghi nhận log
│   │   ├── HttpHandler.java           # Trình xử lý các phương thức GET, HEAD, POST & Partial Content
│   │   └── WebServerGUI.java          # Giao diện quản trị Server Dashboard
│   ├── client/
│   │   ├── HttpClientEngine.java      # Động cơ HTTP Client (thực thi GET, HEAD, POST, Download)
│   │   └── WebBrowserGUI.java         # Giao diện Chrome / Cốc Cốc Swing chuyên nghiệp
│   └── ui/
│       └── ModernScrollBarUI.java     # Thanh cuộn phẳng hiện đại
├── bin/                               # Chứa các file .class đã biên dịch
├── web_root/                          # Thư mục gốc chứa các dịch vụ của Server
│   ├── index.html                     # Trang chủ web hiện đại
│   ├── docs/                          # Dịch vụ tài liệu
│   │   └── TaiLieu_LapTrinhMang.pdf   # Tệp PDF mẫu
│   └── assets/media/                  # Dịch vụ âm thanh & video
│       ├── sample_audio.mp3           # Tệp MP3 mẫu
│       └── sample_video.mp4           # Tệp MP4 mẫu
├── downloads/                         # Nơi lưu các tệp tin tải về từ Browser
├── browser_data/                      # Lưu trữ lịch sử duyệt web
├── compile.bat                        # File biên dịch nhanh bằng javac
├── run-server.bat                     # Chạy riêng Web Server GUI
├── run-client.bat                     # Chạy riêng Chrome Browser Client
├── start-demo.bat                     # Khởi chạy đồng thời Server + Browser
├── README.md                          # Tài liệu hướng dẫn này
└── HUONG_DAN_TRA_LOI_VAN_DAP_WEB.md   # Hướng dẫn trả lời vấn đáp thi
```

---

## 🚀 HƯỚNG DẪN CHẠY DEMO

### Cách 1: Khởi chạy Demo 1-Click (Khuyên dùng khi chấm bài)
Nhấp đúp vào file:
👉 **[`start-demo.bat`](file:///d:/VScode/lap-trinh-mang/web-swing-app/start-demo.bat)**

Hệ thống sẽ:
1. Tự động biên dịch mã nguồn Java.
2. Bật cửa sổ **Web Server Dashboard** (Cổng `http://localhost:8080/`).
3. Bật cửa sổ **Chrome / Cốc Cốc Web Browser** và tự động nạp Trang chủ Web Server!

---

### Thao tác thử nghiệm chấm điểm:
1. **Thử nghiệm Đa Dịch Vụ (PDF, MP3, MP4):**
   - Trên trang chủ trình duyệt, bấm nút **Tải Tệp PDF**, **Tải / Nghe MP3** hoặc **Tải / Xem MP4**.
   - Trình duyệt sẽ hỏi bạn có muốn tải tệp về máy không ➔ Bấm **Yes** ➔ Trình duyệt tự động chuyển sang tab **Downloads**, thanh tiến trình chạy 100% và file được lưu vào thư mục `downloads/`.
   - Trên Server Dashboard, số đếm thống kê dịch vụ tương ứng (`PDF Hits`, `MP3 Hits`, `MP4 Hits`) sẽ tăng lên ngay lập tức!
2. **Thử nghiệm Phương thức HTTP POST:**
   - Kéo xuống mục **Form Feedback** trên trang web ➔ Nhập Họ tên, MSSV ➔ Bấm **Gửi Dữ Liệu Lên Server (HTTP POST)**.
   - Trình duyệt sẽ gửi POST request và hiển thị trang xác nhận thành công.
   - Chuyển sang Server Dashboard, mở tab **Dữ Liệu Nhận Từ HTTP POST** để thấy ngay bản ghi sinh viên vừa gửi.
3. **Thử nghiệm Phương thức HTTP HEAD (Vấn đáp):**
   - Chuyển sang tab **⚡ Công Cụ HTTP (GET / HEAD / POST)**.
   - Chọn dropdown sang **HEAD** ➔ Bấm **Gửi Request HTTP**.
   - Quan sát kết quả: Khung **Response Headers** hiện đầy đủ các trường `Content-Length`, `Content-Type`, `Server`, `Date` nhưng khung **Response Body** hoàn toàn trống (đúng chuẩn giao thức HTTP!).
4. **Kiểm tra Lịch Sử (History) & Điều hướng:**
   - Thử bấm nút ◀ Back và ▶ Forward trên thanh điều hướng để xem chuyển trang theo Stack.
   - Mở tab **📜 Lịch Sử Duyệt Web** để xem toàn bộ danh sách các trang đã truy cập.

