# Lab 5 — ExchangeRate và Mail Server

Yêu cầu Python 3.10+ có Tkinter (bản Python thông thường trên Windows). Không cần `pip install`. Mở terminal tại:

```powershell
cd D:\VScode\lap-trinh-mang\lab-5
```

## Bài 1 — ExchangeRate

Chạy ở hai terminal riêng:

```powershell
python ExchangeRateServer.py
```

```powershell
python ExchangeRateTable.py
```

Hoặc nhấp đúp **start-exchange-demo.bat**.

Trong bảng tỷ giá, giữ IP `127.0.0.1`, cổng **2345**, bấm **Bắt đầu**. Client gửi yêu cầu đến server mỗi giây; server random giá trị Tokyo, Newyork, Hồng Kông và trả ngày giờ hiện tại có múi giờ. Bấm **Dừng** để ngừng yêu cầu. Khi server tắt, bảng đánh dấu dữ liệu cũ và tự thử lại; khởi động lại server để tiếp tục.

Các cặp tiền minh họa: Tokyo `USD/JPY`, Newyork `EUR/USD`, Hồng Kông `USD/HKD`. Đây là **dữ liệu mô phỏng ngẫu nhiên**, không phải tỷ giá thị trường thực. Mỗi yêu cầu tạo một bộ số mới, nên các client có thể nhận giá trị khác nhau. Nếu mạng phản hồi lâu hơn một giây, client đợi yêu cầu hiện tại hoàn tất trước khi gửi tiếp để tránh chồng yêu cầu.

Luồng xử lý: `ExchangeRateTable → TCP :2345 → ExchangeRateServer → JSON {timestamp, rates} → bảng giao diện`.

## Bài 2 — Mail Server

Chạy máy chủ:

```powershell
python MailServer.py
```

Mỗi client chạy ở terminal riêng:

```powershell
python MailClient.py
```

Hoặc nhấp đúp **start-mail-demo.bat** để mở server và hai client. Cổng mặc định của bài Mail là **2525**, chạy đồng thời được với bài tỷ giá.

### Kịch bản demo

1. Client A: IP `127.0.0.1`, cổng `2525`, account `alice`, mật khẩu `123456`; bấm **Tạo account**, sau đó **Đăng nhập**.
2. Client B: tạo account `bob`, mật khẩu `123456`, rồi đăng nhập.
3. Mỗi client thấy file `new_email.txt`. Chọn file, bấm **Đọc thư đã chọn** hoặc nhấp đúp để xem lời chào.
4. Alice nhập account nhận `bob`, tiêu đề và nội dung, bấm **Gửi email**.
5. Bob bấm **Làm mới hộp thư**, chọn file mới để đọc. Nếu Bob đăng xuất rồi đăng nhập lại, server cũng tự gửi toàn bộ tên file trong phản hồi đăng nhập.
6. Có thể gửi thư khi Bob chưa đăng nhập, miễn account đã được tạo.
7. Tắt rồi chạy lại Mail Server: account và email vẫn còn. Client đăng xuất và đăng nhập lại để lấy phiên mới.

Account gồm 3–30 chữ cái ASCII, số hoặc `_`, không phân biệt hoa thường. Mật khẩu dài 4–128 ký tự. Người nhận là tên account nội bộ (ví dụ `bob`), không phải địa chỉ Gmail. Tiêu đề tối đa 200 ký tự; nội dung tối đa 256 KiB UTF-8, không được rỗng.

### File trên máy chủ

Dữ liệu mặc định luôn nằm cạnh `MailServer.py`, không phụ thuộc thư mục đang mở terminal:

```text
mail-data/
├── accounts.json                 # Salt và hash mật khẩu
└── mailboxes/
    ├── alice/
    │   └── new_email.txt
    └── bob/
        ├── new_email.txt
        └── 20260920_...txt        # Email nhận được
```

Khi tạo account, server tạo thư mục tương ứng và `new_email.txt` có nội dung:

```text
Thank you for using this service. we hope that you will feel comfortable using our mail service.
```

Mỗi email là một file UTF-8 chứa người gửi, người nhận, tiêu đề, ngày giờ và phần nội dung. Tên file có timestamp và ID ngẫu nhiên để tránh ghi đè. Việc gửi thư được xác nhận sau khi đã lưu trên server. Danh sách hộp thư chứa toàn bộ tên file `.txt`, gồm thư chào mừng. Email được lưu bằng file tạm rồi đổi tên; client không đọc nhầm file đang ghi.

### Giao thức Mail

| Lệnh | Yêu cầu | Phản hồi chính |
| --- | --- | --- |
| `register` | username, password | Tạo account và thư chào mừng |
| `login` | username, password | token, username, tất cả tên file |
| `list` | token | Tất cả tên file trong account |
| `read` | token, filename | Nội dung thư thuộc account |
| `send` | token, to, subject, body | Tên file đã lưu ở account nhận |
| `logout` | token | Hủy phiên |

Token phiên tồn tại trong RAM tối đa 12 giờ; khởi động lại server sẽ hủy các phiên. Mật khẩu lưu bằng PBKDF2-HMAC-SHA256 với salt riêng. Bản thực hành dùng TCP JSON riêng, chưa triển khai SMTP/POP3/IMAP hoặc TLS; chỉ sử dụng trong localhost/LAN tin cậy. Không gửi ra dịch vụ email Internet. Chỉ chạy một Mail Server trên mỗi thư mục dữ liệu.

## Chạy trên hai máy LAN

Chạy server trên máy A, dùng `ipconfig` lấy IPv4 LAN của máy A và nhập IP đó ở client máy B. Cho phép cổng TCP **2345** (tỷ giá) hoặc **2525** (mail) qua Windows Firewall trên mạng Private. Hai máy cần truy cập được nhau.

Có thể đổi địa chỉ bind, cổng hoặc thư mục lưu mail:

```powershell
python ExchangeRateServer.py --host 0.0.0.0 --port 2345
python MailServer.py --host 0.0.0.0 --port 2525 --data-dir .\mail-data
```

## Cấu trúc và kiểm thử

`network.py` đóng khung JSON bằng 4 byte độ dài big-endian + JSON UTF-8, đọc đủ byte qua TCP và giới hạn thông điệp 2 MiB. Hai server xử lý các kết nối bằng luồng; thao tác lưu mail dùng khóa để tránh xung đột. Hai giao diện gọi mạng ở luồng nền, đưa kết quả qua queue để cập nhật Tkinter.

```powershell
python -m unittest -v test_lab5
```

Kiểm thử qua TCP thật trên localhost: ba thị trường và timestamp, đăng ký và thư chào mừng, danh sách khi đăng nhập, gửi thư cho người nhận offline, đọc Unicode, nạp lại dữ liệu từ đĩa, từ chối thông tin sai/đường dẫn ngoài hộp thư, đăng xuất, gửi đồng thời và thông điệp quá lớn.
