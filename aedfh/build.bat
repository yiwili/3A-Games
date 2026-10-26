@echo off
chcp 65001 >nul
setlocal
call "%~dp0env.bat" || exit /b 1
echo [AEDFH] 使用 JDK: %JAVA_HOME%
call "%GRADLE_CMD%" -p "%~dp0." installDist
