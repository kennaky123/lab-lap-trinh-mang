@echo off
chcp 65001 > nul
echo ===================================================
echo [1/2] Đang biên dịch mã nguồn Java Swing Mail App...
echo ===================================================

if not exist bin mkdir bin
if not exist mail_data mkdir mail_data
if not exist attachments mkdir attachments

javac -encoding UTF-8 -d bin src/mail/common/*.java src/mail/server/*.java src/mail/client/*.java src/mail/ui/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Biên dịch thành công! Các tệp .class đã lưu trong thư mục bin/
) else (
    echo [LỖI] Quá trình biên dịch thất bại. Vui lòng kiểm tra cài đặt JDK.
)

pause

