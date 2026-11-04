# Adds the three local tenant hostnames EDU 360 dev needs to the Windows hosts file, so the
# browser can hit "abc.edu360.local", "xyz.edu360.local" and "platform.edu360.local" and have
# them resolve to this machine - without needing real DNS or a subdomain registration.
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

$entries = @(
    "127.0.0.1 abc.edu360.local",
    "127.0.0.1 xyz.edu360.local",
    "127.0.0.1 platform.edu360.local"
)
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Not running as Administrator. Add these lines to $hostsPath yourself:" -ForegroundColor Yellow
    $entries | ForEach-Object { Write-Host "  $_" }
    exit 1
}

$current = Get-Content $hostsPath
$added = 0
foreach ($entry in $entries) {
    # Only add a line that isn't there yet - re-running this script must never duplicate entries.
    if ($current -notcontains $entry) {
        Add-Content -Path $hostsPath -Value $entry
        Write-Host "Added: $entry"
        $added++
    } else {
        Write-Host "Already present: $entry"
    }
}
Write-Host "$added new entr$(if ($added -eq 1) {'y'} else {'ies'}) added to $hostsPath."
