# Provisions the edu_app Postgres role used by the Spring Boot datasource (never a
# superuser, never BYPASSRLS - see sql/01_schema.sql). Generates a random password only
# if docker/postgres/.env.local does not already hold one; reuses it on every later run.
# Applies the EDU 360 schema (sql/) to the dev database if not already applied.
#
# -Container: optional. Defaults to the docker compose "postgres" service (what dev-up.ps1 uses).
#   Pass a container name only to provision a separate throwaway Postgres (e.g. to prove a fresh
#   volume works) - that container must use the same POSTGRES_USER/PASSWORD/DB as
#   docker/postgres/docker-compose.yml (edu360 / edu360 / edu360db).
param(
    [string]$Container = ""
)
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

if ([string]::IsNullOrWhiteSpace($Container)) {
    $container = (docker compose -f (Join-Path $root "docker-compose.yml") ps -q postgres)
} else {
    $container = $Container
}
if ([string]::IsNullOrWhiteSpace($container)) {
    throw "Postgres container is not running. Run .\dev-up.ps1 first."
}

function Run-Sql($file) {
    # Copy the file in and run it with psql -f, instead of piping it through PowerShell: Windows
    # PowerShell 5.1 re-encodes piped text to native programs, which turned every non-ASCII
    # character of the bundle (e.g. the '<=3.2' (U+2264) version labels in 05_ref_data.sql) into '?'.
    $target = "/tmp/edu360_provision_$file"
    docker cp (Join-Path $root "sql\$file") "${container}:$target" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Copying sql\$file into the Postgres container failed (docker exit code $LASTEXITCODE)."
    }
    docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -f $target
    $psqlExit = $LASTEXITCODE
    docker exec $container rm -f $target | Out-Null
    if ($psqlExit -ne 0) {
        throw "Applying sql\$file to edu360db failed (psql exit code $psqlExit)."
    }
}

# ---------------------------------------------------------------------------------------------
# Bundle order. Keep in step with sql/run_all.sh (Q-06): the schema files of section A, then the
# reference data of section B, then the V4.1 files of section C, in the same order:
#   01 02 03 03b 08 10 | 05 10d | 12 12d 14 15 16 18 20
# Base files and 05 / 12 are not re-runnable, so each is guarded by a "already applied?" check.
# Every other file is idempotent and is applied on every run, so an existing dev database picks
# up later fixes (proved by run_all.sh section D, which applies the V4.1 files twice).
# ---------------------------------------------------------------------------------------------
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

# 05_ref_data.sql (the 71 roles / 278 actions / 1,851 role-action pairs) was previously only
# ever loaded into the throwaway edu360_v4_test/edu360_v4_ref databases by sql/run_all.sh, never
# into this actual dev database - found the hard way when DevRoleAssignmentSeeder failed to
# start the whole app with "Key is not present in table standard_role" because ref.standard_role
# had zero rows here. It's part of the same approved, already-tested sql/ bundle, so applying it
# to the real dev database is not a new decision, just closing a real operational gap.
$refDataApplied = docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -tAc "SELECT count(*) FROM ref.standard_role"
if ($refDataApplied.Trim() -eq "0") {
    Write-Host "Applying EDU 360 reference data (71 roles / 278 actions) to edu360db..."
    Run-Sql "05_ref_data.sql"
    Write-Host "Reference data applied."
} else {
    Write-Host "EDU 360 reference data already present in edu360db ($($refDataApplied.Trim()) standard roles)."
}
# V4.0 roll-down reference rows (2 actions, 12 role-action pairs, 182 roll-down defaults).
# Idempotent (run_all.sh checks a second load inserts 0 rows). It was missing from this script.
Run-Sql "10d_v40_rolldown_ref.sql"

# V4.1 platform roles: added after the base bundle, so an existing dev database needs them too.
$platformRolesApplied = docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -tAc "SELECT to_regclass('platform.platform_role') IS NOT NULL"
if ($platformRolesApplied.Trim() -ne "t") {
    Write-Host "Applying V4.1 platform roles to edu360db..."
    Run-Sql "12_v41_platform_roles.sql"
}
foreach ($f in @("12d_v41_platform_role_templates.sql", "14_v41_audit_foundation.sql", "15_v41_user_active_role.sql",
                 "16_v41_tenant_top_post_authority.sql", "18_v41_user_sessions.sql", "20_v41_never_allowed_guard.sql", "22_v41_platform_audit_event_type.sql", "24_v41_sign_in_security.sql", "24d_v41_action_module_map.sql")) {
    Run-Sql $f
}
Write-Host "V4.1 migrations applied (12d, 14, 15, 16, 18, 20, 22)."

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
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT SELECT ON platform.tenant, platform.platform_user, platform.audit_outbox TO edu_app"
Write-Host "Granted edu_app read-only access to platform.tenant, platform.platform_user, and platform.audit_outbox."

# edu_platform (created by sql/01_schema.sql, already granted SELECT/INSERT/UPDATE on
# platform.platform_user and platform.plan by sql/03_bindings.sql) is the role DevAdminSeeder
# uses for its one dev-only INSERT - never edu_app, which the schema deliberately keeps
# read-only (or with zero access at all) on every platform.* table. Setting its password here,
# not granting it anything new: every grant it needs already exists in the approved bundle.
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "ALTER ROLE edu_platform WITH LOGIN PASSWORD '$platformPassword'"
Write-Host "edu_platform role credential set."

# public.audit_logs is LEGACY, not a D4 table (sql/14_v41_audit_foundation.sql now creates it in
# the AuditLog entity's shape). Tenant audit goes to app.audit_outbox (RLS, append-only, granted
# by sql/03_bindings.sql); platform audit to platform.audit_outbox. This sink only receives events
# that have no tenant user yet. INSERT needs SELECT as well because Hibernate reads back the
# generated id. Do not add more legacy tables here.
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT SELECT, INSERT ON public.audit_logs TO edu_app"
docker exec -e PGPASSWORD=edu360 $container psql -h localhost -U edu360 -d edu360db -v ON_ERROR_STOP=1 -q -c "GRANT USAGE, SELECT ON public.audit_logs_id_seq TO edu_app"
Write-Host "Granted edu_app access to the legacy public.audit_logs sink."
