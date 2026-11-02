@echo off
chcp 65001 > nul
echo ===================================================
echo Đang khởi động MÁY CHỦ FTP (Server GUI)...
echo ===================================================

if not exist bin\ftp\server\FtpServerGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac -encoding UTF-8 -d bin src/ftp/common/*.java src/ftp/server/*.java src/ftp/client/*.java src/ftp/ui/*.java
)

start "FTP Server Dashboard" java -Dfile.encoding=UTF-8 -cp bin ftp.server.FtpServerGUI

