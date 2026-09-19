# Starts the API (:8080) and the web dev server (:5173) in their own windows,
# waits until the backend answers /api/ping, then opens the browser.
# For starting/stopping/restarting one service, use scripts\dev.ps1 (see README).
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\dev.ps1" all start

Write-Host 'Waiting for the backend (first start compiles, this can take a minute or two)...'
for ($i = 0; $i -lt 120; $i++) {
    try {
        $r = Invoke-RestMethod http://localhost:8080/api/ping -TimeoutSec 2
        Write-Host "Backend reachable: $($r | ConvertTo-Json -Compress)"
        Start-Process http://localhost:5173
        return
    } catch { Start-Sleep -Seconds 3 }
}
Write-Warning 'Backend did not answer within 6 minutes. See api\bootrun.log.'
