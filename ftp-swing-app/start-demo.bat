@echo off
chcp 65001 > nul
echo =====================================================================
echo    KHỞI CHẠY DEMO ĐÁNH GIÁ CHẤM ĐIỂM (1 FTP SERVER + 1 FTP CLIENT)
echo    Đề bài: FTP Server (có Database) + FTP Client (Tương tự FileZilla)
echo =====================================================================

if not exist bin mkdir bin
if not exist ftp_data mkdir ftp_data
if not exist server_root mkdir server_root
if not exist client_local mkdir client_local

echo [1/4] Đang kiểm tra và biên dịch mã nguồn Java...
javac -encoding UTF-8 -d bin src/ftp/common/*.java src/ftp/server/*.java src/ftp/client/*.java src/ftp/ui/*.java

echo [2/4] Khởi động FTP Server Dashboard...
start "FTP Server Dashboard" java -Dfile.encoding=UTF-8 -cp bin ftp.server.FtpServerGUI

echo Chờ 2 giây để Server mở cổng 2121...
timeout /t 2 /nobreak > nul

echo [3/4] Khởi động FTP Client (FileZilla Style)...
start "FTP Client (FileZilla Style)" java -Dfile.encoding=UTF-8 -cp bin ftp.client.FtpClientGUI

echo =====================================================================
echo    [HOÀN TẤT] Cả FTP Server và FTP Client đã được bật!
echo    - Tài khoản mặc định: admin / 123456 (Cổng 2121)
echo    - Tài khoản sinh viên: student / 123456
echo    - Bấm nút [Kết Nối Nhanh] trên Client để trải nghiệm ngay.
echo =====================================================================

