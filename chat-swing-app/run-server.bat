@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Đang khởi động MÁY CHỦ CHAT (Server GUI)...
echo ===================================================

if not exist bin\chatapp\server\ServerGUI.class (
    echo [THÔNG BÁO] Chưa biên dịch, đang tự động biên dịch...
    javac --release 11 -encoding UTF-8 -cp "lib/*" -d bin src/chatapp/common/*.java src/chatapp/server/*.java src/chatapp/client/*.java src/chatapp/ui/*.java
)

start "Chat Server Dashboard" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.server.ServerGUI
