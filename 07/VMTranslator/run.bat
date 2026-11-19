@echo off
setlocal
if "%~1"=="" (
  echo Usage: run.bat ^<file.vm or directory^>
  exit /b 1
)
javac -encoding UTF-8 "%~dp0Parser.java" "%~dp0CodeWriter.java" "%~dp0VMTranslator.java"
if errorlevel 1 exit /b 1
java -cp "%~dp0." VMTranslator %*
