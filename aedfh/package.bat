@echo off
chcp 65001 >nul
setlocal
call "%~dp0env.bat" || exit /b 1
echo [AEDFH] 使用 JDK: %JAVA_HOME%

call "%GRADLE_CMD%" -p "%~dp0." installDist || exit /b 1

rem 打包自检：主 jar 必须含有开放世界的类，否则打出来的 exe 是旧版本
"%JAVA_HOME%\bin\jar.exe" tf "%~dp0build\install\aedfh\lib\aedfh-0.1.0.jar" > "%~dp0build\jar-contents.txt" || exit /b 1
for %%C in (com/aedfh/WorldApp.class com/aedfh/WorldBoss.class com/aedfh/WorldProps.class com/aedfh/SaveData.class com/aedfh/CloudMount.class com/aedfh/WorldCollision.class com/aedfh/Gltf.class com/aedfh/ActorRig.class) do (
  findstr /L /C:"%%C" "%~dp0build\jar-contents.txt" >nul || (
    echo [AEDFH] 打包自检失败: jar 缺少 %%C
    exit /b 1
  )
)
echo [AEDFH] 打包自检通过: 开放世界类已就位

if exist "%~dp0dist" rmdir /s /q "%~dp0dist"

"%JAVA_HOME%\bin\jpackage.exe" ^
  --type app-image ^
  --name EldenMonkey ^
  --app-version 0.1.0 ^
  --input "%~dp0build\install\aedfh\lib" ^
  --main-jar "aedfh-0.1.0.jar" ^
  --main-class com.aedfh.Main ^
  --dest "%~dp0dist" ^
  --add-modules java.base,java.desktop,java.logging,java.naming,jdk.unsupported,jdk.zipfs ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --win-console || exit /b 1

echo [AEDFH] 打包完成: %~dp0dist\EldenMonkey\EldenMonkey.exe

rem 启动自检: 必须真的能跑起来。离屏模式不弹窗口。
set "PKG=%~dp0dist\EldenMonkey\EldenMonkey.exe"
if not exist "%PKG%" (
  echo [AEDFH] 启动自检失败: 找不到 exe
  exit /b 1
)
echo [AEDFH] 启动自检: 运行打包后的 exe
"%PKG%" --worldtest > "%~dp0build\pkgselftest.log" 2>&1
findstr /C:"[WORLDTEST] PASS frames" "%~dp0build\pkgselftest.log" >nul || (
  echo [AEDFH] 启动自检失败: 打包后的 exe 跑不起来
  type "%~dp0build\pkgselftest.log"
  exit /b 1
)
echo [AEDFH] 启动自检通过
if exist "%~dp0build\worldtest-saves" rmdir /s /q "%~dp0build\worldtest-saves"
del /q "%~dp0build\pkgselftest.log"
