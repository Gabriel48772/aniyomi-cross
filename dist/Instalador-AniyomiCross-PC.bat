@echo off
chcp 65001 >nul
title Instalador Oficial de Aniyomi Cross para Windows PC
color 0B

echo =====================================================================
echo           ANIYOMI CROSS - INSTALADOR OFICIAL PARA WINDOWS PC
echo =====================================================================
echo.
echo Iniciando el asistente de instalacion inteligente...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\installer\setup.ps1"
if %errorlevel% neq 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
)

pause
