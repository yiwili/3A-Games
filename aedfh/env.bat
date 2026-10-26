@echo off
chcp 65001 >nul
rem 只影响当前脚本进程：不修改系统的 JAVA_HOME / PATH，其他 JDK 8 项目不受影响。
set "AEDFH_ROOT=%~dp0"
set "AEDFH_JDK="
for /d %%D in ("%AEDFH_ROOT%.jdk\jdk-21*") do set "AEDFH_JDK=%%~fD"
if not defined AEDFH_JDK (
  echo [AEDFH] 未找到项目内 JDK 21，请把 Temurin 21 解压到 .jdk\ 目录。 1>&2
  exit /b 1
)
set "JAVA_HOME=%AEDFH_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "GRADLE_CMD="
for /d %%D in ("%AEDFH_ROOT%.tools\gradle-*") do set "GRADLE_CMD=%%~fD\bin\gradle.bat"
if not defined GRADLE_CMD (
  echo [AEDFH] 未找到项目内 Gradle，请把 Gradle 发行版解压到 .tools\ 目录。 1>&2
  exit /b 1
)

rem 依赖缓存也留在项目内，避免碰全局 ~/.gradle 和其它项目的缓存
set "GRADLE_USER_HOME=%AEDFH_ROOT%.gradle-home"
exit /b 0
