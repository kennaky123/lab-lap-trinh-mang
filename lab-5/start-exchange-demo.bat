@echo off
cd /d "%~dp0"
start "ExchangeRate Server" python ExchangeRateServer.py
start "ExchangeRate Table" python ExchangeRateTable.py
