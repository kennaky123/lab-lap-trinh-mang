# TÀI LIỆU HƯỚNG DẪN TRẢ LỜI VẤN ĐÁP: MAIL SERVER & GMAIL CLIENT

> **Tên bài:** Mail Server & Mail Client (Giao thức SMTP & POP3)  
> **Ngôn ngữ:** Java (Swing UI phong cách Gmail)  
> **Giao thức chuẩn:** SMTP (RFC 5321) & POP3 (RFC 1939)  
> **Cổng Localhost:** SMTP **2525**, POP3 **1110**  
> **Kết nối Gmail thật:** SMTP `smtp.gmail.com:465` (SSL), POP3 `pop.gmail.com:995` (SSL)

---

## 1. CÂU HỎI VỀ BẢN CHẤT GIAO THỨC (SMTP vs POP3)

### Câu 1: Phân biệt vai trò của giao thức SMTP và POP3 trong hệ thống thư điện tử?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
>
> 1. **Giao thức SMTP (Simple Mail Transfer Protocol - RFC 5321):**  
>    - Là giao thức **ĐẨY (Push Protocol)** chạy trên nền TCP.  
>    - **Mục đích:** Dùng để **Gửi thư** từ Mail Client lên Mail Server, hoặc chuyển tiếp thư giữa các Mail Server trên Internet với nhau.
>    - Trong bài, Server lắng nghe tại cổng **2525** và Gmail dùng cổng **465/587**.
>
> 2. **Giao thức POP3 (Post Office Protocol v3 - RFC 1939):**  
>    - Là giao thức **KÉO (Pull Protocol)** chạy trên nền TCP.  
>    - **Mục đích:** Dùng để **Nhận/Tải thư** từ hộp thư trên Mail Server về máy cá nhân của Client.
>    - Trong bài, Server lắng nghe tại cổng **1110** và Gmail dùng cổng **995**."

---

### Câu 2: Trình tự các lệnh trao đổi trong một phiên gửi thư SMTP diễn ra như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, một phiên SMTP chuẩn diễn ra qua 5 bước:
> 1. **Kết nối & Chào hỏi:** Client mở Socket TCP tới Server, nhận mã `220 Ready`. Client gửi `HELO localhost` hoặc `EHLO`, Server đáp `250 OK`.
> 2. **Chỉ định người gửi:** Client gửi `MAIL FROM:<nguoigui@local.com>`, Server kiểm tra và đáp `250 Sender OK`.
> 3. **Chỉ định người nhận:** Client gửi `RCPT TO:<nguoinhan@local.com>`, Server đáp `250 Recipient OK`.
> 4. **Truyền nội dung thư (DATA):**  
>    - Client gửi lệnh `DATA`, Server đáp `354 Start mail input; end with <CRLF>.<CRLF>`.  
>    - Client đẩy toàn bộ Header (Subject, Date, To, From) và phần Body (kèm tệp Base64 nếu có đính kèm).  
>    - Kết thúc bằng một dòng chỉ chứa một dấu chấm duy nhất: `.\r\n`. Server lưu thư vào CSDL và đáp `250 Message queued`.
> 5. **Đóng phiên:** Client gửi `QUIT`, Server đáp `221 Bye` và đóng kết nối."

---

### Câu 3: Trình tự các lệnh kiểm tra và tải thư trong POP3 diễn ra như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> 1. Server chào mừng `+OK POP3 Server Ready`.
> 2. **Xác thực:** Client gửi `USER <email>`, Server đáp `+OK`. Client gửi `PASS <matkhau>`, Server kiểm tra CSDL và đáp `+OK Maildrop ready`.
> 3. **Kiểm tra trạng thái (STAT):** Client gửi `STAT`, Server trả về số lượng thư và tổng dung lượng (`+OK 2 3450`).
> 4. **Tải thư (RETR):** Client gửi `RETR 1` (tải thư thứ 1), Server gửi toàn bộ nội dung MIME RFC 822 và kết thúc bằng dòng `.\r\n`.
> 5. **Thoát (QUIT):** Client gửi `QUIT`, Server cập nhật trạng thái xóa (nếu có dùng `DELE`) và kết thúc phiên."

---

## 2. CÂU HỎI VỀ GỬI TRÊN GMAIL THẬT VÀ TRÊN LOCALHOST (CÁCH CHẤM)

### Câu 4: Làm thế nào chương trình gửi được cả trên Localhost và trên Gmail thật?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, ứng dụng của em hỗ trợ linh hoạt 2 chế độ:
>
> 1. **Chế độ Localhost Mail Server:**  
>    - Client mở kết nối `Socket` TCP thông thường tới `localhost:2525` (SMTP) và `localhost:1110` (POP3).  
>    - Dữ liệu được máy chủ nội bộ tiếp nhận, xử lý tức thì và ghi vào CSDL Server.
>
> 2. **Chế độ Google Gmail thật:**  
>    - Sử dụng `javax.net.ssl.SSLSocket` kết nối bảo mật mã hóa SSL tới `smtp.gmail.com` cổng **465**.  
>    - Xác thực bằng lệnh `AUTH LOGIN` với chuỗi Username và **Mật khẩu ứng dụng (Google App Password 16 ký tự)** được mã hóa Base64 theo đúng chuẩn bảo mật hiện nay của Google."

---

### Câu 5: Cơ sở dữ liệu (Database) trên Server lưu trữ những gì?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, lớp `MailDatabaseManager.java` quản lý 3 bảng dữ liệu lưu bền vững trong thư mục `mail_data/`:
> 1. **Bảng Tài khoản hộp thư (Accounts DB):** Quản lý email, tên hiển thị, mật khẩu và trạng thái.
> 2. **Bảng Thư điện tử (Emails DB):** Lưu trữ toàn bộ thư được gửi qua SMTP (người gửi, người nhận, tiêu đề, ngày giờ, nội dung, tệp đính kèm). Nhờ đó khi Client đăng nhập qua POP3 ở bất kỳ thời điểm nào, thư đều được nạp đầy đủ.
> 3. **Bảng Nhật ký giao dịch (Audit Logs DB):** Ghi lại lịch sử từng lệnh SMTP/POP3, IP client và kết quả để quản trị viên giám sát trên Server Dashboard."

