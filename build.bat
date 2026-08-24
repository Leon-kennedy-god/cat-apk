@echo off
rem 喵喵助手一键构建脚本
rem 前置条件：本机已安装 JDK 17+，且 local.properties 中 sdk.dir 指向 Android SDK
cd /d %~dp0
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo 构建失败，请检查：JDK 是否安装、local.properties 的 sdk.dir 是否正确
    pause
    exit /b 1
)
echo.
echo 构建成功！APK 位于：app\build\outputs\apk\debug\app-debug.apk
pause
