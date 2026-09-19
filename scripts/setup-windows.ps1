# One-time local setup for Basecamp on Windows (idempotent, safe to re-run).
# Run from the repo root in PowerShell:  powershell -ExecutionPolicy Bypass -File scripts\setup-windows.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
function Install-Winget($id) {
    Write-Host "==> winget install $id"
    winget install --id $id -e --accept-package-agreements --accept-source-agreements --silent
}
function Get-Jdk21Home {
    Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -like 'jdk-21*' | Select-Object -First 1 -ExpandProperty FullName
}

# --- JDK 21 ---------------------------------------------------------------
if (-not (Get-Jdk21Home)) { Install-Winget 'EclipseAdoptium.Temurin.21.JDK' }
$jdk = Get-Jdk21Home
if (-not $jdk) { throw 'JDK 21 not found after install.' }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
Write-Host "JDK: $jdk"

# --- Node 20+ and pnpm ----------------------------------------------------
Refresh-Path
$nodeMajor = 0
if (Get-Command node -ErrorAction SilentlyContinue) { $nodeMajor = [int]((node -v).TrimStart('v').Split('.')[0]) }
if ($nodeMajor -lt 20) { Install-Winget 'OpenJS.NodeJS.LTS'; Refresh-Path }
if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) { npm install -g pnpm; Refresh-Path }
Write-Host "Node $(node -v), pnpm $(pnpm -v)"

# --- PostgreSQL 17 (native Windows service, dev only) ---------------------
$pgBin = 'C:\Program Files\PostgreSQL\17\bin'
if (-not (Test-Path "$pgBin\psql.exe")) {
    Write-Host '==> Installing PostgreSQL 17 (superuser password for local dev: postgres)'
    winget install --id PostgreSQL.PostgreSQL.17 -e --accept-package-agreements --accept-source-agreements --silent `
        --override '--mode unattended --unattendedmodeui none --superpassword postgres'
}
$env:PGPASSWORD = 'postgres'
$exists = & "$pgBin\psql.exe" -h localhost -U postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='basecamp'"
if (-not $exists) { & "$pgBin\psql.exe" -h localhost -U postgres -c "CREATE ROLE basecamp LOGIN PASSWORD 'basecamp'" }
$exists = & "$pgBin\psql.exe" -h localhost -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='basecamp'"
if (-not $exists) { & "$pgBin\psql.exe" -h localhost -U postgres -c "CREATE DATABASE basecamp OWNER basecamp" }
Write-Host 'Database basecamp ready (user basecamp / password basecamp).'

# --- Gradle wrapper (generated once, then committed) -----------------------
if (-not (Test-Path "$root\api\gradlew.bat")) {
    Write-Host '==> Generating Gradle wrapper'
    $zip = Join-Path $env:TEMP 'gradle-8.14.3-bin.zip'
    if (-not (Test-Path $zip)) { Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip' -OutFile $zip }
    Expand-Archive $zip -DestinationPath $env:TEMP -Force
    Push-Location "$root\api"
    & "$env:TEMP\gradle-8.14.3\bin\gradle.bat" wrapper --gradle-version 8.14.3 --no-daemon
    Pop-Location
}

# --- Frontend deps ---------------------------------------------------------
Push-Location "$root\web"
pnpm install
Pop-Location

Write-Host "`nSetup done. Start everything with:  powershell -ExecutionPolicy Bypass -File scripts\run-dev.ps1"
