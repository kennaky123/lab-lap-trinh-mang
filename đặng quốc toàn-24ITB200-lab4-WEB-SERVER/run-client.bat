@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Đang mở TRÌNH DUYỆT WEB (Chrome / Cốc Cốc Swing)...
echo ===================================================

if not exist bin\web\client\WebBrowserGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac --release 11 -encoding UTF-8 -d bin src/web/common/*.java src/web/server/*.java src/web/client/*.java src/web/ui/*.java
)

start "Chrome / Coc Coc Web Browser" java -Dfile.encoding=UTF-8 -cp bin web.client.WebBrowserGUI

