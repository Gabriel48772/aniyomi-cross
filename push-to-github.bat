@echo off
setlocal enabledelayedexpansion
title Subir Aniyomi Cross a tu GitHub
set "DIR=%~dp0"
cd /d "%DIR%"

echo ===================================================================
echo     VINCULAR Y SUBIR PROYECTO A TU CUENTA DE GITHUB
echo ===================================================================
echo.
echo Para subir este proyecto a tu GitHub:
echo 1. Entra a tu cuenta en https://github.com/new y crea un repositorio nuevo
echo    (ejemplo: aniyomi-cross). Deja las casillas de README sin marcar.
echo 2. Pega aqui la URL HTTPS de tu repositorio de GitHub.
echo.
set /p "REPO_URL=Introduce la URL de tu repositorio GitHub (ej. https://github.com/tu-usuario/aniyomi-cross.git): "

if "%REPO_URL%"=="" (
    echo Error: No introdujiste ninguna URL.
    pause
    exit /b 1
)

echo.
echo Vinculando con %REPO_URL%...
"C:\Program Files\Git\cmd\git.exe" remote remove origin 2>nul
"C:\Program Files\Git\cmd\git.exe" remote add origin "%REPO_URL%"
"C:\Program Files\Git\cmd\git.exe" branch -M main

echo.
echo Subiendo todos los archivos a GitHub...
"C:\Program Files\Git\cmd\git.exe" push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================================
    echo   TODO EL PROYECTO SE HA SUBIDO CON EXITO A TU GITHUB!
    echo ===================================================================
    echo Cada vez que hagamos cambios, GitHub Actions compilara
    echo automaticamente las nuevas versiones y tu app se actualizara sola.
) else (
    echo.
    echo [AVISO] Si GitHub te pidio inicio de sesion, completa la autorizacion
    echo en el navegador o proporciona tu Token Personal de GitHub.
)

echo.
pause
