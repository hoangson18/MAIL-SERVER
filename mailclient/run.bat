@echo off
cd /d "%~dp0"
java %* -cp out mailclient.MailClient
