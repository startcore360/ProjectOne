# Provisions the edu_app Postgres role used by the Spring Boot datasource (never a
# superuser, never BYPASSRLS - see sql/01_schema.sql). Generates a random password only
# if docker/postgres/.env.local does not already hold one; reuses it on every later run.
# Applies the EDU 360 schema (sql/) to the dev database if not already applied.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$envFile = Join-Path $PSScriptRoot ".env.local"

function New-StrongPassword {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes) -replace '[^a-zA-Z0-9]', ''
}

if (-not (Test-Path $envFile)) {
    $appPassword = New-StrongPassword
    $platformPassword = New-StrongPassword
    "EDU_APP_PASSWORD=$appPassword`nEDU_PLATFORM_PASSWORD=$platformPassword" |
        Out-File -FilePath $envFile -Encoding utf8 -NoNewline
    Write-Host "Generated new edu_app/edu_platform passwords in $envFile"
} else {
    Write-Host "Reusing existing passwords from $envFile"
    # EDU_PLATFORM_PASSWORD was added after some .env.local files already existed (edu_platform
    # is needed by DevAdminSeeder, which writes platform.platform_user - a table edu_app is
    # deliberately never granted write access to, matching the schema's own role separation) -
    # append it, once, without touching the existing EDU_APP_PASSWORD line at all.
    $existing = Get-Content $envFile -Raw
    if ($existing -notmatch 'EDU_PLATFORM_PASSWORD=') {
        $platformPassword = New-StrongPassword
        Add-Content -Path $envFile -Value "`nEDU_PLATFORM_PASSWORD=$platformPassword" -NoNewline
        Write-Host "Added a new edu_platform password to $envFile (edu_app password untouched)"
    }
}

$password = (Get-Content $envFile | Select-String '^EDU_APP_PASSWORD=(.*)$').Matches[0].Groups[1].Value
if ([string]::IsNullOrWhiteSpace($password)) {
    throw "$envFile exists but EDU_APP_PASSWORD is empty or malformed."
}
$platformPassword = (Get-Content $envFile | Select-String '^EDU_PLATFORM_PASSWORD=(.*)$').Matches[0].Groups[1].Value
if ([string]::IsNullOrWhiteSpace($platformPassword)) {
    throw "$envFile exists but EDU_PLATFORM_PASSWORD is empty or malformed."
}

$container = (docker compose -f (Join-Path $root "docker-compose.yml") ps -q postgres)
if ([string]::IsNullOrWhiteSpace($container)) {
    throw "Postgres container is not running. Run .\dev-up.ps1 first."
}

function Run-Sql($file) {
    Get-Content (Join-Path $root "sql\$file") -Raw | docker exec -i -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q
}

$schemaApplied = docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -tAc "SELECT to_regclass('app.tenant_local') IS NOT NULL"
if ($schemaApplied.Trim() -ne "t") {
    Write-Host "Applying EDU 360 schema to edu360db..."
    foreach ($f in @("01_schema.sql", "02_rules.sql", "03_bindings.sql", "03b_fk_indexes.sql", "08_v40_rules.sql", "10_v40_rolldown.sql")) {
        Run-Sql $f
    }
    Write-Host "Schema applied."
} else {
    Write-Host "EDU 360 schema already present in edu360db."
}

docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "ALTER ROLE edu_app WITH PASSWORD '$password'"
Write-Host "edu_app role credential set."

# NOT part of the approved sql/ bundle - deliberately kept out of it. The schema grants edu_app
# nothing on the platform schema at all (confirmed empty: SELECT * FROM
# information_schema.role_table_grants WHERE grantee='edu_app' AND table_schema='platform'
# returns zero rows) - platform.* is designed for a separate platform-admin service/role in the
# real target architecture. This monolith's single process currently needs read-only access to
# exactly two platform tables for login/tenant resolution (LoginTenantResolver,
# TenantIssuerAuthenticationManagerResolver): platform.tenant (realm/subdomain -> tenant) and
# platform.platform_user (platform-realm identity lookup). Granted SELECT only, nothing else -
# this app never creates or edits a tenant or a platform user, so no write access is given.
# USAGE on the schema is required separately from SELECT on the tables - Postgres checks both;
# granting only table-level SELECT still fails with "permission denied for schema platform".
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT USAGE ON SCHEMA platform TO edu_app"
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT SELECT ON platform.tenant, platform.platform_user TO edu_app"
Write-Host "Granted edu_app read-only access to platform.tenant and platform.platform_user."

# edu_platform (created by sql/01_schema.sql, already granted SELECT/INSERT/UPDATE on
# platform.platform_user and platform.plan by sql/03_bindings.sql) is the role DevAdminSeeder
# uses for its one dev-only INSERT - never edu_app, which the schema deliberately keeps
# read-only (or with zero access at all) on every platform.* table. Setting its password here,
# not granting it anything new: every grant it needs already exists in the approved bundle.
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "ALTER ROLE edu_platform WITH LOGIN PASSWORD '$platformPassword'"
Write-Host "edu_platform role credential set."

# public.audit_logs is LEGACY debt, not part of the approved V4.0 schema at all - it is the
# pre-migration single-tenant audit trail (AuditLog/AuditLogService), left over from a
# ddl-auto=update run before the identity migration retired that approach. The real V4.0 audit
# mechanism (Foundation Item 9: every create/edit/delete/approval/export/login logged in the
# app/platform schema) is a separate, not-yet-built piece - this grant exists only to keep the
# existing (imperfect) login/logout audit calls from throwing, not to expand this table's role
# in the system. Do not add more legacy tables here; replace this with the real mechanism.
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT SELECT, INSERT ON public.audit_logs TO edu_app" 2>$null
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT USAGE, SELECT ON public.audit_logs_id_seq TO edu_app" 2>$null
Write-Host "Granted edu_app access to the legacy public.audit_logs table (if present)."
