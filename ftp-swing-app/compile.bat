@echo off
chcp 65001 > nul
echo ===================================================
echo [1/2] Đang biên dịch mã nguồn Java Swing FTP App...
echo ===================================================

if not exist bin mkdir bin
if not exist ftp_data mkdir ftp_data
if not exist server_root mkdir server_root
if not exist client_local mkdir client_local

javac -encoding UTF-8 -d bin src/ftp/common/*.java src/ftp/server/*.java src/ftp/client/*.java src/ftp/ui/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Biên dịch thành công! Các tệp .class đã lưu trong thư mục bin/
) else (
    echo [LỖI] Quá trình biên dịch thất bại. Vui lòng kiểm tra cài đặt JDK.
)

pause

