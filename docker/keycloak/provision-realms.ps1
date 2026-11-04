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
