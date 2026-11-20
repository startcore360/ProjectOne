# E6-S3 local development backup agent. Do not run outside the dev profile.
# Required environment: BACKUP_AGENT_SECRET, BACKUP_API_URL, BACKUP_TENANT_ID,
# SPRING_PROFILES_ACTIVE=dev, PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD.
$ErrorActionPreference = "Stop"
if ($env:SPRING_PROFILES_ACTIVE -ne "dev") { throw "Backup agent is dev-profile only." }
foreach ($name in @("BACKUP_AGENT_SECRET","BACKUP_API_URL","BACKUP_TENANT_ID","PGHOST","PGPORT","PGDATABASE","PGUSER","PGPASSWORD")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Missing required environment variable: $name" }
}
$backupDir = Join-Path $PSScriptRoot "backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = [DateTime]::UtcNow
$file = Join-Path $backupDir ("edu360-{0:yyyyMMdd-HHmmss}.dump" -f $stamp)
$env:PGPASSWORD = $env:PGPASSWORD
& pg_dump --format=custom --no-owner --file $file
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE" }
$payload = @{
    tenantId = $env:BACKUP_TENANT_ID
    lastDailyBackupAt = $stamp.ToString("o")
    outcome = "SUCCESS"
    earliestRestorableAt = $null
    latestRestorableAt = $stamp.ToString("o")
    lastRestoreTestAt = $null
    lastRestoreTestOutcome = $null
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri $env:BACKUP_API_URL -Headers @{ "X-Backup-Agent-Secret" = $env:BACKUP_AGENT_SECRET } -ContentType "application/json" -Body $payload | Out-Null
Write-Output "Backup completed and status submitted: $file"
