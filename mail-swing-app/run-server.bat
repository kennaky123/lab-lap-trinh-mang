@echo off
chcp 65001 > nul
echo ===================================================
echo Đang khởi động MAIL SERVER (SMTP 2525 / POP3 1110)...
echo ===================================================

if not exist bin\mail\server\MailServerGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac -encoding UTF-8 -d bin src/mail/common/*.java src/mail/server/*.java src/mail/client/*.java src/mail/ui/*.java
)

start "Mail Server Dashboard" java -Dfile.encoding=UTF-8 -cp bin mail.server.MailServerGUI

