@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Đang khởi động RMI SERVER 1 (Cổng 1099)...
echo ===================================================

if not exist bin\rmi\server\RmiServerGUI.class (
    echo [THÔNG BÁO] Đang biên dịch mã nguồn...
    javac -encoding UTF-8 -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java
)

start "RMI Server 1 (Port 1099)" java -Dfile.encoding=UTF-8 -cp bin rmi.server.RmiServerGUI 1 localhost 1100

