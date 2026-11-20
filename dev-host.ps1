# Shares the local platform console AND every tenant through one temporary Cloudflare link.
#
#   .\dev-host.ps1                share; prints the link, the picker and a link per tenant
#   .\dev-host.ps1 -Tenant abc    same, but the plain link opens tenant "abc" by default
#   .\dev-host.ps1 -New           start a fresh link even if one is running
#   .\dev-host.ps1 -Stop          stop sharing
#
#   .\dev-down.ps1 stops the sharing link too. .\dev-restart.ps1 keeps it: the same link works again
#   once the stack is back up (if not, run .\dev-host.ps1 - it keeps the link, restarting only the
#   tunnel frontend).
#
#   <link>/__tenant               picker: platform console or any tenant subdomain
#   <link>/__tenant/<subdomain>   open that tenant's sign-in (new tenants work at once)
#
# What it starts:
#   1. a second frontend on port 5174 (app/vite.tunnel.config.ts) that calls /api and the live socket
#      on its own address and forwards them to the local backend as the chosen host
#      (platform.edu360.local or <tenant>.edu360.local);
#   2. a Cloudflare quick tunnel to that port.
# The normal dev server (http://platform.edu360.local:5173), the backend and Keycloak are not exposed.
#
# Needs the stack from .\dev-up.ps1 running first.
# One browser opens one address at a time (a cookie on the link remembers it); use a private window for a second.
# Anyone with the link can reach this machine while it runs: share it only with people you trust,
# and run .\dev-host.ps1 -Stop when you are done. Never use this for production.
param([switch]$Stop, [switch]$New, [string]$Tenant = 'platform')

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$logsDir = Join-Path $root 'logs'
$pidFile = Join-Path $logsDir 'dev-host.pids'
$tunnelLog = Join-Path $logsDir 'dev-host-tunnel.log'
$frontendLog = Join-Path $logsDir 'dev-host-frontend.log'
$port = 5174
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

function Stop-DevHost {
    $stopped = 0
    if (Test-Path $pidFile) {
        foreach ($id in (Get-Content $pidFile)) {
            if ($id -match '^\d+$') {
                try { Stop-Process -Id ([int]$id) -Force -ErrorAction Stop; $stopped++ } catch { }
            }
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
    # Anything still holding the tunnel port (e.g. the Vite child of a closed window).
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        try { Stop-Process -Id $listener.OwningProcess -Force -ErrorAction Stop; $stopped++ } catch { }
    }
    Get-Process -Name cloudflared -ErrorAction SilentlyContinue | ForEach-Object {
        try { Stop-Process -Id $_.Id -Force -ErrorAction Stop; $stopped++ } catch { }
    }
    return $stopped
}

if ($Stop) {
    $count = Stop-DevHost
    Write-Host "Sharing stopped ($count process(es) ended). The Cloudflare link no longer works." -ForegroundColor Green
    exit 0
}

# 1. The backend must be up, otherwise the link opens a page that cannot sign in.
try {
    Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
    # 401 means the backend answered; only "no answer at all" is a problem.
    if (-not $_.Exception.Response) {
        Write-Error "The backend is not running on port 8080. Start the stack with .\dev-up.ps1 first."
        exit 1
    }
}

# 2. cloudflared (installed once with winget).
$cloudflared = @(
    (Get-Command cloudflared -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
    'C:\Program Files (x86)\cloudflared\cloudflared.exe',
    'C:\Program Files\cloudflared\cloudflared.exe'
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $cloudflared) {
    Write-Host 'Installing cloudflared (one time)...'
    winget install --id Cloudflare.cloudflared -e --accept-source-agreements --accept-package-agreements --silent | Out-Null
    $cloudflared = @('C:\Program Files (x86)\cloudflared\cloudflared.exe', 'C:\Program Files\cloudflared\cloudflared.exe') |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $cloudflared) { Write-Error 'cloudflared could not be installed. Install it with: winget install Cloudflare.cloudflared'; exit 1 }
}

# The tenant (or "platform") the link presents to the backend; one link serves one of them.
$Tenant = $Tenant.Trim().ToLower()
if ($Tenant -notmatch '^[a-z0-9-]+$') { Write-Error "-Tenant must be a tenant subdomain such as abc (letters, digits, hyphens)."; exit 1 }

# The tunnel frontend on port 5174, in its own window so its output stays readable.
function Start-TunnelFrontend {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { try { Stop-Process -Id $listener.OwningProcess -Force -ErrorAction Stop } catch { } }
    $appDir = Join-Path $root 'app'
    $frontendCmd = "`$env:VITE_API_BASE_URL = '/api'; `$env:TUNNEL_TENANT = '$Tenant'; npx vite --config vite.tunnel.config.ts 2>&1 | Tee-Object -FilePath '$frontendLog'"
    $process = Start-Process powershell -ArgumentList '-NoExit', '-Command', $frontendCmd -WorkingDirectory $appDir -PassThru
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        try { Invoke-WebRequest -Uri "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 3 | Out-Null; return $process } catch { }
    }
    Write-Error "The tunnel frontend did not start on port $port. See $frontendLog"
    exit 1
}

# 3. Keep the current link when its tunnel is still running - e.g. after .\dev-restart.ps1, which
#    stops the frontends but leaves the tunnel alone - and only restart the tunnel frontend.
#    -New always starts a fresh link.
$existingUrl = $null
$existingTunnel = Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Select-Object -First 1
if ($existingTunnel -and -not $New -and (Test-Path $tunnelLog)) {
    $match = Select-String -Path $tunnelLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
    if ($match) { $existingUrl = $match.Matches[0].Value }
}

if ($existingUrl) {
    Write-Host 'The sharing link is still running; keeping it and restarting the tunnel frontend...'
    $frontend = Start-TunnelFrontend
    Set-Content -Path $pidFile -Value @($frontend.Id, $existingTunnel.Id)
    $url = $existingUrl
} else {
    # Start clean: a previous run's link would otherwise keep pointing at an old server.
    Stop-DevHost | Out-Null
    $frontend = Start-TunnelFrontend
}

# 5. The Cloudflare quick tunnel; it prints the public link on stderr.
if (-not $existingUrl) {
    Remove-Item $tunnelLog -Force -ErrorAction SilentlyContinue
    $tunnel = Start-Process -FilePath $cloudflared -ArgumentList 'tunnel', '--no-autoupdate', '--url', "http://localhost:$port" `
        -RedirectStandardError $tunnelLog -RedirectStandardOutput (Join-Path $logsDir 'dev-host-tunnel.out.log') -PassThru -WindowStyle Hidden
    Set-Content -Path $pidFile -Value @($frontend.Id, $tunnel.Id)

    $url = $null
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 2
        if (Test-Path $tunnelLog) {
            $match = Select-String -Path $tunnelLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($match) { $url = $match.Matches[0].Value; break }
        }
    }
    if (-not $url) {
        Write-Error "Cloudflare did not return a link. See $tunnelLog"
        exit 1
    }

    # 6. Cloudflare publishes the address a few seconds after issuing it. Wait instead of probing: a
    #    probe that runs too early makes Windows cache "host not found" and the link then fails on this
    #    machine for several minutes (other devices are unaffected).
    Write-Host 'Waiting for Cloudflare to publish the link...'
    Start-Sleep -Seconds 20
}
try { Set-Clipboard -Value $url } catch { }

Write-Host ''
$what = if ($Tenant -eq 'platform') { 'the platform console' } else { "tenant '$Tenant'" }
Write-Host "  Shared at: $url" -ForegroundColor Green
Write-Host "  (copied to the clipboard; opens $what by default)"
Write-Host ''
Write-Host "  Choose platform or any tenant:  $url/__tenant" -ForegroundColor Cyan
Write-Host "  Platform console:               $url/__tenant/platform"

# A ready link for every tenant that can sign in (Active or Trial), read from the local database.
# New tenants need no restart: open <link>/__tenant/<subdomain> or type it on the picker page.
try {
    $rows = docker exec personalproject-postgres-1 psql -U edu360 -d edu360db -At -F '|' `
        -c "select subdomain, tenant_name, state from platform.tenant where state in ('ACTIVE','TRIAL') order by state, tenant_name" 2>$null
    if ($LASTEXITCODE -eq 0 -and $rows) {
        Write-Host ''
        Write-Host '  Tenants (Active / Trial):'
        foreach ($row in @($rows)) {
            $parts = $row -split '\|', 3
            if ($parts.Count -eq 3) {
                Write-Host ("    {0,-28} {1}/__tenant/{2}   ({3})" -f $parts[1].Substring(0, [Math]::Min(28, $parts[1].Length)), $url, $parts[0], $parts[2].ToLower())
            }
        }
    }
} catch { }

Write-Host ''
Write-Host '  One browser opens one address at a time; use a private/incognito window for a second one.'
Write-Host '  Anyone with the link can reach this machine while it runs.'
Write-Host '  Stop sharing with: .\dev-host.ps1 -Stop'
Write-Host ''
