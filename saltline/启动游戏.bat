@echo off
chcp 65001 >nul
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel%==0 (
  start "" "http://127.0.0.1:8599/index.html"
  python -m http.server 8599 --bind 127.0.0.1
) else (
  start "" "%~dp0index.html"
)