# Manage the local dev services individually.
#   scripts\dev.ps1 api restart      scripts\dev.ps1 web stop
#   scripts\dev.ps1 all start        scripts\dev.ps1 all status
# Each service runs in its own PowerShell window; the window PID is kept in .dev\ and the port owner is
# killed too, so restart works even if the window was closed or the JVM was left running.
param(
    [ValidateSet('api', 'web', 'all')] [string]$Target = 'all',
    [ValidateSet('start', 'stop', 'restart', 'status')] [string]$Action = 'status'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$stateDir = Join-Path $root '.dev'
New-Item -ItemType Directory -Force $stateDir | Out-Null

$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -like 'jdk-21*' | Select-Object -First 1 -ExpandProperty FullName

$services = @{
    api = @{
        port = 8080
        cmd  = "`$env:JAVA_HOME='$jdk'; cd '$root\api'; .\gradlew.bat bootRun 2>&1 | Tee-Object '$root\api\bootrun.log'"
    }
    web = @{
        port = 5173
        cmd  = "cd '$root\web'; pnpm dev --open=false"
    }
}

function Get-PortOwner($port) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
}

function Stop-Service($name) {
    $pidFile = Join-Path $stateDir "$name.pid"
    if (Test-Path $pidFile) {
        $wid = [int](Get-Content $pidFile)
        if (Get-Process -Id $wid -ErrorAction SilentlyContinue) { taskkill /PID $wid /T /F | Out-Null }
        Remove-Item $pidFile
    }
    $owner = Get-PortOwner $services[$name].port
    if ($owner) { Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue }
    Write-Host "$name stopped"
}

function Start-Service($name) {
    if (Get-PortOwner $services[$name].port) { Write-Host "$name already running on :$($services[$name].port)"; return }
    if ($name -eq 'api' -and -not $jdk) { throw 'JDK 21 not found. Run scripts\setup-windows.ps1 first.' }
    $p = Start-Process powershell -ArgumentList '-NoExit', '-Command', $services[$name].cmd -PassThru
    Set-Content (Join-Path $stateDir "$name.pid") $p.Id
    Write-Host "$name starting in a new window (:$($services[$name].port))"
}

function Show-Status($name) {
    $port = $services[$name].port
    $state = if (Get-PortOwner $port) { 'running' } else { 'stopped' }
    Write-Host ("{0,-4} {1,-8} http://localhost:{2}" -f $name, $state, $port)
}

$names = if ($Target -eq 'all') { @('api', 'web') } else { @($Target) }
foreach ($n in $names) {
    switch ($Action) {
        'start'   { Start-Service $n }
        'stop'    { Stop-Service $n }
        'restart' { Stop-Service $n; Start-Sleep -Seconds 1; Start-Service $n }
        'status'  { Show-Status $n }
    }
}
