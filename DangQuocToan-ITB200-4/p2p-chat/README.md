# LAN Link — Chat và truyền file P2P

Bài thực hành lập trình mạng bằng **Python 3.10+**, giao diện **Tkinter**, chỉ sử dụng thư viện chuẩn. Chọn mô hình **Client–Server kết hợp P2P**: máy chủ giữ danh bạ, các peer truyền tin nhắn và file trực tiếp qua TCP. Không cần Internet để demo trong LAN.

## Chạy nhanh trong VS Code

Mở thư mục `D:\VScode\lap-trinh-mang\lab-4\p2p-chat` bằng **File → Open Folder**.

Terminal 1 — chạy máy chủ:

```powershell
cd D:\VScode\lap-trinh-mang\lab-4\p2p-chat
python server.py
```

Terminal 2 và 3 — mỗi terminal chạy một client:

```powershell
cd D:\VScode\lap-trinh-mang\lab-4\p2p-chat
python client.py
```

Hoặc nhấp đúp `start-demo.bat` để mở máy chủ và hai cửa sổ client. Nếu máy chủ chưa khởi động xong, bấm Kết nối lại.

1. Cửa sổ thứ nhất: tên **Alice**, IP máy chủ `127.0.0.1`, cổng `9000`, cổng P2P `0`; bấm **Kết nối**.
2. Cửa sổ thứ hai: tên **Bob**, cùng thông số máy chủ; bấm **Kết nối**.
3. Sau khoảng 2 giây, Alice chọn Bob trong danh sách và gửi tin nhắn; Bob chọn Alice để trả lời.
4. Bấm **Gửi file…**. Bên nhận chọn đồng ý và nơi lưu trong 90 giây. Nhật ký thông báo hoàn tất sau khi kiểm tra SHA-256.
5. Bấm **Ngắt kết nối** để đăng xuất. Tên sẽ được xóa khỏi danh sách của các máy khác.

Nếu Windows không nhận lệnh `python`, dùng `py` thay thế. Bản Python cài đặt cần có thành phần Tcl/Tk (Tkinter).

## Chạy trên hai máy trong LAN

- Chạy `server.py` trên máy A. Dùng `ipconfig` để tìm IPv4 LAN, ví dụ `192.168.1.10`.
- Sao chép thư mục dự án sang máy B. Mở `client.py` trên cả hai máy và nhập IP máy chủ `192.168.1.10`.
- Có thể đặt cổng P2P cố định, ví dụ `9001` trên A và `9002` trên B. Các client cùng máy phải dùng cổng khác nhau, hoặc dùng `0` để tự chọn.
- Cho phép Python qua Windows Firewall trên mạng Private, hoặc mở TCP `9000` ở máy chủ và cổng P2P đã chọn ở mỗi máy nhận.
- Hai máy phải truy cập trực tiếp được nhau; Wi-Fi có chế độ cô lập thiết bị có thể chặn kết nối.

## Kiến trúc và giao thức

```text
                  Directory Server :9000
                  /                   \
        đăng ký, danh bạ          đăng ký, danh bạ
               /                       \
          Peer Alice <==== TCP P2P ====> Peer Bob
                       chat / file
```

Máy chủ chỉ giữ `id`, tên, IP lấy từ kết nối TCP và cổng lắng nghe của peer trong RAM. Tên online không trùng nhau. Client duy trì kết nối danh bạ và lấy danh sách mỗi 2 giây; máy chủ xóa peer khi kết nối đóng hoặc không có yêu cầu trong 20 giây.

Mỗi lần gửi tin hoặc file mở một kết nối TCP trực tiếp tới peer đích. Điều khiển sử dụng JSON UTF-8, đứng trước bởi độ dài 4 byte big-endian (tối đa 64 KiB), tránh giả định rằng một lần `recv()` nhận được trọn thông điệp.

Quy trình truyền file:

1. Gửi metadata: tên file, số byte, thông tin người gửi.
2. Người nhận chấp thuận và tự chọn đường dẫn lưu; từ chối thì không truyền nội dung.
3. Truyền đúng số byte đã thông báo theo khối 64 KiB, không nạp toàn bộ file vào RAM.
4. Gửi SHA-256 của luồng byte; người nhận tính lại để so sánh.
5. Chỉ khi mã khớp mới đổi file `.part` thành file đích và xác nhận hoàn tất cho người gửi. File đích cũ được giữ nguyên nếu truyền lỗi trước bước này.

File tối đa 2 GiB; hỗ trợ file rỗng và tên Unicode. Timeout khi truyền là 30 giây không có dữ liệu. File tạm được dọn khi có lỗi thông thường; nếu tiến trình bị tắt cưỡng bức có thể còn `.part`. Có tối đa 8 kết nối nhận đồng thời. Giao diện cập nhật tiến độ của tác vụ có sự kiện mới nhất; nhật ký ghi kết quả các tác vụ.

Luồng mạng chạy nền; giao diện nhận sự kiện qua `queue.Queue` để không gọi Tkinter từ luồng mạng. Lịch sử chat lưu trong RAM của mỗi cửa sổ, tách theo ID phiên người dùng; không lưu lại sau khi đóng ứng dụng. Tin gửi thành công nghĩa là tiến trình nhận đã nhận, không có nghĩa người dùng đã đọc.

## Phạm vi bài thực hành

Thiết kế minh họa kiểu danh bạ tập trung + truyền trực tiếp, phù hợp đề bài Client–Server kết hợp P2P. Không tái tạo đầy đủ Skype, Napster, Gnutella hay Kazaa; không có tìm kiếm kho file chia sẻ hoặc mạng overlay phân tán.

Ứng dụng dành cho LAN tin cậy: chưa có tài khoản, xác thực người gửi hoặc mã hóa TLS. SHA-256 kiểm tra toàn vẹn, không xác thực danh tính. Chưa triển khai NAT traversal, STUN/TURN, relay, gửi offline hoặc tiếp tục file đang dở. Khi mất máy chủ, client ngắt phiên và cần kết nối lại; bản này không hỗ trợ tiếp tục khám phá peer khi máy chủ ngừng hoạt động.

## Cấu trúc

| File | Vai trò |
| --- | --- |
| `server.py` | Máy chủ danh bạ đa luồng |
| `protocol.py` | Đóng khung JSON, đọc đủ dữ liệu TCP |
| `peer.py` | Kết nối danh bạ, chat P2P, truyền file và SHA-256 |
| `client.py` | Giao diện tiếng Việt |
| `test_app.py` | Kiểm thử tích hợp qua socket TCP thật trên localhost |
| `start-demo.bat` | Mở nhanh một máy chủ và hai client trên Windows |

## Kiểm thử

```powershell
python -m unittest -v
```

Kiểm tra khám phá peer, chat Unicode, đăng xuất, tên trùng, gửi file nhị phân và file rỗng, từ chối file, SHA-256 sai và bảo toàn file đích cũ. Để kiểm chứng giao diện và mạng thực tế, chạy thêm kịch bản hai cửa sổ hoặc hai máy như trên.
