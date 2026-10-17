@echo off
title Build APK P.A.I.R.O
cd /d "%~dp0"

echo ============================================
echo  Membangun APK P.A.I.R.O
echo  Jendela ini akan tetap terbuka sampai selesai.
echo ============================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "Unblock-File -Path '.\build-on-windows.ps1' -ErrorAction SilentlyContinue"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\build-on-windows.ps1"

echo.
echo ============================================
echo  Selesai. Jejak lengkap ada di build-log.txt
echo ============================================
pause
