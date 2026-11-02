# TÀI LIỆU HƯỚNG DẪN TRẢ LỜI VẤN ĐÁP: RMI ĐỒNG BỘ HÓA 2 SERVER

> **Tên bài:** RMI Đồng bộ hóa dữ liệu trên 2 Server (Active-Active 2-Way Data Replication)  
> **Ngôn ngữ:** Java (RMI - Remote Method Invocation & Swing UI)  
> **Mô hình triển khai:** 2 Server trên 2 máy thực và các Client trên các máy ảo (hoặc mô phỏng linh hoạt trên localhost/LAN)

---

## 1. CÂU HỎI VỀ KIẾN TRÚC RMI (REMOTE METHOD INVOCATION)

### Câu 1: Cơ chế hoạt động của Java RMI là gì? Khác gì so với Socket thông thường?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> - **Socket thông thường (TCP/UDP):** Lập trình viên phải tự thiết kế định dạng gói tin (giao thức tầng ứng dụng), tự đọc/ghi từng byte qua luồng `InputStream` / `OutputStream`, và tự bóc tách dữ liệu.
> - **Java RMI (Gọi phương thức từ xa):** Cho phép một đối tượng trên một JVM (máy này) gọi trực tiếp một phương thức của một đối tượng đang nằm trên JVM khác (máy khác) giống hệt như đang gọi hàm cục bộ.
> - **Kiến trúc gồm 3 thành phần chính:**
>   1. **Remote Interface (`IDataSyncService`):** Định nghĩa danh sách các hàm mà Client hoặc Server khác có thể gọi từ xa (phải `extends java.rmi.Remote` và ném ra `RemoteException`).
>   2. **Remote Object (`DataSyncServiceImpl`):** Cài đặt logic thực tế, kế thừa `UnicastRemoteObject`.
>   3. **RMI Registry (`LocateRegistry`):** Đóng vai trò là danh bạ (Naming Service), máy chủ đăng ký đối tượng từ xa bằng hàm `rebind("DataSyncService", service)` để Client tìm kiếm qua `lookup()`."

---

## 2. CÂU HỎI VỀ ĐỒNG BỘ HÓA DỮ LIỆU TRÊN 2 SERVER (CÁCH CHẤM)

### Câu 2: Trong bài, cơ chế đồng bộ hóa dữ liệu 2 chiều (2-Way Replication) hoạt động ra sao?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, hệ thống áp dụng mô hình **Active-Active Peer Replication**:
> 1. Cả **Server 1** và **Server 2** đều có cơ sở dữ liệu riêng biệt lưu trên đĩa (`server1_data` và `server2_data`).
> 2. Khi một Client kết nối vào **Server 1** và thực hiện Thêm/Sửa/Xóa (`addRecord`, `updateRecord`, `deleteRecord`):
>    - Server 1 ghi thay đổi vào CSDL cục bộ của mình.
>    - Ngay lập tức, Server 1 đóng vai trò là một RMI Client, gọi phương thức từ xa `syncReplicate(action, item, "Server 1")` sang Server 2.
>    - Server 2 nhận được lời gọi, cập nhật ngay vào CSDL của mình và làm mới giao diện theo thời gian thực mà không cần người dùng tải lại.
> 3. Tương tự, nếu Client khác kết nối vào **Server 2** thao tác, dữ liệu cũng được đẩy ngược lại Server 1."

---

### Câu 3: Làm thế nào để tránh vòng lặp vô tận (Ping-Pong Loop) khi 2 Server đồng bộ cho nhau?
**Trả lời:**  
> "Dạ thưa Thầy/Cô, trong hàm `syncReplicate(action, item, senderServerId)` trong file `DataSyncServiceImpl.java`:
> - Em có gắn tham số nhận diện nguồn gửi: `senderServerId`.
> - Khi Server 2 nhận được lệnh đồng bộ từ Server 1 (`senderServerId = "Server 1"`), nó chỉ cập nhật vào CSDL của nó và **KHÔNG gọi ngược lại `syncReplicate` sang Server 1 nữa**.
> - Nhờ kiểm tra `if (serverId.equals(senderServerId)) return;` nên hoàn toàn triệt tiêu được nguy cơ lặp vòng vô hạn."

---

### Câu 4: Nếu một Server bị sập (Crash / Offline), hệ thống xử lý như thế nào? (Tính sẵn sàng cao - High Availability)
**Trả lời:**  
> "Dạ thưa Thầy/Cô, hệ thống có hai cơ chế bảo vệ tối quan trọng:
>
> 1. **Phía Client (Tự động chuyển đổi dự phòng - Auto Failover):**  
>    - Nếu Client đang kết nối tới Server 1 mà Server 1 mất điện hoặc tắt đột ngột, Client sẽ bắt được ngoại lệ `RemoteException` và **tự động chuyển hướng kết nối sang Server 2** để người dùng tiếp tục làm việc bình thường, không bị gián đoạn.
>
> 2. **Phía Server (Đồng bộ bù tự động - Catch-up Full Sync):**  
>    - Mỗi Server chạy một luồng giám sát ngầm (`startPeerMonitor`) liên tục ping đối tác.
>    - Khi Server bị sập được bật trở lại, nó sẽ tự động phát hiện đối tác đang chạy và gọi hàm RMI `requestFullSync()`.
>    - Toàn bộ các bản ghi mới nhất được tạo trong lúc nó offline sẽ được kéo về và cập nhật đầy đủ (so sánh `lastModified`), đảm bảo dữ liệu 2 bên luôn luôn nhất quán (Eventual Consistency)."

---

## 3. CÂU HỎI VỀ TRIỂN KHAI TRÊN 2 MÁY THỰC VÀ MÁY ẢO

### Câu 5: Khi Thầy/Cô yêu cầu chạy trên 2 máy tính thực tế và các máy ảo, em cấu hình IP như thế nào?
**Trả lời:**  
> "Dạ thưa Thầy/Cô:
> 1. **Trên Máy thực 1 (ví dụ IP: 192.168.1.10):**  
>    - Chạy lệnh: `java -cp bin rmi.server.RmiServerGUI 1 192.168.1.20 1100`  
>    *(Máy 1 mở cổng 1099, đối tác là Máy 2 tại 192.168.1.20:1100)*
> 2. **Trên Máy thực 2 (ví dụ IP: 192.168.1.20):**  
>    - Chạy lệnh: `java -cp bin rmi.server.RmiServerGUI 2 192.168.1.10 1099`  
>    *(Máy 2 mở cổng 1100, đối tác là Máy 1 tại 192.168.1.10:1099)*
> 3. **Trên các Máy ảo (VMware / VirtualBox) hoặc máy trạm:**  
>    - Bật ứng dụng Client `run-client.bat` ➔ Nhấn nút **⚙ Đổi IP Máy Chủ** ➔ Điền IP của Máy thực 1 và Máy thực 2 ➔ Client có thể thao tác và kiểm tra đồng bộ ngay lập tức!"

