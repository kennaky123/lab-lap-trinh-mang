@echo off
chcp 65001 > nul
echo ===================================================
echo Đang mở CLIENT CHAT SWING...
echo ===================================================

if not exist bin\chatapp\client\ClientGUI.class (
    echo [THÔNG BÁO] Chưa biên dịch, đang tự động biên dịch...
    javac -encoding UTF-8 -cp "lib/*" -d bin src/chatapp/common/*.java src/chatapp/server/*.java src/chatapp/client/*.java src/chatapp/ui/*.java
)

start "Chat Client" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" chatapp.client.ClientGUI
