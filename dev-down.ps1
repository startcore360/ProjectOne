# Stops everything dev-up.ps1 started: the backend window, the frontend window,
# and the Postgres + Keycloak Docker containers - and the dev-host.ps1 Cloudflare sharing link.
#
#   .\dev-down.ps1                 stop everything, including the sharing link
#   .\dev-down.ps1 -KeepSharing    stop the stack but keep the sharing link (dev-restart.ps1 uses
#                                  this, so the same link works again once the stack is back up)
param([switch]$KeepSharing)

$root = $PSScriptRoot
Set-Location $root

Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'mvnw.cmd spring-boot:run' } | ForEach-Object {
    Write-Host "Stopping backend (PID $($_.ProcessId))"
    taskkill /PID $_.ProcessId /T /F 2>$null | Out-Null
}

# The dev-host sharing server (vite.tunnel.config.ts) is handled below, not here.
Get-CimInstance Win32_Process | Where-Object {
    ($_.CommandLine -match 'npm run dev' -or $_.CommandLine -match '\bvite\b') -and $_.CommandLine -notmatch 'vite\.tunnel\.config'
} | ForEach-Object {
    Write-Host "Stopping frontend (PID $($_.ProcessId))"
    taskkill /PID $_.ProcessId /T /F 2>$null | Out-Null
}

docker compose down

$sharing = Get-Process -Name cloudflared -ErrorAction SilentlyContinue
if ($sharing -and -not $KeepSharing) {
    & (Join-Path $root 'dev-host.ps1') -Stop
} elseif ($sharing) {
    Write-Host "Keeping the dev-host sharing link (it works again once the stack is back up)."
}

Write-Host "All stopped."
