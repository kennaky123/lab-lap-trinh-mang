@echo off
chcp 65001 > nul
echo =====================================================================
echo    KHỞI CHẠY DEMO ĐÁNH GIÁ CHẤM ĐIỂM (RMI ĐỒNG BỘ HÓA 2 SERVER)
echo    Yêu cầu: Đồng bộ dữ liệu 2 Server (Active-Active 2-Way Sync)
echo             Mô phỏng 2 Server và các Client (máy thực / máy ảo)
echo =====================================================================

if not exist bin mkdir bin
if not exist server1_data mkdir server1_data
if not exist server2_data mkdir server2_data

echo [1/5] Đang kiểm tra và biên dịch mã nguồn Java...
javac -encoding UTF-8 -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java

echo [2/5] Khởi động RMI Server 1 (Cổng 1099)...
start "RMI Server 1 (Port 1099)" java -Dfile.encoding=UTF-8 -cp bin rmi.server.RmiServerGUI 1 localhost 1100

echo [3/5] Khởi động RMI Server 2 (Cổng 1100)...
start "RMI Server 2 (Port 1100)" java -Dfile.encoding=UTF-8 -cp bin rmi.server.RmiServerGUI 2 localhost 1099

echo Chờ 2 giây để 2 Server bắt tay RMI Registry...
timeout /t 2 /nobreak > nul

echo [4/5] Khởi động Client A (Kết nối Server 1)...
start "RMI Client A" java -Dfile.encoding=UTF-8 -cp bin rmi.client.RmiClientGUI

echo [5/5] Khởi động Client B (Kết nối Server 2)...
start "RMI Client B" java -Dfile.encoding=UTF-8 -cp bin rmi.client.RmiClientGUI

echo =====================================================================
echo    [HOÀN TẤT] 2 Server và 2 Client đã được bật đồng thời!
echo    - Thử nghiệm 1: Nhập dữ liệu trên Client A -> Xem Server 2 & Client B
echo                    tự động đồng bộ hóa tức thì qua RMI.
echo    - Thử nghiệm 2: Tắt Server 1 -> Client A tự động Failover sang Server 2.
echo    - Thử nghiệm 3: Bật lại Server 1 -> Tự động Catch-up Full Sync!
echo =====================================================================

