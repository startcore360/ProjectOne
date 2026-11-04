# Full stop + start. Not a soft reload of any one piece - use this when something is in a
# confused state and you want a genuinely clean slate (Docker data volumes are preserved;
# dev-down.ps1 does not use -v).
$root = $PSScriptRoot
& (Join-Path $root 'dev-down.ps1')
& (Join-Path $root 'dev-up.ps1')
