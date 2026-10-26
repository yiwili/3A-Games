@echo off
chcp 65001 >nul
setlocal
call "%~dp0env.bat" || exit /b 1
echo [AEDFH] 使用 JDK: %JAVA_HOME%
if "%~1"=="" (
  call "%GRADLE_CMD%" -p "%~dp0." run
) else (
  call "%GRADLE_CMD%" -p "%~dp0." run --args="%*"
)
