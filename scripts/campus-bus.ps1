# CAMPUS BUS for Windows 10/11 (64-bit): one-time setup, launch, and demo reset.
# Called by setup-windows.cmd, run-campus-bus.cmd and reset-demo.cmd. Works from any folder, also with spaces in the path.
# It never prints a password and never logs in to MySQL as root without one.
#
#   setup : private MySQL server for this Windows user (no admin rights, port 3310, only this computer can connect),
#           the demo database campus_bus_demo with fictional data, and the settings file %APPDATA%\CampusBus\db.properties
#   run   : starts that MySQL if needed, opens the app, stops MySQL again when the app closes
#   reset : deletes ONLY the demo database (after you type DELETE) and sets it up again with new passwords
param([ValidateSet('setup', 'run', 'reset')][string]$Action = 'run', [switch]$Force)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'      # downloads are much faster without the progress bar

$Root  = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)   # folder with the .cmd files
$Data  = Join-Path $env:LOCALAPPDATA 'CampusBus'      # MySQL program and data
$Cfg   = Join-Path $env:APPDATA 'CampusBus'           # db.properties, demo-accounts.txt, mysql-root.cnf (private)
$Port  = 3310
$MySqlVersion = '8.4.11'
$MySqlZip     = "mysql-$MySqlVersion-winx64.zip"
$MySqlUrls    = @("https://cdn.mysql.com/Downloads/MySQL-8.4/$MySqlZip", "https://dev.mysql.com/get/Downloads/MySQL-8.4/$MySqlZip")
$MySqlSha256  = 'a492371d687d2bab088b0062581144a0044b8964baefdf4faa579292b423d25c'
$Mysqld   = Join-Path $Data 'mysql\bin\mysqld.exe'
$Ini      = Join-Path $Data 'my.ini'
$DataDir  = Join-Path $Data 'data'
$RootCnf  = Join-Path $Cfg 'mysql-root.cnf'
$Settings = Join-Path $Cfg 'db.properties'
$AppDir   = Join-Path $Root 'CAMPUS BUS'                         # packaged release: its own Java runtime + the app's jars
$Javaw    = Join-Path $AppDir 'runtime\bin\javaw.exe'              # signed Java launchers (no unsigned .exe of our own)
$Java     = Join-Path $AppDir 'runtime\bin\java.exe'
$ClassPath = Join-Path $AppDir 'app\*'
$Source   = Test-Path (Join-Path $Root 'pom.xml')                # running from the source code instead

function Say($text) { Write-Host $text }
function Fail($text) { Write-Host ''; Write-Host "PROBLEM: $text" -ForegroundColor Red; exit 1 }

function New-Secret {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

function Protect-Folder($path) {         # only the current Windows user can read it
    $sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $path /inheritance:r /grant:r "*${sid}:(OI)(CI)F" /Q | Out-Null     # the folder itself
    if (Get-ChildItem $path -Force) { & icacls.exe "$path\*" /reset /T /Q | Out-Null }  # files inside inherit exactly that
}

# MySQL takes paths in the ANSI code page: a user folder whose name is outside it (e.g. non-Latin letters) is given by its
# 8.3 short name instead, which is plain ASCII. Paths that are already ASCII are used as they are.
function MySql-Path($folder) {
    if ($folder -notmatch '[^\x00-\x7F]') { return $folder }
    try {
        $short = (New-Object -ComObject Scripting.FileSystemObject).GetFolder($folder).ShortPath
        if ($short -notmatch '[^\x00-\x7F]') { return $short }
    } catch { }
    return $folder
}

function Test-Port {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $ok = $c.ConnectAsync('127.0.0.1', $Port).Wait(700); return ($ok -and $c.Connected) } catch { return $false } finally { $c.Close() }
}

function Test-OurMySql {
    @(Get-Process mysqld -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $Mysqld }).Count -gt 0
}

function Start-MySql([string]$extra = '') {
    if (Test-Port) {
        if (Test-OurMySql) { return $false }
        Fail "Port $Port is used by another program. Close it, or restart Windows, then try again."
    }
    $log = Join-Path $Data 'logs\mysql-start.log'
    $argLine = "--defaults-file=`"$(Join-Path (MySql-Path $Data) 'my.ini')`" $extra"
    Start-Process -FilePath $Mysqld -ArgumentList $argLine -WindowStyle Hidden -RedirectStandardError $log | Out-Null
    for ($i = 0; $i -lt 60; $i++) { if (Test-Port) { return $true }; Start-Sleep -Milliseconds 500 }
    Fail "The database server did not start. Details: $log"
}

function Stop-MySql {
    $admin = Join-Path $Data 'mysql\bin\mysqladmin.exe'
    # PowerShell 5.1 turns any stderr line of a program into a terminating error under 'Stop': a server that is already
    # down must not abort the shutdown (or the 'finally' it runs in)
    $ErrorActionPreference = 'Continue'
    try { & $admin "--defaults-extra-file=$(Join-Path (MySql-Path $Cfg) 'mysql-root.cnf')" --host=127.0.0.1 "--port=$Port" shutdown 2>&1 | Out-Null } catch { }
    for ($i = 0; $i -lt 40 -and (Test-OurMySql); $i++) { Start-Sleep -Milliseconds 500 }
    if (Test-OurMySql) { Say "Note: the private database server is still running; it stops when you log off or restart Windows." }
}

function Read-Settings($file) {
    $map = @{}
    foreach ($line in Get-Content $file) { if ($line -match '^\s*([\w.]+)\s*=\s*(.*)$') { $map[$matches[1]] = $matches[2].Trim() } }
    $map
}

function Check-Windows {
    if (-not [Environment]::Is64BitOperatingSystem) { Fail 'CAMPUS BUS needs 64-bit Windows 10 or 11.' }
    if ([Environment]::OSVersion.Version.Major -lt 10) { Fail 'CAMPUS BUS needs Windows 10 or 11.' }
}

function Check-VcRuntime {             # MySQL needs the Microsoft Visual C++ runtime (most PCs already have it)
    $sys = Join-Path $env:WINDIR 'System32'
    foreach ($dll in 'vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll') {
        if (-not (Test-Path (Join-Path $sys $dll))) {
            Fail ("The Microsoft Visual C++ runtime is missing ($dll). Install it, then run setup again:`n" +
                  "  https://aka.ms/vs/17/release/vc_redist.x64.exe   (or: winget install Microsoft.VCRedist.2015+.x64)")
        }
    }
}

function Check-SourceTools {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail 'Java was not found. Install JDK 17 or newer (or use the packaged release, which includes Java).' }
    if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) { Fail 'Maven was not found. Install Apache Maven 3.8+ and add its bin folder to PATH (or use the packaged release).' }
}

function Install-MySql {
    if (Test-Path $Mysqld) { return }
    $zip = $null
    foreach ($c in @($env:CAMPUS_BUS_MYSQL_ZIP, (Join-Path $Root $MySqlZip), (Join-Path $Data "download\$MySqlZip"))) {
        if ($c -and (Test-Path $c)) { $zip = $c; break }
    }
    if (-not $zip) {
        $zip = Join-Path $Data "download\$MySqlZip"
        New-Item -ItemType Directory -Force (Split-Path $zip) | Out-Null
        Say "Downloading MySQL $MySqlVersion (about 280 MB, one time only) ..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $ok = $false; $why = ''
        foreach ($url in $MySqlUrls) {       # the checksum below proves it is the right file, whichever mirror answers
            try { Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -UserAgent 'curl/8.0'; $ok = $true; break }
            catch { $why = $_.Exception.Message; Remove-Item $zip -ErrorAction SilentlyContinue }
        }
        if (-not $ok) { Fail "Download failed: $why`nCheck the internet connection, or download $($MySqlUrls[0]) yourself and put the zip next to setup-windows.cmd." }
    }
    Say 'Checking the download ...'
    if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $MySqlSha256) { Fail "$zip is damaged or not the expected MySQL $MySqlVersion file. Delete it and run setup again." }
    Say 'Unpacking MySQL (skips debug files) ...'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    # unpacked into mysql.tmp and renamed only when complete: an interrupted unzip never looks like an installed MySQL
    $final = Join-Path $Data 'mysql'
    $target = Join-Path $Data 'mysql.tmp'
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
    try {
        foreach ($e in $archive.Entries) {
            $rel = $e.FullName.Substring($e.FullName.IndexOf('/') + 1)
            if (-not $rel -or $rel.EndsWith('/') -or $rel -match '\.pdb$|^lib/mecab/|^include/|^docs/|^lib/[^/]+\.lib$') { continue }
            $out = Join-Path $target ($rel -replace '/', '\')
            New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $out, $true)
        }
    } finally { $archive.Dispose() }
    if (Test-Path $final) { Remove-Item $final -Recurse -Force }     # an older half-unpacked folder (no mysqld.exe in it)
    Rename-Item $target $final
}

function Initialize-MySql {
    if (Test-Path $DataDir) {
        if (-not (Test-Path $RootCnf)) { Fail "A CAMPUS BUS database exists in $DataDir but its settings are missing. To start over, delete $Data and $Cfg and run setup again." }
        if (Test-Path $Settings) { return }                       # finished earlier
        # no settings file: either an earlier setup stopped before writing it (the demo database has no tables yet: safe to
        # start over), or someone deleted the file after a finished setup (demo tables exist: never delete them unasked)
        # anything we can't read, any database besides MySQL's own and the demo, or demo tables all count as data
        try {
            $demoDir = Join-Path $DataDir 'campus_bus_demo'
            $tables = if (Test-Path $demoDir) { @(Get-ChildItem $demoDir -Filter '*.ibd' -ErrorAction Stop) } else { @() }
            $others = @(Get-ChildItem $DataDir -Directory -ErrorAction Stop | Where-Object { $_.Name -notmatch '^(#.*|mysql|sys|performance_schema|campus_bus_demo)$' })
        } catch { $tables = @('unreadable') ; $others = @() }
        if ($tables.Count -gt 0 -or $others.Count -gt 0) {
            Fail "The database folder $DataDir holds data, but its settings file $Settings is missing. Nothing was deleted. Put the settings file back, or, to start over completely, delete the folders $Data and $Cfg yourself and run setup again."
        }
        Say 'An earlier setup did not finish. Starting the database server files again ...'
        Get-Process mysqld -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $Mysqld } | Stop-Process -Force
        Start-Sleep -Seconds 1
        Remove-Item $DataDir -Recurse -Force
        Remove-Item $RootCnf -Force
    }
    if (Test-Port) { Fail "Port $Port is used by another program. Close it, or restart Windows, then run setup again." }
    New-Item -ItemType Directory -Force (Join-Path $Data 'logs'), (Join-Path $Data 'private'), $Cfg | Out-Null
    Protect-Folder $Cfg
    Protect-Folder (Join-Path $Data 'private')
    $slash = { param($p) $p -replace '\\', '/' }
    $iniLines = @(
        '[mysqld]',
        "basedir=`"$(& $slash (Join-Path (MySql-Path $Data) 'mysql'))`"",
        "datadir=`"$(& $slash (Join-Path (MySql-Path $Data) 'data'))`"",
        "port=$Port",
        'bind-address=127.0.0.1',
        'mysqlx=OFF',
        'performance_schema=OFF',
        'innodb_buffer_pool_size=64M',
        "log-error=`"$(& $slash (Join-Path (MySql-Path $Data) 'logs\mysql.err'))`""
    )
    # MySQL reads my.ini in the Windows ANSI code page (tested: UTF-8 and ASCII both break an accented user name)
    $iniLines | Set-Content -Path $Ini -Encoding Default
    Say 'Creating the database server files ...'
    $p = Start-Process -FilePath $Mysqld -ArgumentList "--defaults-file=`"$(Join-Path (MySql-Path $Data) 'my.ini')`" --initialize-insecure" -WindowStyle Hidden -Wait -PassThru
    if ($p.ExitCode -ne 0) { Fail "MySQL could not initialise. Details: $(Join-Path $Data 'logs\mysql.err')" }

    # the first start sets the root password and creates the app user; we never connect as root without a password
    $rootPw = New-Secret; $appPw = New-Secret
    $init = Join-Path (MySql-Path (Join-Path $Data 'private')) 'init.sql'      # MySQL reads this path too: ASCII-safe like my.ini
    @(
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPw';",
        'CREATE DATABASE IF NOT EXISTS campus_bus_demo;',
        "CREATE USER IF NOT EXISTS 'campus_bus'@'localhost' IDENTIFIED BY '$appPw';",
        "CREATE USER IF NOT EXISTS 'campus_bus'@'127.0.0.1' IDENTIFIED BY '$appPw';",
        "GRANT ALL ON campus_bus_demo.* TO 'campus_bus'@'localhost';",
        "GRANT ALL ON campus_bus_demo.* TO 'campus_bus'@'127.0.0.1';"
    ) | Set-Content -Path $init -Encoding ASCII
    @('[client]', 'user=root', "password=$rootPw") | Set-Content -Path $RootCnf -Encoding ASCII
    try {
        Start-MySql "--init-file=`"$($init -replace '\\', '/')`"" | Out-Null
        Stop-MySql
    } finally { Remove-Item $init -Force -ErrorAction SilentlyContinue }
    if (-not (Test-Path $Settings)) {
        @('# Written by setup-windows.cmd for the CAMPUS BUS demo. Private: do not share.',
          "db.host=localhost:$Port", 'db.name=campus_bus_demo', 'db.user=campus_bus', "db.password=$appPw") | Set-Content -Path $Settings -Encoding ASCII
    }
}

function Invoke-DemoSetup {
    Say 'Preparing the demo database ...'
    # always the demo settings, even if a project db.properties points somewhere else (passed privately, never printed)
    $s = Read-Settings $Settings
    $env:CAMPUS_BUS_DB_HOST = $s['db.host']; $env:CAMPUS_BUS_DB_NAME = $s['db.name']
    $env:CAMPUS_BUS_DB_USER = $s['db.user']; $env:CAMPUS_BUS_DB_PASSWORD = $s['db.password']
    try {
        if ($Source) {
            Push-Location $Root
            try { & mvn.cmd -q compile exec:java "-Dexec.args=--setup-demo" } finally { Pop-Location }
        } else {
            & $Java -cp $ClassPath com.collegebus.Main --setup-demo
        }
        $code = $LASTEXITCODE
    } finally {
        foreach ($k in 'HOST', 'NAME', 'USER', 'PASSWORD') { Remove-Item "Env:CAMPUS_BUS_DB_$k" -ErrorAction SilentlyContinue }
    }
    if ($code -ne 0) { Fail 'The demo database could not be prepared (see the message above).' }
}

function Settings-InUse {          # same order as the app: project folder first, then the user's settings folder
    $project = Join-Path $Root 'db.properties'
    if ($Source -and (Test-Path $project)) { return $project }
    if (Test-Path $Settings) { return $Settings }
    return $null
}

# ---------------------------------------------------------------------------------------------------------------
Check-Windows
if (-not $Source -and -not (Test-Path $Javaw)) { Fail "CAMPUS BUS was not found next to this script ($Root). Unzip the whole release folder and try again." }

switch ($Action) {
    'setup' {
        Check-VcRuntime
        if ($Source) { Check-SourceTools }
        if ((Test-Path $Settings) -and -not (Test-Path $RootCnf)) {
            Fail "$Settings already exists and was not made by this setup. It was left unchanged. Use those settings with run-campus-bus.cmd, or rename the file to use the demo setup."
        }
        Install-MySql
        Initialize-MySql
        $started = Start-MySql
        try { Invoke-DemoSetup } finally { if ($started) { Stop-MySql } }
        Protect-Folder $Cfg
        Say ''
        Say 'Setup finished. Start the app with run-campus-bus.cmd.'
        Say "Your demo sign-in names and passwords are in: $(Join-Path $Cfg 'demo-accounts.txt')"
        if (-not $Force) { Start-Process notepad.exe -ArgumentList "`"$(Join-Path $Cfg 'demo-accounts.txt')`"" }
    }
    'run' {
        if ($Source) { Check-SourceTools }
        $file = Settings-InUse
        if (-not $file) { Fail 'No database settings yet. Run setup-windows.cmd once (or create db.properties from db.properties.example).' }
        $usesOurs = (Test-Path $Mysqld) -and ((Read-Settings $file)['db.host'] -eq "localhost:$Port")
        $started = $false
        if ($usesOurs) { $started = Start-MySql }
        try {
            Say 'Starting CAMPUS BUS ...'
            if ($Source) {
                Push-Location $Root
                try { & mvn.cmd -q compile exec:java; $code = $LASTEXITCODE } finally { Pop-Location }
            } else {
                $code = (Start-Process -FilePath $Javaw -ArgumentList "-cp `"$ClassPath`" com.collegebus.Main" -WorkingDirectory $AppDir -Wait -PassThru).ExitCode
            }
            if ($code -ne 0) { Fail "CAMPUS BUS stopped with an error (code $code). Check that the database is running and the settings are correct." }
        } finally { if ($started) { Stop-MySql } }
    }
    'reset' {
        if (-not (Test-Path $RootCnf)) { Fail 'There is no demo setup to reset on this computer. Run setup-windows.cmd first.' }
        Say 'This deletes the demo database campus_bus_demo: every booking and account made during demos is lost.'
        Say 'Nothing else is touched. Afterwards the demo starts fresh with NEW passwords.'
        if (-not $Force) {
            $answer = Read-Host 'Type DELETE to continue'
            if ("$answer".Trim() -cne 'DELETE') { Say 'Cancelled. Nothing was changed.'; exit 0 }
        }
        $started = Start-MySql
        try {
            & (Join-Path $Data 'mysql\bin\mysql.exe') "--defaults-extra-file=$(Join-Path (MySql-Path $Cfg) 'mysql-root.cnf')" --host=127.0.0.1 "--port=$Port" -e 'DROP DATABASE IF EXISTS campus_bus_demo'
            if ($LASTEXITCODE -ne 0) { Fail 'The demo database could not be deleted.' }
            Invoke-DemoSetup
        } finally { if ($started) { Stop-MySql } }
        Say "Demo reset. New passwords: $(Join-Path $Cfg 'demo-accounts.txt')"
    }
}
exit 0
