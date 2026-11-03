@echo off
title Aniyomi Cross - Instalador en Android
set "DIR=%~dp0"
set "APK=%DIR%dist\AniyomiCross-Android.apk"

echo ======================================================
echo   Instalando Aniyomi Cross en tu telefono Android...
echo ======================================================
echo.

adb devices
echo.
echo Instalando APK (%APK%)...
adb install -r "%APK%"
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ======================================================
    echo  INSTALACION EXITOSA! Abre la app en tu telefono.
    echo ======================================================
) else (
    echo.
    echo [AVISO] Si adb no detecta tu telefono, puedes copiar el archivo:
    echo "%APK%"
    echo directamente a la memoria de tu telefono y tocarlo para instalarlo.
)
echo.
pause
