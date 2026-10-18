@echo off
rem Builds the Windows release (for the developer): target\dist\CAMPUS-BUS-1.1-windows-x64.zip, with Java included.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0packaging\package-windows.ps1" %*
exit /b %ERRORLEVEL%
