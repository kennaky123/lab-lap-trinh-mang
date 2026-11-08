@echo off
chcp 65001 > nul
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
echo =====================================================================
echo    KHá»I CHáº Y DEMO ÄĂNH GIĂ CHáº¤M ÄIá»‚M (RMI Äá»’NG Bá»˜ HĂ“A 2 SERVER)
echo    YĂªu cáº§u: Äá»“ng bá»™ dá»¯ liá»‡u 2 Server (Active-Active 2-Way Sync)
echo             MĂ´ phá»ng 2 Server vĂ  cĂ¡c Client (mĂ¡y thá»±c / mĂ¡y áº£o)
echo =====================================================================

if not exist bin mkdir bin
if not exist server1_data mkdir server1_data
if not exist server2_data mkdir server2_data

echo [1/5] Äang kiá»ƒm tra vĂ  biĂªn dá»‹ch mĂ£ nguá»“n Java...
javac --release 8 -encoding UTF-8 -cp "lib/*" -d bin src/rmi/common/*.java src/rmi/server/*.java src/rmi/client/*.java src/rmi/ui/*.java

echo [2/5] Khá»Ÿi Ä‘á»™ng RMI Server 1 (Cá»•ng 1099)...
start "RMI Server 1 (Port 1099)" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" rmi.server.RmiServerGUI 1 localhost 1100

echo [3/5] Khá»Ÿi Ä‘á»™ng RMI Server 2 (Cá»•ng 1100)...
start "RMI Server 2 (Port 1100)" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" rmi.server.RmiServerGUI 2 localhost 1099

echo Chá» 2 giĂ¢y Ä‘á»ƒ 2 Server báº¯t tay RMI Registry...
timeout /t 2 /nobreak > nul

echo [4/5] Khởi động Client A (Kết nối Server 1)...
start "RMI Client A (Server 1)" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" rmi.client.RmiClientGUI 1

echo [5/5] Khởi động Client B (Kết nối Server 2)...
start "RMI Client B (Server 2)" java -Dfile.encoding=UTF-8 -cp "bin;lib/*" rmi.client.RmiClientGUI 2

echo =====================================================================
echo    [HOĂ€N Táº¤T] 2 Server vĂ  2 Client Ä‘Ă£ Ä‘Æ°á»£c báº­t Ä‘á»“ng thá»i!
echo    - Thá»­ nghiá»‡m 1: Nháº­p dá»¯ liá»‡u trĂªn Client A -> Xem Server 2 & Client B
echo                    tá»± Ä‘á»™ng Ä‘á»“ng bá»™ hĂ³a tá»©c thĂ¬ qua RMI.
echo    - Thá»­ nghiá»‡m 2: Táº¯t Server 1 -> Client A tá»± Ä‘á»™ng Failover sang Server 2.
echo    - Thá»­ nghiá»‡m 3: Báº­t láº¡i Server 1 -> Tá»± Ä‘á»™ng Catch-up Full Sync!
echo =====================================================================

