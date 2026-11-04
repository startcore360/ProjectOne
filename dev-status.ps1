# Quick health check: are the Docker services, backend and frontend actually up and reachable -
# not just "a process exists", but actually answering requests.
$root = $PSScriptRoot
Set-Location $root

Write-Host "--- Docker containers ---" -ForegroundColor Cyan
docker compose ps

function Test-Http($name, $url) {
    # Found by actually running this script: Invoke-WebRequest throws on any non-2xx/3xx status
    # by default (PowerShell 5.1 has no -SkipHttpErrorCheck), which made /api/auth/me's correct
    # 401-when-unauthenticated response look like "the backend is down". A response object
    # existing at all - any status code - means something answered; only a connection-level
    # failure (refused/timeout, no response at all) means actually down.
    try {
        Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 | Out-Null
        Write-Host "  [UP]   $name ($url)" -ForegroundColor Green
    } catch {
        if ($_.Exception.Response) {
            Write-Host "  [UP]   $name ($url) - HTTP $([int]$_.Exception.Response.StatusCode)" -ForegroundColor Green
        } else {
            Write-Host "  [DOWN] $name ($url)" -ForegroundColor Red
        }
    }
}

Write-Host "`n--- Services ---" -ForegroundColor Cyan
try {
    docker compose exec -T postgres pg_isready -U edu360 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "  [UP]   Postgres" -ForegroundColor Green }
    else { Write-Host "  [DOWN] Postgres" -ForegroundColor Red }
} catch { Write-Host "  [DOWN] Postgres" -ForegroundColor Red }
Test-Http "Keycloak (abc-university realm)" "http://127.0.0.1:8081/realms/abc-university/.well-known/openid-configuration"
Test-Http "LocalStack" "http://127.0.0.1:4566/_localstack/health"
Test-Http "Backend API" "http://127.0.0.1:8080/api/auth/me"
Test-Http "Frontend (Vite)" "http://127.0.0.1:5173"
