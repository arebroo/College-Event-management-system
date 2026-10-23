@echo off
REM Windows: compile and start the server  ->  http://localhost:8080
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 (
  echo Compilation failed. Is the JDK installed? Try: javac -version
  pause
  exit /b 1
)
del sources.txt
java -cp out Main %*
pause
