# Wipes everything CREATED by using the app - every tenant, institution, user, plan, custom
# platform/tenant role, and all their data (app.* and platform.* schemas) - while keeping the
# database schema itself and the static reference catalog (ref.*: action codes, standard roles,
# approval processes) the system needs just to function. Deletes every Keycloak realm except
# master and the platform realm, and every user inside the platform realm. Leaves exactly one
# signed-in-able account: a single Product Owner, whose one-time password is printed at the end.
#
#   .\dev-reset-data.ps1
#
# This is destructive and cannot be undone - only for local dev data you don't need. Needs the
# stack from .\dev-up.ps1 running (Postgres + Keycloak). Running .\dev-up.ps1 or .\dev-restart.ps1
# afterwards starts with exactly what this script left behind: DevAdminSeeder (api, dev profile
# only) seeds its own default accounts just once, on a genuinely empty database - it never tops
# up an account later found "missing", so your single Product Owner is never quietly added to.
param([string]$OwnerEmail = "owner@edu360-platform.local", [string]$OwnerName = "Product Owner", [switch]$Force)

$ErrorActionPreference = 'Stop'
$pgContainer = 'personalproject-postgres-1'
$kcBase = 'http://127.0.0.1:8081'
$platformRealm = 'edu360-platform'

Write-Host "This permanently deletes ALL tenants, institutions, users, plans and custom roles, keeping only the schema and reference data." -ForegroundColor Yellow
if (-not $Force) {
    $confirm = Read-Host "Type RESET to continue"
    if ($confirm -ne 'RESET') { Write-Host "Cancelled."; exit 1 }
}

# 1. Wipe every table in app.* and platform.* (everything the app itself ever wrote), in one
#    TRUNCATE so cross-table foreign keys never block it; ref.* (the static catalog) is untouched.
$truncateSql = @'
DO $do$
DECLARE tbls text;
BEGIN
  SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
  INTO tbls
  FROM pg_tables
  WHERE schemaname IN ('app','platform');
  IF tbls IS NOT NULL THEN
    EXECUTE 'TRUNCATE TABLE ' || tbls || ' RESTART IDENTITY CASCADE';
  END IF;
END
$do$;
'@
$truncateSql | docker exec -i $pgContainer psql -U edu360 -d edu360db -v ON_ERROR_STOP=1
Write-Host "Database data wiped (schema and ref.* reference data kept)." -ForegroundColor Green

# 2. Keycloak: delete every tenant realm (everything except master and the platform realm).
# A fresh admin token is fetched before EVERY call below (not once up front): deleting dozens of
# realms one by one is slow enough that Keycloak's short-lived admin access token expired partway
# through a single shared token, found by actually running this against ~55 tenant realms and
# hitting "401 Unauthorized" mid-loop - not by reading Keycloak's docs.
function Get-MasterAdminToken {
    $response = Invoke-RestMethod -Method Post -Uri "$kcBase/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body "client_id=admin-cli&grant_type=password&username=kcadmin&password=kcadmin"
    return $response.access_token
}
function Invoke-KcRequest {
    param([string]$Method, [string]$Uri, [object]$Body = $null, [string]$ContentType = $null)
    $headers = @{ Authorization = "Bearer $(Get-MasterAdminToken)" }
    if ($Body -ne $null) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType $ContentType -Body $Body
    }
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
}

$realms = Invoke-KcRequest -Method Get -Uri "$kcBase/admin/realms"
foreach ($realm in $realms) {
    if ($realm.realm -eq 'master' -or $realm.realm -eq $platformRealm) { continue }
    Invoke-KcRequest -Method Delete -Uri "$kcBase/admin/realms/$($realm.realm)" | Out-Null
    Write-Host "Deleted Keycloak realm '$($realm.realm)'."
}

# 3. Platform realm: delete every existing user (their platform.platform_user rows are already
#    gone from step 1), then create exactly one Product Owner.
$users = Invoke-KcRequest -Method Get -Uri "$kcBase/admin/realms/$platformRealm/users?max=1000"
foreach ($user in $users) {
    Invoke-KcRequest -Method Delete -Uri "$kcBase/admin/realms/$platformRealm/users/$($user.id)" | Out-Null
}
Write-Host "Cleared $($users.Count) existing platform realm user(s)."

$ownerPassword = "Owner-$(Get-Random -Minimum 100000 -Maximum 999999)!Aa"
$nameParts = $OwnerName.Trim() -split '\s+', 2
$firstName = $nameParts[0]
$lastName = if ($nameParts.Count -gt 1) { $nameParts[1] } else { $nameParts[0] }
$userBody = @{
    username = $OwnerEmail
    email = $OwnerEmail
    emailVerified = $true
    enabled = $true
    firstName = $firstName
    lastName = $lastName
    credentials = @(@{ type = "password"; value = $ownerPassword; temporary = $false })
    attributes = @{ must_change_password = @("false") }
} | ConvertTo-Json -Depth 5

$createResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$kcBase/admin/realms/$platformRealm/users" `
    -Headers @{ Authorization = "Bearer $(Get-MasterAdminToken)" } -ContentType "application/json" -Body $userBody
$location = $createResponse.Headers.Location
if ($location -is [System.Array]) { $location = $location[0] }
$keycloakSubject = $location.Substring($location.LastIndexOf('/') + 1)
Write-Host "Created Keycloak user for '$OwnerEmail' (id $keycloakSubject)."

# 4. The matching platform.platform_user row - same shape DevAdminSeeder uses for its own seeded
#    accounts, so this owner signs in exactly like any other platform user.
$platformUserId = [guid]::NewGuid().ToString()
$insertSql = @"
INSERT INTO platform.platform_user
  (platform_user_id, email, full_name, platform_role, status, mfa_required, identity_subject, created_by, updated_by)
VALUES
  ('$platformUserId', '$OwnerEmail', '$($OwnerName.Replace("'", "''"))', 'PRODUCT_OWNER', 'ACTIVE', true, '$keycloakSubject', '$platformUserId', '$platformUserId');
"@
$insertSql | docker exec -i $pgContainer psql -U edu360 -d edu360db -v ON_ERROR_STOP=1

Write-Host ""
Write-Host "Reset complete. One Product Owner account is ready:" -ForegroundColor Green
Write-Host "  Email:    $OwnerEmail"
Write-Host "  Password: $ownerPassword"
Write-Host "Sign in at the platform console (http://platform.edu360.localhost:5173 or your tunnel's /__tenant/platform link)."
