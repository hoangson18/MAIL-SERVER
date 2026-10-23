@echo off
chcp 65001 > nul
cd /d "%~dp0"
if not exist out mkdir out
(for /r src %%f in (*.java) do @echo "%%f") > sources.txt
javac -encoding UTF-8 -d out @sources.txt && echo Build OK
