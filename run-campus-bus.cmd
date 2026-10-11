@echo off
rem CAMPUS BUS launcher: checks Java and Maven, then starts the app from this folder (where db.properties lives).
rem It never contains passwords: the database settings come from db.properties (copy db.properties.example).
setlocal
cd /d "%~dp0"

where java >nul 2>&1 || (echo Java was not found. Install JDK 17 or newer and add it to PATH. & goto :fail)
rem full path: "call" with a quoted bare name ("mvn.cmd") silently runs nothing
set "CAMPUS_MVN="
for /f "delims=" %%m in ('where mvn.cmd 2^>nul') do if not defined CAMPUS_MVN set "CAMPUS_MVN=%%m"
if not defined CAMPUS_MVN if exist "%MAVEN_HOME%\bin\mvn.cmd" set "CAMPUS_MVN=%MAVEN_HOME%\bin\mvn.cmd"
if not defined CAMPUS_MVN (
  echo Maven was not found. Install Apache Maven 3.8+ and add its bin folder to PATH, or set MAVEN_HOME.
  goto :fail
)
if not exist db.properties (
  echo db.properties is missing. Copy db.properties.example to db.properties and fill in your MySQL user and password.
  goto :fail
)

echo Starting CAMPUS BUS ...
call "%CAMPUS_MVN%" -q compile exec:java
if errorlevel 1 (
  echo.
  echo CAMPUS BUS stopped with an error. Check that MySQL is running and that db.properties is correct.
  goto :fail
)
exit /b 0

:fail
echo.
pause
exit /b 1
