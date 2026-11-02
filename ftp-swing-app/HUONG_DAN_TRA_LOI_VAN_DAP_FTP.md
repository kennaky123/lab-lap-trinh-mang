# TÀI LIỆU HƯỚNG DẪN TRẢ LỜI VẤN ĐÁP: FTP SERVER & FTP CLIENT

> **Tên bài:** FTP Server (có CSDL) & FTP Client phong cách FileZilla  
> **Ngôn ngữ:** Java (Swing UI)  
> **Giao thức chuẩn:** FTP (File Transfer Protocol - RFC 959)  
> **Cổng mặc định:** Control Port **2121** (hoặc 21), Dải Passive Data Port **30000 - 30100**

---

## 1. CÂU HỎI VỀ BẢN CHẤT GIAO THỨC FTP (RFC 959)

### Câu 1: Tại sao FTP lại sử dụng tới hai kết nối TCP khác nhau thay vì một?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, kiến trúc FTP theo chuẩn **RFC 959** tách bạch thành hai kênh truyền độc lập:
>
> 1. **Kênh Điều Khiển (Control Connection):**  
>    - Mở liên tục giữa Client và Server tại cổng **2121** (hoặc 21).  
>    - **Nhiệm vụ:** Dùng để trao đổi lệnh bằng văn bản (như `USER`, `PASS`, `CWD`, `QUIT`) và nhận mã trạng thái (220, 230, 250, 550...). Kênh này không bao giờ dùng để chuyển byte tệp tin lớn.
>
> 2. **Kênh Dữ Liệu (Data Connection):**  
>    - Là kết nối tạm thời, chỉ được mở ra khi có thao tác chuyển dữ liệu (như gửi danh sách tệp `LIST`, tải về `RETR`, tải lên `STOR`).  
>    - Sau khi truyền xong số byte của tệp tin, kênh dữ liệu sẽ tự động đóng lại để báo hiệu kết thúc truyền (End-Of-File), trong khi kênh điều khiển vẫn giữ nguyên để người dùng tiếp tục thao tác."

---

### Câu 2: Sự khác nhau giữa Active Mode (PORT) và Passive Mode (PASV) là gì? Trong bài em dùng chế độ nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> - **Active Mode (Lệnh PORT):** Client tự mở một cổng dữ liệu ngẫu nhiên, sau đó gửi IP và Port của mình về Server qua lệnh `PORT h1,h2,h3,h4,p1,p2`. Server sẽ chủ động kết nối ngược lại Client. Nhược điểm lớn là thường bị Firewall/NAT phía Client chặn.
> - **Passive Mode (Lệnh PASV):** Client gửi lệnh `PASV`. **Server sẽ mở một ServerSocket trên một cổng ngẫu nhiên trong dải (30000 - 30100)** và gửi phản hồi `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`. Client sẽ chủ động kết nối tới cổng đó của Server.
> - **Trong bài:** Cả Server và Client của em đều hỗ trợ **Passive Mode (PASV)** là chuẩn thông dụng nhất hiện nay (giống hệt FileZilla thật), và Server có sẵn mã lệnh xử lý cả `PORT`."

---

### Câu 3: Hãy giải thích ý nghĩa các mã phản hồi (Status Code) trong FTP?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, mã trạng thái FTP gồm 3 chữ số:
> - **1xx (Khởi đầu thành công):** Ví dụ `150 Opening data connection` (bắt đầu truyền dữ liệu).
> - **2xx (Hoàn tất thành công):** Ví dụ `220 Ready` (sẵn sàng), `230 Login OK`, `250 Requested file action completed`, `226 Transfer complete`.
> - **3xx (Cần thêm thông tin):** Ví dụ `331 User name okay, need password` (yêu cầu gửi tiếp lệnh PASS).
> - **4xx (Lỗi tạm thời):** Ví dụ `425 Can't open data connection`, `426 Connection closed; transfer aborted`.
> - **5xx (Lỗi vĩnh viễn / Từ chối):** Ví dụ `530 Not logged in`, `550 File not found hoặc Permission denied`."

---

## 2. CÂU HỎI VỀ CƠ SỞ DỮ LIỆU & BẢO MẬT (CÁCH CHẤM)

### Câu 4: Cơ sở dữ liệu (Database) của Server lưu những gì và tổ chức ra sao?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, lớp `FtpDatabaseManager.java` quản lý 2 bảng CSDL được lưu bền vững (Persistent) vào thư mục `ftp_data/`:
>
> 1. **Bảng Người Dùng & Phân Quyền (Users Database):**  
>    - Lưu Username, Password, Thư mục gốc (Home Directory), Trạng thái kích hoạt.
>    - **Phân quyền 3 cấp:** Quyền đọc `canRead` (RETR), quyền ghi `canWrite` (STOR, MKD), quyền xóa `canDelete` (DELE, RMD).
>    - Có sẵn giao diện quản trị trên Server để Thêm, Sửa, Xóa tài khoản trực tiếp vào CSDL.
>
> 2. **Bảng Nhật Ký Kiểm Toán (Audit Log Database):**  
>    - Ghi lại từng thao tác theo thời gian thực: Thời gian, User, Hành động (LOGIN, UPLOAD, DOWNLOAD, DELETE), Tên file, Dung lượng bytes truyền tải, IP của Client, Kết quả Thành công/Thất bại."

---

### Câu 5: Hệ thống chống lỗ hổng Directory Traversal (người dùng dùng `../../` để xem trộm file hệ thống) như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, trong file `FtpClientHandler.java`:
> - Em cài đặt hàm kiểm tra an toàn `isWithinRoot(File target)`:
>   ```java
>   String rootPath = userRootDir.getCanonicalPath();
>   String filePath = target.getCanonicalPath();
>   return filePath.startsWith(rootPath);
>   ```
> - Bằng cách dùng `getCanonicalPath()` để giải quyết triệt để các ký tự `..` hoặc symlink, nếu người dùng cố tình nhảy ra ngoài thư mục gốc được cấp phép, Server sẽ ngay lập tức chặn và trả về mã lỗi `550 Permission denied`."

---

## 3. CÂU HỎI VỀ GIAO DIỆN FILEZILLA SWING

### Câu 6: Giao diện Client được tổ chức như thế nào để giống FileZilla?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, giao diện `FtpClientGUI.java` được thiết kế theo đúng chuẩn FileZilla:
> 1. **Thanh Quickconnect trên cùng:** Nhập Host, User, Pass, Port và nút Kết Nối Nhanh.
> 2. **Khung Console Log:** Hiển thị trực tiếp các dòng lệnh gửi đi màu xanh và phản hồi của Server.
> 3. **Dual-Pane Explorer (2 khung song song):** Khung trái hiển thị tệp trên máy người dùng (Local Site), khung phải hiển thị tệp trên Server (Remote Site). Hỗ trợ nhấp đúp vào thư mục để duyệt, bấm nút Lên (▲), chuột phải để Upload/Download/Xóa/Tạo thư mục.
> 4. **Hàng đợi truyền file ở dưới:** Hiển thị danh sách file đang truyền tải cùng thanh phần trăm tiến độ `JProgressBar` thời gian thực."

