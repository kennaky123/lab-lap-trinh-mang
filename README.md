# Lập trình mạng

Tổng hợp các đồ án và bài thực hành môn Lập trình mạng bao gồm các ứng dụng Java Swing đa luồng (Socket TCP/UDP, RMI) và các bài thực hành Python Socket/Tkinter.

## Yêu cầu môi trường

- **Java**: JDK 8+ hoặc JDK 17/21/24 (các tệp batch `.bat` tự động phát hiện và ưu tiên `JAVA_HOME`).
- **Python**: Python 3.10+ tích hợp sẵn Tkinter (chỉ dùng thư viện chuẩn, không cần `pip install`).

## Danh mục các bài Lab

| Lab | Nội dung | Hướng dẫn chi tiết | Khởi chạy nhanh |
| --- | --- | --- | --- |
| **1. Chat Java Swing** | Chat đa phương tiện (Broadcast, Multicast nhóm, Voice thoại, Video, File, SQLite CSDL) | [chat-swing-app/README.md](chat-swing-app/README.md) | `chat-swing-app\start-demo-6-clients.bat` |
| **2. FTP Java Swing** | FTP Server (CSDL người dùng, quản trị & kiểm toán RFC 959) + FTP Client (Giao diện FileZilla) | [ftp-swing-app/README.md](ftp-swing-app/README.md) | `ftp-swing-app\start-demo.bat` |
| **3. Mail Java Swing** | Mail Server (SMTP 2525 & POP3 1110 có CSDL) + Gmail Client (Localhost & Gmail Thật) | [mail-swing-app/README.md](mail-swing-app/README.md) | `mail-swing-app\start-demo.bat` |
| **4. Web Java Swing** | Web Server HTTP/1.1 (Đa dịch vụ .pdf, .mp3, .mp4, GET/POST/HEAD) + Web Browser Client | [web-swing-app/README.md](web-swing-app/README.md) | `web-swing-app\start-demo.bat` |
| **5. RMI Sync Java Swing** | RMI Đồng bộ hóa dữ liệu 2 Server (Active-Active 2-Way Sync) trên máy thực & máy ảo | [rmi-sync-app/README.md](rmi-sync-app/README.md) | `rmi-sync-app\start-demo.bat` |
| **Lab 4 (Python)** | Chat và truyền file P2P / Client–Server kết hợp (Python Socket, Tkinter) | [DangQuocToan-ITB200-4/README.md](DangQuocToan-ITB200-4/README.md) | `DangQuocToan-ITB200-4\p2p-chat\start-demo.bat` |
| **Lab 5 — Bài 1 (Python)** | ExchangeRateServer và ExchangeRateTable (Python Socket, Tkinter) | [lab-5/README.md](lab-5/README.md) | `lab-5\start-exchange-demo.bat` |
| **Lab 5 — Bài 2 (Python)** | Mail Server và Mail Client (Python Socket, Tkinter) | [lab-5/README.md](lab-5/README.md) | `lab-5\start-mail-demo.bat` |

## Hướng dẫn sử dụng nhanh

- Nhấp đúp trực tiếp các tệp `.bat` trong từng thư mục lab tương ứng để khởi chạy Server và Client.
- Các script batch Java đã được nâng cấp tự động điều hướng đúng thư mục thực thi và nạp đúng cấu hình `JAVA_HOME`.
- Nếu trên Windows không nhận diện lệnh `python`, bạn có thể dùng `py` trong terminal.
