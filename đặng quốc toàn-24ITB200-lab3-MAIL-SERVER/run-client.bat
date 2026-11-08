@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Đang mở ỨNG DỤNG GMAIL CLIENT (Swing UI)...
echo ===================================================

if not exist bin\mail\client\MailClientGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac --release 11 -encoding UTF-8 -d bin src/mail/common/*.java src/mail/server/*.java src/mail/client/*.java src/mail/ui/*.java
)

start "Gmail Client (Java Swing)" java -Dfile.encoding=UTF-8 -cp bin mail.client.MailClientGUI

