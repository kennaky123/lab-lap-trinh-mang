@echo off
chcp 65001 > nul
echo =====================================================================
echo    KHỞI CHẠY DEMO ĐÁNH GIÁ CHẤM ĐIỂM (WEB SERVER + WEB BROWSER)
echo    Đề bài: Web Server (Đa dịch vụ .pdf, .mp3, .mp4...)
echo            Web Client Browser (GET, HEAD, POST, History, Download...)
echo            Giao diện tương tự Chrome / Cốc Cốc
echo =====================================================================

if not exist bin mkdir bin
if not exist downloads mkdir downloads
if not exist browser_data mkdir browser_data

echo [1/4] Đang kiểm tra và biên dịch mã nguồn Java...
javac -encoding UTF-8 -d bin src/web/common/*.java src/web/server/*.java src/web/client/*.java src/web/ui/*.java

echo [2/4] Khởi động Web Server Dashboard (Port 8080)...
start "Web Server Dashboard" java -Dfile.encoding=UTF-8 -cp bin web.server.WebServerGUI

echo Chờ 2 giây để Web Server mở cổng mạng...
timeout /t 2 /nobreak > nul

echo [3/4] Khởi động Chrome / Cốc Cốc Web Browser (Java Swing)...
start "Chrome / Coc Coc Web Browser" java -Dfile.encoding=UTF-8 -cp bin web.client.WebBrowserGUI

echo =====================================================================
echo    [HOÀN TẤT] Cả Web Server và Web Browser đã được bật!
echo    - Địa chỉ Web Server: http://localhost:8080/
echo    - Thử nghiệm dịch vụ: Tải file PDF, MP3, MP4, gửi Form POST
echo    - Thử nghiệm phương thức: Tab Công cụ HTTP (GET / HEAD / POST)
echo =====================================================================

