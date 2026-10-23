@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 (
    echo [LỖIL Build thất bại, không thể khởi động server.
    pause
    exit /b 1
)
echo.
echo Khoi dong Mail Server...
java -Ddb.host=localhost\SQLEXPRESS -Ddb.name=MailDB -cp "out;lib/*" mailserver.MailServer
