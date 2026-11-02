@echo off
chcp 65001 > nul
echo ===================================================
echo [1/2] Đang biên dịch mã nguồn Java Web Server & Browser...
echo ===================================================

if not exist bin mkdir bin
if not exist downloads mkdir downloads
if not exist browser_data mkdir browser_data

javac -encoding UTF-8 -d bin src/web/common/*.java src/web/server/*.java src/web/client/*.java src/web/ui/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Biên dịch thành công! Các tệp .class đã lưu trong thư mục bin/
) else (
    echo [LỖI] Quá trình biên dịch thất bại. Vui lòng kiểm tra cài đặt JDK.
)

pause

