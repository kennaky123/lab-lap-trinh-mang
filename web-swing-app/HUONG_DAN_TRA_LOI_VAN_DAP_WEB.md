# TÀI LIỆU HƯỚNG DẪN TRẢ LỜI VẤN ĐÁP: WEB SERVER & WEB BROWSER

> **Tên bài:** Web Server (Đa dịch vụ .pdf, .mp3, .mp4) & Web Browser Client (Chrome / Cốc Cốc Style)  
> **Ngôn ngữ:** Java (Swing UI)  
> **Giao thức chuẩn:** HTTP/1.1 (RFC 2616 / RFC 7230-7235)  
> **Cổng mặc định:** **8080** (hoặc 80)

---

## 1. CÂU HỎI VỀ CÁC PHƯƠNG THỨC HTTP (GET, HEAD, POST)

### Câu 1: Phân biệt sự khác nhau giữa ba phương thức GET, HEAD và POST?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> 1. **Phương thức GET (Lấy dữ liệu):**  
>    - Dùng để yêu cầu máy chủ trả về tài nguyên (HTML, PDF, MP3, MP4).  
>    - Tham số được gắn trực tiếp trên URL (Query String: `?key=value`).  
>    - Máy chủ trả về đầy đủ cả dòng trạng thái (Status Line), Header và **Nội dung Body**.
>
> 2. **Phương thức HEAD (Kiểm tra tài nguyên):**  
>    - Giống hệt GET về mặt Header, nhưng **hoàn toàn KHÔNG có nội dung Body** gửi về.  
>    - **Mục đích:** Giúp trình duyệt kiểm tra trước kích thước tệp (`Content-Length`), loại tệp (`Content-Type`), hoặc thời điểm sửa đổi (`Last-Modified`) mà không cần tốn băng thông tải cả tệp lớn về máy.
>
> 3. **Phương thức POST (Gửi dữ liệu lên Server):**  
>    - Dùng để gửi dữ liệu từ Client lên Server để xử lý (như Form góp ý, đăng ký, đăng nhập).  
>    - Dữ liệu được đóng gói trong **Body của HTTP Request** (định dạng `application/x-www-form-urlencoded` hoặc JSON), không bị lộ trên thanh địa chỉ URL và không bị giới hạn độ dài như URL."

---

### Câu 2: Trong code, phương thức HEAD được xử lý như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, trong file `HttpHandler.java`:
> - Em dùng chung hàm xử lý logic `handleGetOrHead(req, out, sendBody)`.
> - Nếu là **GET**, biến cờ `sendBody = true` (ghi cả header và mảng byte dữ liệu file ra OutputStream).
> - Nếu là **HEAD**, biến cờ `sendBody = false` (chỉ ghi các dòng Header và kết thúc bằng `\r\n\r\n`, sau đó ngắt kết nối).
> - Thầy/Cô có thể kiểm tra trực tiếp trên tab **⚡ Công Cụ HTTP** của trình duyệt: khi chọn HEAD, khung Response Headers hiện đầy đủ Content-Length và Content-Type nhưng khung Body hoàn toàn trống."

---

### Câu 3: Dữ liệu gửi bằng POST được phân tích ra sao?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, trong lớp `HttpRequest.java`:
> - Server đọc dòng `Content-Length` trong Header để biết chính xác số ký tự của Body.
> - Sau đó đọc số byte tương ứng từ luồng mạng, giải mã URL Decoder (`URLDecoder.decode(..., UTF-8)`), phân tách theo cặp `key=value` và lưu vào `Map<String, String> formParams`.
> - Dữ liệu này được hiển thị ngay trên bảng **Dữ Liệu Nhận Từ HTTP POST** của Web Server Dashboard."

---

## 2. CÂU HỎI VỀ ĐA DỊCH VỤ CỦA SERVER (.PDF, .MP3, .MP4)

### Câu 4: Làm thế nào Server có thể phục vụ nhiều loại tệp tin khác nhau như .pdf, .mp3, .mp4?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, cơ chế của Web Server dựa vào trường **`Content-Type` (MIME Type)** trong Header:
> - Trong file `HttpConstants.java`, em xây dựng bảng ánh xạ phần mở rộng của file sang chuẩn MIME quốc tế:
>   + Tệp `.html` / `.htm` ➔ `text/html; charset=UTF-8`
>   + Tệp `.pdf` ➔ `application/pdf`
>   + Tệp `.mp3` ➔ `audio/mpeg`
>   + Tệp `.mp4` ➔ `video/mp4`
> - Khi Client yêu cầu một file, Server nhận diện đuôi file, gắn đúng `Content-Type` tương ứng và đặt thêm `Accept-Ranges: bytes` để hỗ trợ tua nhanh video/âm thanh (Streaming Media). Nhờ đó trình duyệt biết chính xác cách render hoặc tải về."

---

## 3. CÂU HỎI VỀ TÍNH NĂNG TRÌNH DUYỆT (CHROME / CỐC CỐC STYLE)

### Câu 5: Tính năng Lịch Sử (History) và Tải xuống (Download Manager) được cài đặt thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> 1. **Lịch sử duyệt web (History):**  
>    - Mỗi khi người dùng bấm truy cập một trang web hoặc submit form, trình duyệt ghi nhận thời gian, tiêu đề trang và URL vào bảng hiển thị và lưu bền vững vào tệp `browser_data/history.txt`. Người dùng có thể nhấp đúp để mở lại bất kỳ lúc nào.
>    - Cơ chế nút **Back (◀)** và **Forward (▶)** sử dụng hai cấu trúc dữ liệu **`Stack<String> backStack`** và **`Stack<String> forwardStack`** chuẩn theo kiến trúc trình duyệt thế giới.
>
> 2. **Quản lý tải xuống (Download Manager):**  
>    - Khi người dùng bấm vào các link tệp tin `.pdf`, `.mp3`, `.mp4`, trình duyệt tự động nhận diện đuôi tệp, mở luồng tải riêng trên một Thread ngầm, tính toán phần trăm hoàn thành bằng `(transferredBytes * 100) / totalBytes`, cập nhật thời gian thực vào bảng và lưu vào thư mục `downloads/`."

