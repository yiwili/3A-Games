@echo off
chcp 65001 >nul
title Genshin LAN Lobby - port 2648
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] node.exe not found in PATH.
  echo Install Node.js from https://nodejs.org/ then run this file again.
  echo.
  pause
  exit /b 1
)

echo Starting LAN lobby on port 2648 ...
echo Keep this window open: closing it stops the server.
echo.
node duoren-server.js 2648 --open

echo.
echo Server stopped. Friends already in the lobby will lose the hall channel.
pause
