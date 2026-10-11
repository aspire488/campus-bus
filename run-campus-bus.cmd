@echo off
rem CAMPUS BUS launcher: starts the database made by setup-windows.cmd (if used), opens the app, and stops the database afterwards.
rem Works from any folder. This file contains no passwords.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\campus-bus.ps1" run
if errorlevel 1 (
  echo.
  pause
  exit /b 1
)
