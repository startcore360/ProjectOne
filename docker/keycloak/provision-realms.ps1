# WHAT: post-import Keycloak configuration that cannot be expressed in the realm-export-*.json
# files themselves (or at least not reliably across Keycloak versions) - run every time Keycloak
# starts, since it has no persistent volume and re-imports from scratch on every container
# recreate.
#
# WHY this is needed at all, found by actually testing the must_change_password flow end-to-end
# (not by reading docs): Keycloak 26's Declarative User Profile feature silently DROPS any user
# attribute not explicitly declared in the realm's user-profile schema - both from storage and
# from every API response, including the custom "must_change_password" attribute
# KeycloakAdminClient relies on (see its class comment). The fix is
# unmanagedAttributePolicy=ENABLED, which allows arbitrary attributes through without declaring
# each one by name - set via PUT .../users/profile, a call with no equivalent field in the
# realm-export JSON import format.
$ErrorActionPreference = "Stop"
$keycloakBaseUrl = "http://127.0.0.1:8081"
$realms = @("abc-university", "xyz-university", "edu360-platform")

function Get-MasterAdminToken {
    $response = Invoke-RestMethod -Method Post -Uri "$keycloakBaseUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body "client_id=admin-cli&grant_type=password&username=kcadmin&password=kcadmin"
    return $response.access_token
}

$token = Get-MasterAdminToken
foreach ($realm in $realms) {
    $profile = Invoke-RestMethod -Method Get -Uri "$keycloakBaseUrl/admin/realms/$realm/users/profile" `
        -Headers @{ Authorization = "Bearer $token" }
    $profile | Add-Member -NotePropertyName unmanagedAttributePolicy -NotePropertyValue "ENABLED" -Force
    Invoke-RestMethod -Method Put -Uri "$keycloakBaseUrl/admin/realms/$realm/users/profile" `
        -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" `
        -Body ($profile | ConvertTo-Json -Depth 10) | Out-Null
    Write-Host "Enabled unmanaged attributes (must_change_password) for realm '$realm'."
}

# Ensure edu360-identity-events confidential client and identity-events:write role exist in edu360-platform realm (M-03 / PD-2)
try {
    $platformRealm = "edu360-platform"
    $clients = Invoke-RestMethod -Method Get -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/clients?clientId=edu360-identity-events" `
        -Headers @{ Authorization = "Bearer $token" }
    $clientUuid = $null
    if (-not $clients -or $clients.Count -eq 0) {
        $clientBody = @{
            clientId = "edu360-identity-events"
            enabled = $true
            publicClient = $false
            protocol = "openid-connect"
            secret = "dev-identity-events-secret"
            serviceAccountsEnabled = $true
            standardFlowEnabled = $false
            directAccessGrantsEnabled = $false
            clientAuthenticatorType = "client-secret"
        } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/clients" `
            -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $clientBody | Out-Null
        $clients = Invoke-RestMethod -Method Get -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/clients?clientId=edu360-identity-events" `
            -Headers @{ Authorization = "Bearer $token" }
    }
    if ($clients -and $clients.Count -gt 0) {
        $clientUuid = $clients[0].id
    }

    # Ensure identity-events:write realm role exists
    $roles = Invoke-RestMethod -Method Get -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/roles" `
        -Headers @{ Authorization = "Bearer $token" }
    $hasRole = $false
    foreach ($r in $roles) {
        if ($r.name -eq "identity-events:write") { $hasRole = $true; break }
    }
    if (-not $hasRole) {
        $roleBody = @{ name = "identity-events:write"; description = "EDU 360 platform: identity events webhook" } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/roles" `
            -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $roleBody | Out-Null
    }

    # Assign role to service account user
    if ($clientUuid) {
        $saUser = Invoke-RestMethod -Method Get -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/clients/$clientUuid/service-account-user" `
            -Headers @{ Authorization = "Bearer $token" }
        if ($saUser -and $saUser.id) {
            $roleObj = Invoke-RestMethod -Method Get -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/roles/identity-events:write" `
                -Headers @{ Authorization = "Bearer $token" }
            $assignBody = "[$($roleObj | ConvertTo-Json -Compress)]"
            Invoke-RestMethod -Method Post -Uri "$keycloakBaseUrl/admin/realms/$platformRealm/users/$($saUser.id)/role-mappings/realm" `
                -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $assignBody | Out-Null
        }
    }
    Write-Host "Configured edu360-identity-events client and role in '$platformRealm'."
} catch {
    Write-Warning "Could not configure edu360-identity-events client: $_"
}
