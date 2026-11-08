@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo =====================================================================
echo    KHỞI CHẠY DEMO ĐÁNH GIÁ CHẤM ĐIỂM (1 SERVER + 6 CLIENTS CHAT)
echo    Yêu cầu: Chạy từ 5 đến 6 Client cùng lúc (TCP + UDP Multicast)
echo    Hỗ trợ: Chat riêng 1-1, SQL Database (SQLite), Gửi ảnh, video, icon
echo =====================================================================

if not exist bin mkdir bin
if not exist downloads mkdir downloads
if not exist chat_data mkdir chat_data

echo [1/8] Đang kiểm tra và biên dịch mã nguồn...
javac --release 11 -encoding UTF-8 -cp "lib/*" -d bin src/chatapp/common/*.java src/chatapp/server/*.java src/chatapp/client/*.java src/chatapp/ui/*.java

echo [2/8] Khởi động Server GUI...
start "Chat Server Dashboard" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.server.ServerGUI

echo Chờ 2 giây để Server mở cổng mạng...
timeout /t 2 /nobreak > nul

echo [3/8] Khởi động Client 1 (Alice)...
start "Client 1 - Alice" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI Alice localhost 8888

echo [4/8] Khởi động Client 2 (Bob)...
start "Client 2 - Bob" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI Bob localhost 8888

echo [5/8] Khởi động Client 3 (Charlie)...
start "Client 3 - Charlie" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI Charlie localhost 8888

echo [6/8] Khởi động Client 4 (David)...
start "Client 4 - David" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI David localhost 8888

echo [7/8] Khởi động Client 5 (Emma)...
start "Client 5 - Emma" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI Emma localhost 8888

echo [8/8] Khởi động Client 6 (Frank)...
start "Client 6 - Frank" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI Frank localhost 8888

echo =====================================================================
echo    [HOÀN TẤT] 1 Server và 6 Client đã được bật đồng thời!
echo    - Thử nghiệm Chat Riêng: Click chọn tên bạn chat trong danh sách
echo    - Thử nghiệm Gửi Ảnh / Video: Bấm nút Gửi Hình Ảnh hoặc Gửi Video
echo    - Thử nghiệm CSDL: Mở tab Lịch Sử CSDL trên Server xem bảng SQLite
echo =====================================================================
