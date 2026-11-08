# TÀI LIỆU HƯỚNG DẪN TRẢ LỜI VẤN ĐÁP (BẢO VỆ ĐỒ ÁN LẬP TRÌNH MẠNG)

> **Tên bài:** Ứng dụng Chat Đa Năng Client-Server / P2P (Giao diện Java Swing phong cách Zalo / Messenger)  
> **Ngôn ngữ:** Java (JDK 8 / 11 / 17 / 21 / 23)  
> **Giao thức:** TCP (ServerSocket, Socket) + UDP (DatagramSocket, MulticastSocket `230.0.0.1`)

---

## 1. CÂU HỎI VỀ KIẾN TRÚC & MẠNG (TCP vs UDP)

### Câu 1: Hệ thống này sử dụng TCP hay UDP? Tại sao?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, ứng dụng của em **kết hợp cả hai giao thức TCP và UDP** nhằm tối ưu cho từng mục đích sử dụng trong mạng:
>
> 1. **Giao thức TCP (Đáng tin cậy - Connection-oriented):**  
>    - Dùng `ServerSocket` trên cổng **8888** và các `Socket` kết nối liên tục từ Client.  
>    - **Mục đích:** Quản lý kết nối Client-Server, đăng nhập, duy trì danh sách Online thời gian thực, chat riêng 1-1, chat theo phòng và **truyền tải dữ liệu an toàn (tệp tin văn bản, file âm thanh, file video)** mà không bị mất mát hay đảo lộn thứ tự byte.
>
> 2. **Giao thức UDP (Tốc độ cao - Connectionless):**  
>    - Dùng `MulticastSocket` với địa chỉ nhóm **230.0.0.1** (cổng **4446**) và `DatagramSocket` (cổng **8889**).  
>    - **Mục đích:** 
>      + Triển khai phòng chat **UDP Multicast thuần túy** (các máy trong LAN nhận tin đồng thời bằng gói tin `DatagramPacket` mà không cần duy trì kết nối TCP đến Server).
>      + Phục vụ cơ chế **UDP Broadcast Discovery** để tự động tìm kiếm máy chủ trong mạng nội bộ."

---

### Câu 2: Trong code, Broadcast được thực hiện như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, Broadcast là cơ chế gửi một thông điệp tới **tất cả mọi người đang online**:
> - Vị trí code: Phương thức `broadcast(Message msg)` trong lớp `ChatServer.java`.
> - Khi một client gửi tin nhắn với receiver là `"ALL"`, Server nhận được gói tin, duyệt qua danh sách các luồng client đang kết nối (`ConcurrentHashMap<String, ClientHandler> clients`), và gọi hàm `ch.sendMessage(msg)` để đẩy dữ liệu xuống Socket TCP của từng client."

---

### Câu 3: Multicast trong bài được hiện thực như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, bài của em hiện thực **hai cấp độ Multicast**:
>
> 1. **Multicast Logic theo Phòng trên Server (TCP Group Routing):**  
>    - Mỗi Client có danh sách phòng đã đăng ký (`subscribedRooms`).  
>    - Khi có tin nhắn gửi vào phòng (ví dụ phòng `#Công-Nghệ`), hàm `multicastToRoom(room, msg)` trong `ChatServer.java` chỉ lọc và gửi gói tin đến những Client nào đã tham gia (`ch.isInRoom(room)`). Những client không ở trong phòng sẽ không nhận được.
>
> 2. **UDP Multicast Socket ở tầng Transport (Chuẩn môn Lập trình mạng):**  
>    - Nằm trong file `UdpMulticastService.java`.
>    - Sử dụng lớp `java.net.MulticastSocket` gắn vào cổng `4446`.
>    - Tham gia nhóm địa chỉ IP Class D `230.0.0.1` qua hàm `socket.joinGroup(groupAddress)`.
>    - Dữ liệu được đóng vào `DatagramPacket` và gửi trực tiếp vào nhóm mạng Multicast."

---

## 2. CÂU HỎI VỀ TRUYỀN DỮ LIỆU ĐA PHƯƠNG TIỆN (AUDIO, VIDEO, FILE)

### Câu 4: Chức năng ghi âm giọng nói (Audio Voice Note) hoạt động thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, em sử dụng bộ thư viện chuẩn của Java là `javax.sound.sampled`:
> - Khi bấm nút **🎙️ Ghi Âm Voice**, luồng `AudioRecorder.java` kích hoạt `TargetDataLine` đọc luồng âm thanh PCM từ Microphone của máy tính và lưu vào mảng byte trong bộ nhớ.
> - Khi bấm dừng, ứng dụng tự động đóng gói dữ liệu PCM với **Header chuẩn WAV 44 byte** (chứa thông tin RIFF, WAVE, fmt, sample rate 16000Hz, 16 bit, Mono).
> - File âm thanh WAV được chuyển thành mảng `byte[]` và truyền qua Socket TCP đến người nhận.
> - Phía người nhận có nút bấm **▶ Nghe Ghi Âm**, gọi lớp `AudioPlayer.java` dùng `javax.sound.sampled.Clip` để phát âm thanh trực tiếp trên loa."

---

### Câu 5: Việc gửi file và video đính kèm được xử lý ra sao?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> - Người dùng chọn tệp tin bất kỳ thông qua `JFileChooser`.
> - Ứng dụng đọc dữ liệu file thành mảng byte (`byte[] data = Files.readAllBytes(path)`), gói vào đối tượng `Message` với tên tệp, kích thước và loại thông điệp (`FILE_TRANSFER` hoặc `VIDEO_MESSAGE`).
> - Gói tin được tuần tự hóa (Serialization) và gửi qua luồng `ObjectOutputStream` trên đường truyền TCP.
> - Khi Client đích nhận được gói tin, lớp `ChatClient.java` tự động trích xuất mảng byte và ghi vào thư mục `downloads/`.
> - Người dùng có thể nhấn nút **Mở Tệp Tin** hoặc **▶ Mở Xem Video**, chương trình dùng `Desktop.getDesktop().open(file)` để kích hoạt trình phát video / xem tài liệu mặc định của hệ điều hành."

---

## 3. CÂU HỎI VỀ ĐA LUỒNG (MULTITHREADING) & CƠ SỞ DỮ LIỆU

### Câu 6: Làm thế nào Server có thể chịu tải và phục vụ 5 đến 6 Client cùng lúc mà không bị treo?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> - Server sử dụng mô hình **Đa luồng (Multi-threading)** kết hợp với `ExecutorService.newCachedThreadPool()`.
> - Luồng chính (`listenTcpClients`) chỉ làm nhiệm vụ đợi kết nối tại `tcpServerSocket.accept()`.
> - Mỗi khi có 1 Client kết nối, Server cấp phát riêng một luồng `ClientHandler` chạy độc lập để liên tục lắng nghe và nhận dữ liệu từ Client đó. Do đó 5, 6 hay nhiều client gửi tin đồng thời đều được xử lý song song, không gây nghẽn (blocking).
> - Phía Client, luồng nhận tin nhắn (`listenIncomingMessages`) chạy ngầm ở chế độ Daemon, khi nhận được tin sẽ gọi `SwingUtilities.invokeLater()` để cập nhật lên giao diện mà không làm đơ Event Dispatch Thread (EDT) của giao diện Swing."

---

### Câu 7: Cơ sở dữ liệu (Database) được thiết kế và lưu trữ như thế nào? Có dùng SQL không?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, ứng dụng sử dụng **Cơ sở dữ liệu SQL chuẩn (SQLite Database)** thông qua thư viện SQLite JDBC:
> - Tệp cơ sở dữ liệu được lưu tại `chat_data/chat.db`.
> - Hệ thống tự động khởi tạo các bảng SQL bằng câu lệnh:
>   + `CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, ip_address TEXT, port INTEGER, created_at INTEGER)`
>   + `CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, sender TEXT, receiver TEXT, room TEXT, content TEXT, file_name TEXT, file_size INTEGER, created_at INTEGER)`
>   + `CREATE TABLE IF NOT EXISTS rooms (room_name TEXT PRIMARY KEY, created_at INTEGER)`
> - Mọi thao tác lưu và truy vấn tin nhắn đều dùng các câu lệnh SQL chuẩn: `INSERT INTO messages...` và `SELECT * FROM messages...` với `PreparedStatement` để chống tấn công SQL Injection.
> - Thầy/Cô có thể kiểm tra trực tiếp trên tab **Lịch Sử CSDL** của Server Dashboard, toàn bộ dữ liệu tin nhắn, chat riêng, file và video đều được lưu vĩnh viễn trên SQL."

---

### Câu 8: Chức năng Chat Riêng 1-1 và gửi Hình ảnh / Video hoạt động thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> 1. **Chat Riêng 1-1 (Private Chat):**  
>    - Khi người dùng click vào một bạn chat trong danh mục **CHAT RIÊNG 1-1 (ONLINE)**, ứng dụng sẽ chuyển ngữ cảnh sang phòng trò chuyện bí mật chỉ giữa 2 người.
>    - Gói tin mang loại `CHAT_PRIVATE`, Server nhận diện người nhận và chỉ chuyển tiếp đúng Socket của người nhận đó, không phát tán cho các thành viên khác.
> 2. **Gửi Hình Ảnh & Video:**  
>    - **Hình ảnh:** Được truyền bằng loại `IMAGE_MESSAGE`. Phía người nhận sẽ giải mã mảng byte và tự động hiển thị trực tiếp ảnh thu nhỏ (Thumbnail Preview) bo góc trong bong bóng chat giống Zalo/Messenger, kèm nút mở xem ảnh kích thước gốc.
>    - **Video:** Được truyền bằng loại `VIDEO_MESSAGE` kèm tên tệp và dung lượng, tự động tải về thư mục `downloads/` và có nút bấm kích hoạt trình phát video của hệ điều hành."

