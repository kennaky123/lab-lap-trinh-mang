@echo off
chcp 65001 > nul
echo ===================================================
echo Đang mở ỨNG DỤNG FTP CLIENT (FileZilla Swing UI)...
echo ===================================================

if not exist bin\ftp\client\FtpClientGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac -encoding UTF-8 -d bin src/ftp/common/*.java src/ftp/server/*.java src/ftp/client/*.java src/ftp/ui/*.java
)

start "FTP Client (FileZilla Style)" java -Dfile.encoding=UTF-8 -cp bin ftp.client.FtpClientGUI

