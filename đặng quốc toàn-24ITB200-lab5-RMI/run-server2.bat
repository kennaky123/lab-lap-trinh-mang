@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Äang khá»Ÿi Ä‘á»™ng RMI SERVER 2 (Cá»•ng 1100)...
echo ===================================================

if not exist bin\rmi\server\RmiServerGUI.class (
    echo [THĂ”NG BĂO] Äang biĂªn dá»‹ch mĂ£ nguá»“n...
    javac --release 8 -encoding UTF-8 -cp "lib/*" -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java
)

start "RMI Server 2 (Port 1100)" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" rmi.server.RmiServerGUI 2 localhost 1099

