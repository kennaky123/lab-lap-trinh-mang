# HỆ THỐNG RMI ĐỒNG BỘ HÓA DỮ LIỆU TRÊN 2 SERVER (ACTIVE-ACTIVE REPLICATION)

Ứng dụng hoàn chỉnh gồm **2 Máy chủ RMI Server chạy song song (có cơ sở dữ liệu riêng biệt và tự động đồng bộ hóa hai chiều)** cùng **Ứng dụng RMI Client (hỗ trợ chuyển đổi máy chủ tự động - Auto Failover)**, được xây dựng bằng **Java RMI & Java Swing** phục vụ môn học **Lập trình mạng**.

---

## 🌟 ĐÁP ỨNG TOÀN DIỆN TIÊU CHÍ ĐỀ BÀI

| Tiêu chí đề bài | Đánh giá | Chi tiết giải pháp hiện thực |
| :--- | :---: | :--- |
| **Công nghệ Java RMI** |  **Đạt** | - Định nghĩa Remote Interface `IDataSyncService extends Remote`.<br>- Triển khai `DataSyncServiceImpl extends UnicastRemoteObject`.<br>- Đăng ký vào RMI Registry qua `LocateRegistry` và `rebind()`. |
| **Đồng bộ hóa dữ liệu trên 2 Server** |  **Đạt** | - **Mô hình Active-Active 2-Way Sync:** Bất kỳ thao tác Thêm/Sửa/Xóa nào trên Server 1 đều tự động kích hoạt lời gọi RMI `syncReplicate()` sang Server 2 và ngược lại.<br>- **Chống lặp vòng (Cycle Prevention):** Nhận diện ID Server nguồn để không bị lặp vô tận.<br>- **Đồng bộ bù (Catch-up Full Sync):** Khi 1 Server bị sập rồi bật lại, Server đó tự động kéo toàn bộ dữ liệu mới nhất từ Server đối tác về qua `requestFullSync()`. |
| **Chạy trên 2 máy thực và máy ảo (Cách chấm)** |  **Đạt** | - **Hỗ trợ 2 máy tính thực tế:** Chỉ định IP của máy thực 1 và máy thực 2 qua tham số dòng lệnh hoặc form cấu hình.<br>- **Hỗ trợ máy ảo / máy trạm:** Client có nút **⚙ Đổi IP Máy Chủ** và tính năng **Auto Failover** (nếu Server 1 chết, tự động chuyển ngay sang Server 2 mà không mất dữ liệu). |
| **Giao diện trực quan Swing** |  **Đạt** | - **Server Dashboard:** Giám sát trạng thái Online/Offline của Server đối tác, bảng CSDL phân tán, và bảng nhật ký các lời gọi RMI thời gian thực.<br>- **Client GUI:** Bảng danh sách bản ghi, form CRUD, tìm kiếm, nút chuyển đổi Server linh hoạt. |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN

```
rmi-sync-app/
├── src/rmi/
│   ├── common/
│   │   ├── RecordItem.java            # Model dữ liệu phân tán (ID, Tên, Ngành, Điểm, Timestamp, Server)
│   │   └── IDataSyncService.java      # Remote Interface định nghĩa các phương thức RMI
│   ├── server/
│   │   ├── RmiDatabase.java           # Quản lý CSDL lưu trữ trên đĩa cho từng Server
│   │   ├── DataSyncServiceImpl.java   # Logic thực thi đồng bộ RMI hai chiều & Auto Catch-up
│   │   └── RmiServerGUI.java          # Giao diện quản trị Server Dashboard
│   ├── client/
│   │   └── RmiClientGUI.java          # Giao diện Client thao tác dữ liệu & Auto Failover
│   └── ui/
│       └── ModernScrollBarUI.java     # Thanh cuộn phẳng hiện đại
├── bin/                               # Chứa các file .class đã biên dịch
├── server1_data/                      # Thư mục CSDL riêng của Server 1
├── server2_data/                      # Thư mục CSDL riêng của Server 2
├── compile.bat                        # Biên dịch dự án bằng javac
├── run-server1.bat                    # Chạy Server 1 (Cổng RMI 1099)
├── run-server2.bat                    # Chạy Server 2 (Cổng RMI 1100)
├── run-client.bat                     # Chạy RMI Client
├── start-demo.bat                     # Khởi chạy đồng thời 2 Server + 2 Client
├── README.md                          # Tài liệu hướng dẫn này
└── HUONG_DAN_TRA_LOI_VAN_DAP_RMI.md   # Hướng dẫn trả lời vấn đáp thi
```

---

## 🚀 HƯỚNG DẪN CHẠY DEMO

### Cách 1: Khởi chạy Demo 1-Click (Khuyên dùng khi báo cáo)
Nhấp đúp vào file:
👉 **[`start-demo.bat`](file:///d:/VScode/lap-trinh-mang/rmi-sync-app/start-demo.bat)**

Hệ thống sẽ tự động mở cùng lúc:
1. **RMI Server 1 Dashboard** (Cổng 1099).
2. **RMI Server 2 Dashboard** (Cổng 1100).
3. **RMI Client A** (Kết nối Server 1).
4. **RMI Client B** (Kết nối Server 2).

---

### Các kịch bản thử nghiệm chấm điểm:
1. **Kiểm tra Đồng bộ 2 chiều (Real-time 2-Way Sync):**
   - Trên **Client A** (đang kết nối Server 1), nhập bản ghi mới: ID `SV101`, Tên `Nguyễn Văn An`, Điểm `9.0` ➔ Bấm **+ Thêm Mới**.
   - Ngay lập tức:
     + Bảng CSDL của **Server 1** xuất hiện bản ghi `SV101`.
     + Bảng CSDL của **Server 2** tự động xuất hiện bản ghi `SV101` (Cột Server xuất phát ghi rõ: `Server 1`).
     + Mở sang **Client B** (đang kết nối Server 2) ➔ Bấm **🔄 Nạp Lại** ➔ Dữ liệu đã có ngay trên Client B!
2. **Kiểm tra Tự động chuyển đổi dự phòng (Auto Failover):**
   - Đóng (tắt) cửa sổ **Server 1**.
   - Trên **Client A**, thử bấm Thêm hoặc Cập nhật ➔ Client A phát hiện Server 1 mất kết nối, hiển thị thông báo và **tự động chuyển ngay sang Server 2** mà không bị văng ứng dụng.
3. **Kiểm tra Đồng bộ bù (Catch-up Full Sync khi Server bật lại):**
   - Trong lúc Server 1 đang tắt, dùng Client B thêm bản ghi mới `SV102` vào Server 2.
   - Bật lại Server 1 (bằng file `run-server1.bat`).
   - Server 1 khởi động lên, phát hiện Server 2 đang chạy ➔ Tự động kích hoạt cơ chế **Catch-up Sync** kéo toàn bộ bản ghi `SV102` về CSDL của Server 1.
4. **Triển khai trên 2 máy tính thực tế và các máy ảo (theo yêu cầu đề bài):**
   - **Tự động nhận diện IP LAN:** Khi khởi động, RMI Server tự phát hiện card mạng LAN thực tế và hiển thị ngay trên thanh tiêu đề (ví dụ: `IP LAN: 192.168.1.15:1099`), đồng thời tự thiết lập `java.rmi.server.hostname` và ghim cổng RMI UnicastRemoteObject để không bị tường lửa Windows chặn.
   - **Cấu hình Server đối tác linh hoạt:** Bấm nút **⚙ Đổi IP Đối Tác** trên thanh công cụ của Server để thay đổi IP Server còn lại ngay khi đang chạy mà không cần gõ lệnh.
   - **Client dò tìm Server tự động:** Trên Client, chỉ cần bấm **Tìm IP LAN**, Client sẽ gửi gói tin UDP Broadcast tìm Server trong mạng nội bộ và tự điền IP kết nối; hoặc bấm **⚙ Đổi IP Máy Chủ** để nhập thủ công IP máy thực 1 và máy thực 2.

