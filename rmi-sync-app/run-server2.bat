@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Đang khởi động RMI SERVER 2 (Cổng 1100)...
echo ===================================================

if not exist bin\rmi\server\RmiServerGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac --release 11 -encoding UTF-8 -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java
)

start "RMI Server 2 (Port 1100)" java -Dfile.encoding=UTF-8 -cp bin rmi.server.RmiServerGUI 2 localhost 1099

