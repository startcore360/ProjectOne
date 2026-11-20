# One-time production Keycloak client bootstrap. Run from a secured admin workstation.
# The bootstrap admin password is used only for this operation; the application uses the
# realm-scoped confidential clients created below after setup.
$ErrorActionPreference = "Stop"

function Require-Env([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Required environment variable $Name is missing." }
    return $value
}

$baseUrl = (Require-Env "KEYCLOAK_BASE_URL").TrimEnd("/")
$bootstrapUser = Require-Env "KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME"
$bootstrapPassword = Require-Env "KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD"
$adminClientId = Require-Env "KEYCLOAK_ADMIN_CLIENT_ID"
$adminClientSecret = Require-Env "KEYCLOAK_ADMIN_CLIENT_SECRET"
$identityClientId = Require-Env "KEYCLOAK_IDENTITY_EVENTS_CLIENT_ID"
$identityClientSecret = Require-Env "KEYCLOAK_IDENTITY_EVENTS_CLIENT_SECRET"
$frontendClientId = Require-Env "KEYCLOAK_FRONTEND_CLIENT_ID"
$redirectUris = @( (Require-Env "KEYCLOAK_FRONTEND_REDIRECT_URIS").Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ } )
$tenantRealms = @()
$realmEnv = [Environment]::GetEnvironmentVariable("KEYCLOAK_TENANT_REALMS")
if ($realmEnv) { $tenantRealms = @($realmEnv.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
$realms = @("edu360-platform") + $tenantRealms | Select-Object -Unique

$tokenResponse = Invoke-RestMethod -Method Post -Uri "$baseUrl/realms/master/protocol/openid-connect/token" -ContentType "application/x-www-form-urlencoded" -Body @{
    grant_type = "password"
    client_id = "admin-cli"
    username = $bootstrapUser
    password = $bootstrapPassword
}
$script:AccessToken = $tokenResponse.access_token
if (-not $script:AccessToken) { throw "Keycloak did not issue the bootstrap admin token." }

function Invoke-Keycloak([string]$Method, [string]$Path, $Body = $null) {
    $headers = @{ Authorization = "Bearer $script:AccessToken" }
    $uri = "$baseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    }
    $json = ConvertTo-Json -InputObject $Body -Depth 30
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body $json
}

function Get-Client([string]$Realm, [string]$ClientId) {
    $encoded = [uri]::EscapeDataString($ClientId)
    $matches = Invoke-Keycloak "Get" "/admin/realms/$Realm/clients?clientId=$encoded"
    if (@($matches).Count -gt 0) { return @($matches)[0] }
    return $null
}

function Ensure-Client([string]$Realm, [string]$ClientId, [string]$Secret, [bool]$ServiceAccountsEnabled) {
    $client = Get-Client $Realm $ClientId
    if ($null -eq $client) {
        $representation = @{
            clientId = $ClientId
            enabled = $true
            publicClient = $false
            clientAuthenticatorType = "client-secret"
            secret = $Secret
            serviceAccountsEnabled = $ServiceAccountsEnabled
            standardFlowEnabled = $false
            directAccessGrantsEnabled = $false
        }
        Invoke-Keycloak "Post" "/admin/realms/$Realm/clients" $representation | Out-Null
        $client = Get-Client $Realm $ClientId
    } else {
        $client | Add-Member -NotePropertyName secret -NotePropertyValue $Secret -Force
        $client.enabled = $true
        $client.publicClient = $false
        $client.clientAuthenticatorType = "client-secret"
        $client.serviceAccountsEnabled = $ServiceAccountsEnabled
        $client.standardFlowEnabled = $false
        $client.directAccessGrantsEnabled = $false
        Invoke-Keycloak "Put" "/admin/realms/$Realm/clients/$($client.id)" $client | Out-Null
        $client = Get-Client $Realm $ClientId
    }
    if (-not $client.id) { throw "Could not resolve client id for $ClientId in realm $Realm." }
    return $client
}

function Ensure-ClientRoles([string]$Realm, [string]$ClientUuid, [string[]]$RoleNames) {
    $serviceAccount = Invoke-Keycloak "Get" "/admin/realms/$Realm/clients/$ClientUuid/service-account-user"
    if (-not $serviceAccount.id) { throw "Service account is missing for client $ClientUuid in realm $Realm." }
    $realmManagement = Get-Client $Realm "realm-management"
    if (-not $realmManagement.id) { throw "realm-management client missing in realm $Realm." }

    $existing = Invoke-Keycloak "Get" "/admin/realms/$Realm/users/$($serviceAccount.id)/role-mappings/clients/$($realmManagement.id)"
    $existingNames = @($existing | ForEach-Object { $_.name })
    $missing = @()
    foreach ($name in $RoleNames) {
        if ($existingNames -notcontains $name) {
            $role = Invoke-Keycloak "Get" "/admin/realms/$Realm/clients/$($realmManagement.id)/roles/$name"
            if (-not $role.id) { throw "Required Keycloak role '$name' is missing in realm $Realm." }
            $missing += $role
        }
    }
    if ($missing.Count -gt 0) {
        Invoke-Keycloak "Post" "/admin/realms/$Realm/users/$($serviceAccount.id)/role-mappings/clients/$($realmManagement.id)" $missing | Out-Null
    }
    return $serviceAccount
}

function Ensure-FrontendRedirects([string]$Realm) {
    $client = Get-Client $Realm $frontendClientId
    if (-not $client) { throw "Frontend client $frontendClientId is missing in realm $Realm." }
    $client.redirectUris = $redirectUris
    Invoke-Keycloak "Put" "/admin/realms/$Realm/clients/$($client.id)" $client | Out-Null
}

# Master realm client: can create/delete realms, but cannot administer users in a tenant realm.
$masterClient = Ensure-Client "master" $adminClientId $adminClientSecret $true
Ensure-ClientRoles "master" $masterClient.id @("create-realm", "delete-realm", "view-realm", "query-realms") | Out-Null

foreach ($realm in $realms) {
    $tenantClient = Ensure-Client $realm $adminClientId $adminClientSecret $true
    Ensure-ClientRoles $realm $tenantClient.id @("manage-users", "view-users", "query-users", "view-realm", "manage-clients") | Out-Null
    Ensure-FrontendRedirects $realm
}

# The webhook client is separate from user administration and receives only its dedicated
# realm role in the platform realm.
$identityClient = Ensure-Client "edu360-platform" $identityClientId $identityClientSecret $true
$identityServiceAccount = Invoke-Keycloak "Get" "/admin/realms/edu360-platform/clients/$($identityClient.id)/service-account-user"
try {
    $identityRole = Invoke-Keycloak "Get" "/admin/realms/edu360-platform/roles/identity-events%3Awrite"
} catch {
    $identityRole = $null
}
if (-not $identityRole) {
    Invoke-Keycloak "Post" "/admin/realms/edu360-platform/roles" @{
        name = "identity-events:write"
        description = "EDU 360 internal identity event webhook"
    } | Out-Null
    $identityRole = Invoke-Keycloak "Get" "/admin/realms/edu360-platform/roles/identity-events%3Awrite"
}
$currentRoles = Invoke-Keycloak "Get" "/admin/realms/edu360-platform/users/$($identityServiceAccount.id)/role-mappings/realm"
if (@($currentRoles | ForEach-Object { $_.name }) -notcontains "identity-events:write") {
    Invoke-Keycloak "Post" "/admin/realms/edu360-platform/users/$($identityServiceAccount.id)/role-mappings/realm" @($identityRole) | Out-Null
}

Write-Host "Production Keycloak clients configured for realms: $($realms -join ', ')."
Write-Host "Admin service account roles: create/delete realm in master; manage users only within each listed realm."
Write-Host "Identity events service account has only identity-events:write in edu360-platform."
Write-Host "Store the configured client secrets in the deployment secret manager; remove bootstrap credentials from the operator environment."
