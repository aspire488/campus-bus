@echo off
rem Deletes ONLY the demo database campus_bus_demo (asks you to type DELETE first) and sets it up again with new passwords.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\campus-bus.ps1" reset %*
set "CODE=%ERRORLEVEL%"
echo.
pause
exit /b %CODE%
