@echo off
cd /d "%~dp0"
start "Mail Server" python MailServer.py
start "Mail Client Alice" python MailClient.py
start "Mail Client Bob" python MailClient.py
