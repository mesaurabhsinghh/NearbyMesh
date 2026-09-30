@echo off
title Install NearbyMesh & Launch on Emulator
echo ==============================================
echo     Installing NearbyMesh on Emulator
echo ==============================================
echo.
"C:\Users\sk800\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "c:\NearbyMesh\NearbyMesh.apk"
echo.
echo Launching NearbyMesh app on screen...
"C:\Users\sk800\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell monkey -p com.nearbymesh.app -c android.intent.category.LAUNCHER 1
echo.
echo App opened successfully!
pause
