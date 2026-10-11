CAMPUS BUS - bus seat reservation app (Windows 10 / 11, 64-bit)
=================================================================

Java is included. You do NOT need to install Java or Maven.

FIRST TIME (once per computer, needs internet, about 5 minutes)
  1. Unzip this folder anywhere, e.g. your Desktop. Keep all files together.
  2. Double-click  setup-windows.cmd
       - downloads MySQL 8.4 (about 280 MB) from mysql.com and checks it
       - sets up a private database for your Windows user only (no admin rights needed,
         nothing else on the computer is changed, other computers cannot connect)
       - creates the demo database with fictional students, routes and buses
       - opens demo-accounts.txt with the demo sign-in names and passwords
         (saved in %APPDATA%\CampusBus - keep it private)

EVERY TIME
  Double-click  run-campus-bus.cmd
  It starts the database, opens CAMPUS BUS, and stops the database when you close the app.

START THE DEMO AGAIN WITH FRESH DATA
  Double-click  reset-demo.cmd  and type DELETE.
  This deletes only the demo database (all demo bookings) and creates it again with NEW passwords.

IF SOMETHING GOES WRONG
  - "Visual C++ runtime is missing": install https://aka.ms/vs/17/release/vc_redist.x64.exe, run setup again.
  - "Download failed": check the internet, or download
      https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip
    yourself and put the zip next to setup-windows.cmd, then run setup again.
  - "Port 3310 is used by another program": restart Windows and try again.
  - Windows SmartScreen ("Windows protected your PC") on the .cmd files: click "More info" > "Run anyway"
    (the app itself runs on the signed Java included in this folder).
  - The app says it can't reach the database: close it and start it again with run-campus-bus.cmd.

REMOVE EVERYTHING
  Delete this folder, %LOCALAPPDATA%\CampusBus and %APPDATA%\CampusBus.

Needs: Windows 10 or 11, 64-bit, about 1 GB free disk space, 4 GB RAM or more.
