# Adds the local hostnames EDU 360 dev needs to the Windows hosts file: "platform.edu360.local",
# "abc.edu360.local", "xyz.edu360.local", and one for every ACTIVE or TRIAL tenant in the local
# database - without needing real DNS or a subdomain registration. Re-run it after creating a
# tenant. The hosts file has no wildcards, so this step exists only in local development;
# production uses wildcard DNS (see docker/PRODUCTION_TENANT_ROUTING.txt).
#
# WHY a hosts file at all: the backend tells tenants apart by the Host header on each request
# (see TenantResolver). That only works if the browser is actually sending a different
# hostname per tenant - "localhost" for everything would give every tenant the same Host header.
#
# This script must be run as Administrator (editing the hosts file needs elevated rights) -
# it is NOT something this agent can do on its own, since it changes a system file outside the
# project folder. Run it yourself: right-click PowerShell -> "Run as Administrator", then:
#   .\docker\dev-hosts-setup.ps1
$ErrorActionPreference = "Stop"

$hostnames = @("abc", "xyz", "platform")

# Add every tenant that can currently log in. If Docker or the database isn't up, keep the three
# names above rather than failing.
$root = Split-Path $PSScriptRoot -Parent
try {
    $container = (docker compose -f (Join-Path $root "docker-compose.yml") ps -q postgres 2>$null)
    if ($container) {
        $subdomains = docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -tAc "SELECT subdomain FROM platform.tenant WHERE state IN ('ACTIVE', 'TRIAL') ORDER BY 1" 2>$null
        # This value goes into a system file, so accept only what ck_tenant_subdomain allows.
        $hostnames += @($subdomains | Where-Object { $_ -cmatch '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$' })
    } else {
        Write-Host "Postgres isn't running, so only the standard names will be added." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Could not read tenants from the database, so only the standard names will be added." -ForegroundColor Yellow
}
$entries = @($hostnames | Select-Object -Unique | ForEach-Object { "127.0.0.1 $_.edu360.local" })
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    $missing = @($entries | Where-Object { (Get-Content $hostsPath) -notcontains $_ })
    if ($missing.Count -eq 0) {
        Write-Host "All $($entries.Count) hostnames are already in $hostsPath."
        exit 0
    }
    Write-Host "Not running as Administrator. Add these lines to $hostsPath yourself, or re-run as Administrator:" -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host "  $_" }
    exit 1
}

# Only add lines that aren't there yet - re-running this script must never duplicate entries.
$current = Get-Content $hostsPath
$missing = @($entries | Where-Object { $current -notcontains $_ })
if ($missing.Count -eq 0) {
    Write-Host "All $($entries.Count) hostnames are already in $hostsPath."
    exit 0
}

# Windows locks the hosts file briefly after each change while it reloads it, so write every new
# line in one go, and wait and retry if the file is still busy.
$maxAttempts = 10
for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    try {
        Add-Content -Path $hostsPath -Value $missing -ErrorAction Stop
        break
    } catch [System.IO.IOException] {
        if ($attempt -eq $maxAttempts) {
            Write-Host "The hosts file is still busy. Nothing was added. Wait a few seconds and run this again." -ForegroundColor Red
            exit 1
        }
        Start-Sleep -Milliseconds 500
    }
}

$missing | ForEach-Object { Write-Host "Added: $_" }
Write-Host "$($missing.Count) new entr$(if ($missing.Count -eq 1) {'y'} else {'ies'}) added to $hostsPath."
