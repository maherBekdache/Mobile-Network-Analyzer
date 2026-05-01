@echo off
setlocal

set "TARGET=%LOCALAPPDATA%\MobileNetworkAnalyzerProfessor"
set "ARCHIVE=%~dp0ProfessorBundle.zip"

if exist "%TARGET%" rmdir /s /q "%TARGET%"
mkdir "%TARGET%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ARCHIVE%' -DestinationPath '%TARGET%' -Force"
if errorlevel 1 (
  echo Failed to extract the professor package.
  pause
  exit /b 1
)

start "" "%TARGET%\Launch-Mobile-Network-Analyzer.cmd"
exit /b 0
