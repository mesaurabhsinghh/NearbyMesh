@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat assembleDebug
copy /y app\build\outputs\apk\debug\app-debug.apk NearbyMesh.apk
