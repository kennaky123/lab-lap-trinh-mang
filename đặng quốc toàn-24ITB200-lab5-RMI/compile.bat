@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ===================================================
echo [1/2] Äang biĂªn dá»‹ch mĂ£ nguá»“n Java RMI Sync App...
echo ===================================================

if not exist bin mkdir bin
if not exist server1_data mkdir server1_data
if not exist server2_data mkdir server2_data

javac --release 8 -encoding UTF-8 -cp "lib/*" -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] BiĂªn dá»‹ch thĂ nh cĂ´ng! CĂ¡c tá»‡p .class Ä‘Ă£ lÆ°u trong thÆ° má»¥c bin/
) else (
    echo [Lá»–I] QuĂ¡ trĂ¬nh biĂªn dá»‹ch tháº¥t báº¡i. Vui lĂ²ng kiá»ƒm tra cĂ i Ä‘áº·t JDK.
)

pause

