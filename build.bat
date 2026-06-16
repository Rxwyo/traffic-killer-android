@echo off
chcp 65001 >nul
echo ========================================
echo   流量杀手 Android 版 - 构建脚本
echo ========================================
echo.

:: 检查 Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [!] 未检测到 Java，正在安装 JDK 17...
    winget install --id Amazon.Corretto.17.JDK --accept-source-agreements --accept-package-agreements --silent
    if %errorlevel% neq 0 (
        echo [x] JDK 安装失败，请手动安装 JDK 17+
        pause
        exit /b 1
    )
    :: 刷新环境变量
    call refreshenv 2>nul || echo [*] 请重启终端后再次运行此脚本
    set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

java -version
if %errorlevel% neq 0 (
    echo [x] Java 不可用
    pause
    exit /b 1
)

:: 检查 Android SDK
if not exist "%LOCALAPPDATA%\Android\Sdk" (
    echo [*] 正在安装 Android SDK command-line tools...
    
    :: 创建 SDK 目录
    mkdir "%LOCALAPPDATA%\Android\Sdk" 2>nul
    
    :: 下载 command-line tools
    echo [*] 下载 Android command-line tools...
    powershell -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile '%TEMP%\cmdline-tools.zip'"
    if %errorlevel% neq 0 (
        echo [x] 下载失败，请检查网络
        pause
        exit /b 1
    )
    
    :: 解压
    powershell -Command "Expand-Archive -Path '%TEMP%\cmdline-tools.zip' -DestinationPath '%LOCALAPPDATA%\Android\Sdk\'"
    mkdir "%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest" 2>nul
    move "%LOCALAPPDATA%\Android\Sdk\cmdline-tools\cmdline-tools\*" "%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\" 2>nul
    
    :: 设置环境变量
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    set "PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%"
    
    :: 安装必要组件
    echo [*] 安装 platform-tools 和 build-tools...
    yes | sdkmanager --licenses 2>nul
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
)

set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo.
echo ========================================
echo   开始构建 APK...
echo ========================================

cd /d "%~dp0"

:: 使用 Gradle 构建
call gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo   ✓ 构建成功！
    echo   APK 位置: app\build\outputs\apk\debug\app-debug.apk
    echo ========================================
    copy "app\build\outputs\apk\debug\app-debug.apk" "..\流量杀手_Android.apk"
    echo   已复制到桌面: ..\流量杀手_Android.apk
) else (
    echo.
    echo [x] 构建失败
)

pause
