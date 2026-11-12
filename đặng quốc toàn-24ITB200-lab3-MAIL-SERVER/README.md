# HỆ THỐNG MAIL SERVER & GMAIL CLIENT BẰNG JAVA SWING (SMTP & POP3)

Ứng dụng hoàn chỉnh bao gồm **Máy chủ Mail Server (tích hợp SMTP 2525 và POP3 1110 kèm Cơ Sở Dữ Liệu)** và **Ứng dụng Gmail Client có giao diện phong cách Gmail**, xây dựng bằng **Java Swing thuần túy** phục vụ môn học **Lập trình mạng**.

---

## 🌟 ĐÁP ỨNG TOÀN DIỆN TIÊU CHÍ ĐỀ BÀI

| Tiêu chí đề bài | Đánh giá | Chi tiết giải pháp hiện thực |
| :--- | :---: | :--- |
| **Giao thức SMTP & POP3** |  **Đạt** | - **SMTP (RFC 5321):** Lắng nghe tại cổng 2525, xử lý `HELO/EHLO`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`.<br>- **POP3 (RFC 1939):** Lắng nghe tại cổng 1110, xử lý `USER`, `PASS`, `STAT`, `LIST`, `RETR`, `DELE`, `QUIT`. |
| **Gửi được trên Gmail và Localhost** |  **Đạt** | - **Trên Localhost:** Gửi nhận thư nội bộ siêu tốc qua Server cục bộ.<br>- **Trên Gmail thật:** Hỗ trợ kết nối bảo mật `SSLSocket` tới `smtp.gmail.com:465` (gửi thư) và `pop.gmail.com:995` (nhận thư) bằng tài khoản Gmail thật (Google App Password). |
| **Server có Database (Cách chấm)** |  **Đạt** | Lớp `MailDatabaseManager` lưu trữ vĩnh viễn trên đĩa trong thư mục `mail_data/` gồm: CSDL Tài khoản hộp thư, CSDL Thư điện tử, và CSDL Lịch sử giao dịch SMTP/POP3. |
| **Giao diện tương tự Gmail** |  **Đạt** | - Nút **✏ Soạn Thư (Compose)** nổi bật phong cách Gmail.<br>- Danh mục: Hộp thư đến (Inbox), Thư đã gửi (Sent), Cấu hình tài khoản.<br>- Danh sách thư dạng bảng trực quan (Người gửi, Tiêu đề, Ngày nhận).<br>- Khung đọc thư chi tiết (Reading Pane) với hỗ trợ tệp đính kèm. |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN

```
mail-swing-app/
├── src/mail/
│   ├── common/
│   │   ├── MailConfig.java            # Cấu hình cổng SMTP 2525, POP3 1110, Gmail host
│   │   ├── EmailMessage.java          # Model thư điện tử và chuẩn xuất RFC 822 / MIME
│   │   └── MailAccount.java           # Model tài khoản người dùng hộp thư
│   ├── server/
│   │   ├── MailServer.java            # Máy chủ điều phối 2 cổng dịch vụ SMTP & POP3
│   │   ├── SmtpServerHandler.java     # Trình xử lý giao thức gửi thư SMTP (RFC 5321)
│   │   ├── Pop3ServerHandler.java     # Trình xử lý giao thức nhận thư POP3 (RFC 1939)
│   │   ├── MailDatabaseManager.java   # CSDL tài khoản, thư điện tử & nhật ký giao dịch
│   │   └── MailServerGUI.java         # Giao diện quản trị Server Dashboard
│   ├── client/
│   │   ├── SmtpClient.java            # Động cơ gửi thư SMTP (hỗ trợ cả Localhost & Gmail)
│   │   ├── Pop3Client.java            # Động cơ nhận thư POP3 (hỗ trợ cả Localhost & Gmail)
│   │   └── MailClientGUI.java         # Giao diện Gmail Client chuyên nghiệp
│   └── ui/
│       └── ModernScrollBarUI.java     # Thanh cuộn phẳng hiện đại
├── bin/                               # Chứa các file .class đã biên dịch
├── mail_data/                         # Thư mục lưu trữ CSDL tài khoản và thư điện tử
├── attachments/                       # Thư mục lưu tệp đính kèm khi tải về
├── compile.bat                        # Biên dịch dự án bằng javac
├── run-server.bat                     # Chạy riêng Mail Server GUI
├── run-client.bat                     # Chạy riêng Gmail Client GUI
├── start-demo.bat                     # Khởi chạy đồng thời Server + Client
├── README.md                          # Tài liệu hướng dẫn này
└── HUONG_DAN_TRA_LOI_VAN_DAP_MAIL.md  # Bộ câu hỏi & trả lời vấn đáp thi
```

---

## 🚀 HƯỚNG DẪN CHẠY DEMO

### Cách 1: Khởi chạy Demo 1-Click (Khuyên dùng khi chấm bài)
Nhấp đúp vào file:
👉 **[`start-demo.bat`](file:///d:/VScode/lap-trinh-mang/mail-swing-app/start-demo.bat)**

Hệ thống sẽ:
1. Tự động biên dịch mã nguồn Java.
2. Bật cửa sổ **Mail Server Dashboard** (SMTP 2525 & POP3 1110).
3. Bật cửa sổ **Gmail Client** với tài khoản mặc định `alice@local.com`.
4. Hộp thư đến (Inbox) sẽ tự động nạp thư chào mừng có sẵn trong CSDL.

---

### Danh sách tài khoản mẫu có sẵn trong CSDL:
| Tài khoản Email | Mật khẩu | Tên hiển thị |
| :--- | :--- | :--- |
| **`alice@local.com`** | `123456` | Alice Nguyễn |
| **`bob@local.com`** | `123456` | Bob Trần |
| **`admin@local.com`** | `123456` | Administrator |

---

### Thao tác thử nghiệm chấm điểm:
1. **Gửi thư nội bộ trên Localhost:**
   - Trên Client, bấm nút **✏ Soạn Thư Mới**.
   - Nhập người nhận: `bob@local.com`, nhập tiêu đề và nội dung (có thể đính kèm file).
   - Bấm **Gửi Thư (Send)** ➔ Lệnh SMTP sẽ chạy tức thì, thư được ghi nhận vào CSDL Server.
2. **Kiểm tra nhận thư:**
   - Ở thanh trên cùng của Client, chọn chế độ sang `bob@local.com` (hoặc mở thêm 1 cửa sổ Client bằng file `run-client.bat`).
   - Bấm **🔄 Làm Mới / Nhận Thư** ➔ Bức thư Alice vừa gửi sẽ xuất hiện ngay trong Hộp thư đến của Bob qua giao thức POP3.
3. **Gửi thư qua Google Gmail thật:**
   - Chọn chế độ `Google Gmail Thật` trên thanh chọn.
   - Nhập địa chỉ Gmail và **Mật khẩu ứng dụng (Google App Password 16 ký tự)**.
   - Bạn có thể gửi thư thực tế đến bất kỳ địa chỉ email nào trên thế giới!
4. **Kiểm tra CSDL trên Server:**
   - Mở cửa sổ Mail Server Dashboard ➔ Xem tab **CSDL Tài Khoản Email**, **CSDL Thư Điện Tử** và **CSDL Lịch Sử Giao Dịch** để giáo viên kiểm tra dữ liệu lưu trên đĩa.
5. **Kết nối bằng Mozilla Thunderbird:**
   - Mở tab **Kết Nối Thunderbird** trên Mail Server Dashboard để xem thông số cổng/IP LAN và bấm nút **Kiểm Tra Chuẩn Thunderbird** để kiểm thử trực tiếp.
   - Trên Thunderbird chọn **Manual config (Cấu hình thủ công)**:
     - **Incoming (POP3):** Host `localhost` (hoặc IP LAN), Port `1110`, Security: `None`, Auth: `Normal password`, Username: `alice@local.com`.
     - **Outgoing (SMTP):** Host `localhost` (hoặc IP LAN), Port `2525`, Security: `None`, Auth: `Normal password`, Username: `alice@local.com`.
     - Bấm **Done** ➔ Tích **I understand the risks** ➔ **Confirm** để gửi/nhận thư trực tiếp giữa Thunderbird và Mail Server / Gmail Client.


