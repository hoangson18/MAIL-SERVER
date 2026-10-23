@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 (
    echo [LOI] Build that bai, khong the khoi dong client.
    pause
    exit /b 1
)
echo.
echo Khoi dong Mail Client...
java -cp out mailclient.MailClient
