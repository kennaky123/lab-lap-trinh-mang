@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo =====================================================================
echo    KHỞI CHẠY DEMO ĐÁNH GIÁ CHẤM ĐIỂM (MAIL SERVER + GMAIL CLIENT)
echo    Đề bài: Có giao thức POP3 và SMTP - Gửi được trên Gmail và Localhost
echo            Có dữ liệu trên Database của Server - Giao diện như Gmail
echo =====================================================================

if not exist bin mkdir bin
if not exist mail_data mkdir mail_data
if not exist attachments mkdir attachments

echo [1/4] Đang kiểm tra và biên dịch mã nguồn Java...
javac --release 11 -encoding UTF-8 -d bin src/mail/common/*.java src/mail/server/*.java src/mail/client/*.java src/mail/ui/*.java

echo [2/4] Khởi động Mail Server (SMTP 2525 + POP3 1110)...
start "Mail Server Dashboard" java -Dfile.encoding=UTF-8 -cp bin mail.server.MailServerGUI

echo Chờ 2 giây để Server mở cổng dịch vụ...
timeout /t 2 /nobreak > nul

echo [3/4] Khởi động 2 cửa sổ Gmail Client (Alice và Bob)...
start "Gmail Client - Alice" java -Dfile.encoding=UTF-8 -cp bin mail.client.MailClientGUI alice@local.com
start "Gmail Client - Bob" java -Dfile.encoding=UTF-8 -cp bin mail.client.MailClientGUI bob@local.com

echo =====================================================================
echo    [HOÀN TẤT] Cả Mail Server và Gmail Client đã được bật!
echo    - Tài khoản Localhost mặc định: alice@local.com (pass: 123456)
echo    - Tài khoản nhận thử nghiệm: bob@local.com (pass: 123456)
echo    - Hỗ trợ gửi/nhận thư Localhost lẫn kết nối trực tiếp Gmail thật!
echo =====================================================================

