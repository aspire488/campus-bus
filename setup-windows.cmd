@echo off
rem CAMPUS BUS one-time setup for Windows 10/11 (64-bit): a private MySQL for this user, the demo database and its settings.
rem Safe to run again: it never overwrites existing settings or data. This file contains no passwords.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\campus-bus.ps1" setup %*
set "CODE=%ERRORLEVEL%"
echo.
pause
exit /b %CODE%
