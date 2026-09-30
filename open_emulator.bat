@echo off
title Android Emulator - Pixel 9 Pro
echo ==============================================
echo       Launching Android Emulator Screen
echo ==============================================
echo.

:: Clean up stale locks if any exist
if exist "%USERPROFILE%\.android\avd\Pixel_9_Pro.avd\hardware-qemu.ini.lock" (
    rmdir /s /q "%USERPROFILE%\.android\avd\Pixel_9_Pro.avd\hardware-qemu.ini.lock" >nul 2>&1
)
if exist "%USERPROFILE%\.android\avd\Pixel_9_Pro.avd\multiinstance.lock" (
    del /f /q "%USERPROFILE%\.android\avd\Pixel_9_Pro.avd\multiinstance.lock" >nul 2>&1
)

echo Starting Pixel 9 Pro GUI window...
cd /d "C:\Users\sk800\AppData\Local\Android\Sdk\emulator"
start "" "emulator.exe" -avd Pixel_9_Pro

echo.
echo Emulator screen opened!
echo.
timeout /t 4
