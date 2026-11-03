@echo off
setlocal
title Conectar y Subir Aniyomi Cross a tu GitHub
set "DIR=%~dp0"
cd /d "%DIR%"

echo =====================================================================
echo          CONECTAR Y SUBIR AUTOMATICAMENTE A TU GITHUB
echo =====================================================================
echo.
echo 1. Se abrira una ventana en tu navegador para autorizar con GitHub.
echo 2. El codigo de seguridad de un solo uso se copiara automaticamente
echo    en tu portapapeles. Solo dale 'Pegar' (Ctrl+V) y 'Authorize'.
echo.
pause

echo Iniciando autenticacion segura en GitHub...
"C:\Program Files\GitHub CLI\gh.exe" auth login --hostname github.com --git-protocol https --web --clipboard

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] No se pudo completar el inicio de sesion.
    pause
    exit /b 1
)

echo.
echo =====================================================================
echo  Sesion iniciada! Creando repositorio 'aniyomi-cross' en tu cuenta...
echo =====================================================================
echo.

"C:\Program Files\GitHub CLI\gh.exe" repo create aniyomi-cross --public --source=. --remote=origin --push

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =====================================================================
    echo   TODO EL PROYECTO SE HA CREADO Y SUBIDO A TU GITHUB CON EXITO!
    echo =====================================================================
    echo Tus archivos ya estan seguros en linea y GitHub Actions
    echo se encargara de compilar las actualizaciones automaticamente.
) else (
    echo.
    echo [AVISO] Si el repositorio ya existia, intentando subir los cambios...
    "C:\Program Files\Git\cmd\git.exe" push -u origin main
)

echo.
pause
