# Full stop + start. Not a soft reload of any one piece - use this when something is in a
# confused state and you want a genuinely clean slate (Docker data volumes are preserved;
# dev-down.ps1 does not use -v). A running dev-host.ps1 sharing link is kept, so the same
# Cloudflare link works again once the stack is back up (.\dev-down.ps1 alone stops it).
$root = $PSScriptRoot
& (Join-Path $root 'dev-down.ps1') -KeepSharing
& (Join-Path $root 'dev-up.ps1')
