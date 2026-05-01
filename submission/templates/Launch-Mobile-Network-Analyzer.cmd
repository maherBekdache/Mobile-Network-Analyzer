@echo off
setlocal
cd /d "%~dp0"
title Mobile Network Analyzer Professor Package

set "NODE_RUNTIME=%~dp0runtime\node-win-x64\node.exe"
set "APP_ENTRY=%~dp0app\backend\submission-server.mjs"
set "DB_FILE=%~dp0app\backend\data\network-analyzer.sqlite"

if not exist "%NODE_RUNTIME%" (
  echo Portable Node runtime was not found at:
  echo   %NODE_RUNTIME%
  pause
  exit /b 1
)

if not exist "%APP_ENTRY%" (
  echo Submission server entry file was not found at:
  echo   %APP_ENTRY%
  pause
  exit /b 1
)

set "DB_FILE=%DB_FILE%"
"%NODE_RUNTIME%" "%APP_ENTRY%"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo The server has stopped. Press any key to close this window.
pause >nul
exit /b %EXIT_CODE%
