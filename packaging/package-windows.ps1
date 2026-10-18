# Builds the Windows release: target\dist\CAMPUS-BUS-1.0-windows-x64.zip
# The release contains its own Java runtime (made with jlink from a JDK), so users install neither Java nor Maven.
# No custom .exe is produced: the app starts with the runtime's signed javaw.exe, which Windows Smart App Control
# accepts, while an unsigned launcher .exe would be blocked on many Windows 11 PCs.
# Needs (developer only): a redistributable JDK 21+ (e.g. Eclipse Temurin) and Maven 3.8+.
param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$jlink = $null
foreach ($c in @($(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jlink.exe' })) +
               @(Get-ChildItem "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\*\bin\jlink.exe", 'C:\Program Files\Eclipse Adoptium\*\bin\jlink.exe' -ErrorAction SilentlyContinue | ForEach-Object FullName) +
               @((Get-Command jlink -ErrorAction SilentlyContinue).Source)) {
    if ($c -and (Test-Path $c)) { $jlink = $c; break }
}
if (-not $jlink) { Write-Host 'jlink was not found: install a JDK 21+ such as Eclipse Temurin, or set JAVA_HOME.'; exit 1 }
Write-Host "Using $jlink"

$mvnArgs = @('-B', 'clean', 'package'); if ($SkipTests) { $mvnArgs += '-DskipTests' }
& mvn.cmd @mvnArgs
if ($LASTEXITCODE -ne 0) { Write-Host 'Maven build failed.'; exit 1 }

$name = 'CAMPUS-BUS-1.0-windows-x64'
$dist = "target\dist\$name"
$app  = "$dist\CAMPUS BUS"
New-Item -ItemType Directory -Force "$app\app", "$dist\scripts" | Out-Null
Copy-Item 'target\college-bus-reservation-system-1.0.jar', 'target\lib\*.jar' "$app\app\"

$modules = 'java.base,java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,' +
           'java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,java.xml.crypto,jdk.accessibility,jdk.crypto.mscapi,' +
           'jdk.jsobject,jdk.localedata,jdk.net,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom,jdk.zipfs'
# JDK 21 keeps the elliptic-curve ciphers (needed for TLS to Gmail) in jdk.crypto.ec; from JDK 22 they are in java.base
$javaOfJlink = Join-Path (Split-Path $jlink) 'java.exe'
if ((Test-Path $javaOfJlink) -and (& $javaOfJlink --list-modules | Select-String -Pattern '^jdk\.crypto\.ec@' -Quiet)) { $modules += ',jdk.crypto.ec' }
& $jlink --add-modules $modules --include-locales 'en,en-IN' --strip-debug --no-header-files --no-man-pages --output "$app\runtime"
if ($LASTEXITCODE -ne 0) { Write-Host 'jlink failed.'; exit 1 }

Copy-Item setup-windows.cmd, run-campus-bus.cmd, reset-demo.cmd $dist
Copy-Item scripts\campus-bus.ps1 "$dist\scripts\"
Copy-Item packaging\README-WINDOWS.txt $dist
if (Get-ChildItem $dist -Recurse -Include db.properties, *.cnf, my.ini, demo-accounts.txt, *credentials*, *.pdf, *.sql.bak, *.dump) {
    Write-Host 'A settings, account or personal file ended up in the release. Stopping.'; exit 1 }
# standard zip: forward-slash entry names (Windows PowerShell 5 would write backslashes), top folder included
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zipPath = Join-Path (Resolve-Path 'target\dist').Path "$name.zip"
Remove-Item $zipPath -ErrorAction SilentlyContinue
$base = (Resolve-Path 'target\dist').Path
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    foreach ($f in Get-ChildItem $dist -Recurse -File) {
        $entry = $f.FullName.Substring($base.Length + 1) -replace '\\', '/'
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $entry, 'Optimal')
    }
} finally { $zip.Dispose() }
$size = [int]((Get-Item "target\dist\$name.zip").Length / 1MB)
Write-Host "Release: target\dist\$name.zip ($size MB)"
