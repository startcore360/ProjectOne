# Stops everything dev-up.ps1 started: the backend window, the frontend window,
# and the Postgres + Keycloak Docker containers.
$root = $PSScriptRoot
Set-Location $root

Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'mvnw.cmd spring-boot:run' } | ForEach-Object {
    Write-Host "Stopping backend (PID $($_.ProcessId))"
    taskkill /PID $_.ProcessId /T /F 2>$null | Out-Null
}

Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'npm run dev' -or $_.CommandLine -match '\bvite\b' } | ForEach-Object {
    Write-Host "Stopping frontend (PID $($_.ProcessId))"
    taskkill /PID $_.ProcessId /T /F 2>$null | Out-Null
}

docker compose down

Write-Host "All stopped."
