@echo off
chcp 65001 > nul
echo ===================================================
echo [1/2] Đang biên dịch mã nguồn Java RMI Sync App...
echo ===================================================

if not exist bin mkdir bin
if not exist server1_data mkdir server1_data
if not exist server2_data mkdir server2_data

javac -encoding UTF-8 -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Biên dịch thành công! Các tệp .class đã lưu trong thư mục bin/
) else (
    echo [LỖI] Quá trình biên dịch thất bại. Vui lòng kiểm tra cài đặt JDK.
)

pause

