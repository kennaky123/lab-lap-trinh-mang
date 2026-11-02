@echo off
chcp 65001 > nul
echo ===================================================
echo Đang khởi động MÁY CHỦ WEB SERVER (HTTP/1.1)...
echo ===================================================

if not exist bin\web\server\WebServerGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac -encoding UTF-8 -d bin src/web/common/*.java src/web/server/*.java src/web/client/*.java src/web/ui/*.java
)

start "Web Server Dashboard (Port 8080)" java -Dfile.encoding=UTF-8 -cp bin web.server.WebServerGUI

