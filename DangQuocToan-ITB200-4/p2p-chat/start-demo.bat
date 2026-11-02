@echo off
cd /d "%~dp0"
start "P2P Directory" python server.py
start "P2P Alice" python client.py
start "P2P Bob" python client.py
