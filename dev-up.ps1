# Starts Postgres + Keycloak in Docker, then opens the backend (Spring Boot) and
# frontend (Vite) dev servers in their own windows, wired to the Dockerized Postgres.
$root = $PSScriptRoot

Set-Location $root
docker compose up -d

Write-Host "Waiting for Postgres to accept connections..."
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    docker compose exec -T postgres pg_isready -U edu360 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    Write-Warning "Postgres did not report ready in time; continuing anyway."
}

# Applies the sql/ bundle and sets the edu_app role's password if not already done - safe to
# call every run, since both steps are idempotent (see provision-edu-app.ps1's own checks).
& (Join-Path $root 'docker\postgres\provision-edu-app.ps1')

Write-Host "Waiting for Keycloak to accept requests..."
$keycloakReady = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:8081/realms/abc-university/.well-known/openid-configuration" `
            -UseBasicParsing -TimeoutSec 2 | Out-Null
        $keycloakReady = $true
        break
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $keycloakReady) {
    Write-Warning "Keycloak did not report ready in time; continuing anyway."
} else {
    # Keycloak has no persistent volume - every container recreate re-imports the realms from
    # scratch, so this post-import fix (see that script's own comment for why it is needed at
    # all) has to run every time, not just once.
    & (Join-Path $root 'docker\keycloak\provision-realms.ps1')
}

# The backend connects as edu_app specifically (never the edu360 superuser) so Postgres RLS is
# actually enforced in dev the same way it will be in prod - see application-dev.properties.
# The password is never generated or overwritten here: provision-edu-app.ps1 is the only thing
# that creates it (once, into the gitignored .env.local), and this script only ever reads it.
$envFile = Join-Path $root 'docker\postgres\.env.local'
if (-not (Test-Path $envFile)) {
    Write-Error "docker\postgres\.env.local not found. Run .\docker\postgres\provision-edu-app.ps1 first."
    exit 1
}
$eduAppPassword = (Get-Content $envFile | Select-String '^EDU_APP_PASSWORD=(.*)$').Matches[0].Groups[1].Value
if ([string]::IsNullOrWhiteSpace($eduAppPassword)) {
    Write-Error "$envFile exists but EDU_APP_PASSWORD is empty or malformed."
    exit 1
}
$eduPlatformPassword = (Get-Content $envFile | Select-String '^EDU_PLATFORM_PASSWORD=(.*)$').Matches[0].Groups[1].Value
if ([string]::IsNullOrWhiteSpace($eduPlatformPassword)) {
    Write-Error "$envFile exists but EDU_PLATFORM_PASSWORD is empty or malformed. Re-run .\docker\postgres\provision-edu-app.ps1."
    exit 1
}

$logsDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
$backendLog = Join-Path $logsDir 'backend.log'
$frontendLog = Join-Path $logsDir 'frontend.log'

# Backend/frontend output is teed to logs/*.log (in addition to the visible window) so
# dev-logs.ps1 has something real to tail - the windows alone aren't readable from a script.
$backendCmd = @"
`$env:SPRING_DATASOURCE_USERNAME = 'edu_app'
`$env:SPRING_DATASOURCE_PASSWORD = '$eduAppPassword'
`$env:PLATFORM_ADMIN_DATASOURCE_PASSWORD = '$eduPlatformPassword'
.\mvnw.cmd spring-boot:run 2>&1 | Tee-Object -FilePath '$backendLog'
"@
$backendProc = Start-Process powershell -ArgumentList '-NoExit', '-Command', $backendCmd -WorkingDirectory (Join-Path $root 'api') -PassThru
Start-Sleep -Seconds 3
if ($backendProc.HasExited) {
    Write-Warning "Backend window closed immediately (exit code $($backendProc.ExitCode)); check the api/ setup."
}

$frontendCmd = "npm run dev 2>&1 | Tee-Object -FilePath '$frontendLog'"
$frontendProc = Start-Process powershell -ArgumentList '-NoExit', '-Command', $frontendCmd -WorkingDirectory (Join-Path $root 'app') -PassThru
Start-Sleep -Seconds 3
if ($frontendProc.HasExited) {
    Write-Warning "Frontend window closed immediately (exit code $($frontendProc.ExitCode)); check the app/ setup."
}

Write-Host "Postgres + Keycloak running in Docker; backend and frontend starting in new windows."
Write-Host "Logs: $backendLog / $frontendLog (or .\dev-logs.ps1)"
