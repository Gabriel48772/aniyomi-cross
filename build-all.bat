@echo off
title Aniyomi Cross - Compilador Completo
set "DIR=%~dp0"
cd /d "%DIR%"

echo Compilando APK para Android y version ejecutable para PC...
call gradlew.bat :desktop:fatJar :app:assembleDebug

if %ERRORLEVEL% EQU 0 (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "dist\AniyomiCross-Android.apk"
    copy /y "desktop\build\libs\desktop-all.jar" "dist\AniyomiCross-PC.jar"
    echo.
    echo ==============================================================
    echo  COMPILACION COMPLETADA CON EXITO!
    echo  Archivos listos en la carpeta 'dist\':
    echo   - Android: dist\AniyomiCross-Android.apk
    echo   - PC:      dist\AniyomiCross-PC.jar (o ejecuta run-pc.bat)
    echo ==============================================================
) else (
    echo Error durante la compilacion.
)
pause
