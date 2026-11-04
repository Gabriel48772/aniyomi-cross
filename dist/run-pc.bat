@echo off
setlocal
title Aniyomi Cross - PC Desktop

cd /d "%~dp0"

set "JAR_PATH=%~dp0AniyomiCross-PC.jar"
if not exist "%JAR_PATH%" set "JAR_PATH=%~dp0dist\AniyomiCross-PC.jar"

set "JAVA_EXE="

if exist "%USERPROFILE%\.jdks\jdk-17.0.20.1+1\bin\javaw.exe" (
    set "JAVA_EXE=%USERPROFILE%\.jdks\jdk-17.0.20.1+1\bin\javaw.exe"
) else if exist "C:\Users\equipo\.jdks\jdk-17.0.20.1+1\bin\javaw.exe" (
    set "JAVA_EXE=C:\Users\equipo\.jdks\jdk-17.0.20.1+1\bin\javaw.exe"
) else (
    for /f "tokens=*" %%i in ('where javaw 2^>nul') do (
        set "JAVA_EXE=%%i"
    )
)

if defined JAVA_EXE (
    start "" "%JAVA_EXE%" -jar "%JAR_PATH%"
) else (
    echo [ERROR] No se encontro Java 17 en el sistema.
    pause
)
