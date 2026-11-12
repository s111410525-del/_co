@echo off
setlocal
if "%~1"=="" (
  echo Usage: run.bat ^<file.asm^>
  exit /b 1
)
javac -encoding UTF-8 "%~dp0Assembler.java"
if errorlevel 1 exit /b 1
java -cp "%~dp0." Assembler %*
