# FTP SERVER & FTP CLIENT BẰNG JAVA SWING (CHUẨN RFC 959)

Ứng dụng hoàn chỉnh gồm **Máy chủ FTP Server (có Cơ Sở Dữ Liệu quản lý người dùng và nhật ký)** cùng **Ứng dụng FTP Client có giao diện 2 khung tương tự FileZilla**, được xây dựng bằng **Java Swing thuần túy** phục vụ môn học **Lập trình mạng**.

---

## 🌟 ĐÁP ỨNG TOÀN DIỆN YÊU CẦU ĐỀ BÀI

| Tiêu chí đề bài | Đánh giá | Chi tiết giải pháp hiện thực |
| :--- | :---: | :--- |
| **Giao thức FTP (RFC 959)** |  **Đạt** | Hỗ trợ đầy đủ tập lệnh chuẩn: `USER`, `PASS`, `PWD`, `CWD`, `CDUP`, `TYPE`, `PASV`, `EPSV`, `PORT`, `LIST`, `RETR` (Download), `STOR` (Upload), `DELE`, `MKD`, `RMD`, `SIZE`, `QUIT`. Tương thích 100% với cả phần mềm FileZilla thật! |
| **Server có Database** |  **Đạt** | Lớp `FtpDatabaseManager` quản lý: <br>1. **Users Database:** Lưu trữ tài khoản, mật khẩu, thư mục gốc và phân quyền 3 cấp (Read / Write / Delete).<br>2. **Audit Log Database:** Ghi nhận lịch sử từng lượt tải lên, tải về, IP client, dung lượng byte và thời gian. |
| **Giao diện tương tự FileZilla** |  **Đạt** | - Thanh Quickconnect kết nối nhanh ở trên cùng.<br>- Khung Console Log phản hồi mã trạng thái FTP (220, 230, 150, 226...).<br>- Khung kép (Dual-Pane Explorer): Khung trái hiển thị Local Site, khung phải hiển thị Remote Site.<br>- Hàng đợi truyền tệp và thanh tiến trình `JProgressBar` thời gian thực. |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN

```
ftp-swing-app/
├── src/ftp/
│   ├── common/
│   │   ├── FtpConfig.java             # Cấu hình cổng 2121, dải cổng PASV 30000-30100
│   │   ├── FtpFileItem.java           # Model tệp tin/thư mục và định dạng Unix Listing
│   │   └── FtpUser.java               # Model tài khoản người dùng và phân quyền R/W/D
│   ├── server/
│   │   ├── FtpServer.java             # Quản lý Socket điều khiển TCP và luồng kết nối
│   │   ├── FtpClientHandler.java      # Trình biên dịch lệnh FTP chuẩn RFC 959
│   │   ├── FtpDatabaseManager.java    # CSDL người dùng và nhật ký kiểm toán (Audit Log)
│   │   └── FtpServerGUI.java          # Giao diện quản trị Server Dashboard
│   ├── client/
│   │   ├── FtpClient.java             # Động cơ giao thức FTP Client (PASV mode)
│   │   └── FtpClientGUI.java          # Giao diện FileZilla Swing chuyên nghiệp
│   └── ui/
│       └── ModernScrollBarUI.java     # Thanh cuộn phẳng hiện đại
├── bin/                               # Chứa các file .class đã biên dịch
├── server_root/                       # Thư mục gốc lưu trữ dữ liệu của Server
│   ├── documents/                     # Thư mục mẫu chứa file README_SERVER.txt
│   └── images/
├── client_local/                      # Thư mục trên máy cục bộ của Client
│   └── my_files/                      # Thư mục mẫu chứa TaiLieu_Client.txt
├── ftp_data/                          # Thư mục lưu trữ CSDL người dùng & nhật ký
├── compile.bat                        # Biên dịch dự án bằng javac
├── run-server.bat                     # Chạy riêng FTP Server GUI
├── run-client.bat                     # Chạy riêng FTP Client GUI (FileZilla)
├── start-demo.bat                     # Khởi chạy đồng thời Server + Client
├── README.md                          # Tài liệu tổng quan
└── HUONG_DAN_TRA_LOI_VAN_DAP_FTP.md   # Hướng dẫn trả lời vấn đáp thi
```

---

## 🚀 HƯỚNG DẪN CHẠY DEMO

### Cách 1: Khởi chạy Demo 1-Click (Khuyên dùng khi chấm bài)
Nhấp đúp vào file:
👉 **[`start-demo.bat`](file:///d:/VScode/lap-trinh-mang/ftp-swing-app/start-demo.bat)**

Hệ thống sẽ:
1. Tự động biên dịch mã nguồn Java.
2. Bật cửa sổ **FTP Server Dashboard** (Cổng 2121).
3. Bật cửa sổ **FTP Client (FileZilla Style)** với thông tin tài khoản mẫu điền sẵn.
4. Bạn chỉ cần nhấn **[Kết Nối Nhanh]** trên Client để kết nối ngay lập tức!

---

### Danh sách tài khoản thử nghiệm có sẵn trong CSDL:
| Tài khoản (Username) | Mật khẩu (Password) | Quyền hạn | Thư mục gốc |
| :--- | :--- | :--- | :--- |
| **`admin`** | `123456` | Toàn quyền (Read + Write + Delete) | Toàn bộ `server_root` |
| **`student`** | `123456` | Đọc và Ghi (Read + Write, không được xóa) | Toàn bộ `server_root` |
| **`anonymous`** | *(để trống)* | Chỉ đọc (Read-only) | Thư mục mẫu |

---

### Các thao tác thử nghiệm trên giao diện:
1. **Duyệt thư mục:** Nhấp đúp vào thư mục trên khung Local hoặc Remote để vào bên trong; bấm nút **[▲ Lên]** để trở về thư mục cha.
2. **Tải lên (Upload):** Chọn file `TaiLieu_Client.txt` ở khung bên trái (Local) -> Bấm nút **⬆ Tải Lên (Upload)** hoặc chuột phải -> File sẽ xuất hiện ngay trên Remote và Server ghi nhận vào CSDL.
3. **Tải về (Download):** Chọn file `README_SERVER.txt` ở khung bên phải (Remote) -> Bấm nút **⬇ Tải Về (Download)** -> File sẽ được lưu về máy cục bộ.
4. **Tạo/Xóa thư mục:** Chuột phải trên khung Remote -> Chọn **+ Tạo Thư Mục Trên Server (MKD)** hoặc **Xóa**.
5. **Kiểm tra CSDL trên Server:** Chuyển sang cửa sổ Server -> Mở tab **CSDL Người Dùng & Phân Quyền** hoặc **CSDL Lịch Sử Truyền File (Audit Log)** để xem các bản ghi đã được lưu bền vững.

