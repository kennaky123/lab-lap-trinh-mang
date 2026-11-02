@echo off
chcp 65001 > nul
echo ===================================================
echo Đang khởi động MÁY CHỦ CHAT (Server GUI)...
echo ===================================================

if not exist bin\chatapp\server\ServerGUI.class (
    echo [THÔNG BÁO] Chưa biên dịch, đang tự động biên dịch...
    javac -encoding UTF-8 -cp "lib/*" -d bin src/chatapp/common/*.java src/chatapp/server/*.java src/chatapp/client/*.java src/chatapp/ui/*.java
)

start "Chat Server Dashboard" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.server.ServerGUI
