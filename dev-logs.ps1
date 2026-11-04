# Tails logs for one service, or lists what's available if called with no argument.
# Usage: .\dev-logs.ps1 [postgres|keycloak|localstack|backend|frontend] [-Follow]
param(
    [ValidateSet("postgres", "keycloak", "localstack", "backend", "frontend")]
    [string]$Service,
    [switch]$Follow
)
$root = $PSScriptRoot
Set-Location $root

if (-not $Service) {
    Write-Host "Usage: .\dev-logs.ps1 <postgres|keycloak|localstack|backend|frontend> [-Follow]"
    Write-Host "  postgres/keycloak/localstack -> docker compose logs"
    Write-Host "  backend/frontend             -> logs\backend.log / logs\frontend.log (written by dev-up.ps1)"
    exit 0
}

switch ($Service) {
    { $_ -in "postgres", "keycloak", "localstack" } {
        if ($Follow) { docker compose logs -f $Service } else { docker compose logs --tail 200 $Service }
    }
    "backend" {
        $log = Join-Path $root 'logs\backend.log'
        if (-not (Test-Path $log)) { Write-Warning "No logs\backend.log yet - has dev-up.ps1 been run?"; exit 1 }
        if ($Follow) { Get-Content $log -Wait -Tail 50 } else { Get-Content $log -Tail 200 }
    }
    "frontend" {
        $log = Join-Path $root 'logs\frontend.log'
        if (-not (Test-Path $log)) { Write-Warning "No logs\frontend.log yet - has dev-up.ps1 been run?"; exit 1 }
        if ($Follow) { Get-Content $log -Wait -Tail 50 } else { Get-Content $log -Tail 200 }
    }
}
