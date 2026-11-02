@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo [1/2] Đang biên dịch mã nguồn Java Swing Chat App...
echo ===================================================

if not exist bin mkdir bin
if not exist downloads mkdir downloads
if not exist chat_data mkdir chat_data

javac --release 11 -encoding UTF-8 -cp "lib/*" -d bin src/chatapp/common/*.java src/chatapp/server/*.java src/chatapp/client/*.java src/chatapp/ui/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Biên dịch thành công! Các tệp .class đã lưu trong thư mục bin/
) else (
    echo [LỖI] Quá trình biên dịch thất bại. Vui lòng kiểm tra cài đặt JDK.
)

pause
