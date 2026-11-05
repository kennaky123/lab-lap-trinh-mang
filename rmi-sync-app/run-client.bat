@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo Äang má»Ÿ á»¨NG Dá»¤NG RMI CLIENT (MĂ¡y áº£o / MĂ¡y tráº¡m)...
echo ===================================================

if not exist bin\rmi\client\RmiClientGUI.class (
    echo [THĂ”NG BĂO] Äang biĂªn dá»‹ch mĂ£ nguá»“n...
    javac --release 8 -encoding UTF-8 -cp "lib/*" -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java
)

start "RMI Client" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" rmi.client.RmiClientGUI

