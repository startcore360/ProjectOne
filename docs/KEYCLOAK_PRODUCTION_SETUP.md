# Production Keycloak setup

This is a one-time deployment operation. Run it against the production Keycloak instance from a secured admin workstation before enabling tenant provisioning.

## Service-account model

The application does not use the bootstrap admin password in production.

- Master realm client `edu360-admin-cli`: client-credentials grant, service account roles limited to `create-realm`, `delete-realm`, `view-realm`, and `query-realms`. This client is used only to create/delete tenant realms.
- Each tenant realm client `edu360-admin-cli`: same client ID and configured secret, separate realm-local service-account user, roles limited to `manage-users`, `view-users`, `query-users`, `view-realm`, and `manage-clients`. User provisioning, password reset, OTP enrollment, and session administration obtain a token from the target realm, not from master.
- Platform realm client `edu360-admin-cli`: the same realm-scoped user-management role set for platform identities.
- Platform realm client `edu360-identity-events`: separate client-credentials service account with only the realm role `identity-events:write`. It is not reused for user administration.
- Frontend client `edu360-frontend`: public client with the production HTTPS wildcard redirect allowlist. Existing clients are updated in place; the API preserves the rest of each client representation.

The master service account is deliberately not granted tenant user-management roles. Each newly created tenant realm receives its own service-account user and role mappings in the realm representation. Existing realms must be migrated once with the setup script. **Important credential boundary:** this implementation uses one shared client secret across the realm-local client instances. Realm tokens are scoped to the realm from which they were issued, but compromise of the shared secret permits an attacker to request a token in any configured realm. Treat it as a platform-wide privileged secret, restrict it to the API workload, and rotate it. Per-tenant secret distribution is a separate hardening item.

## Required operator environment

Set these in a temporary, protected shell environment. Generate the two client secrets with a cryptographically secure password generator (at least 32 random bytes); never commit them.

- `KEYCLOAK_BASE_URL`: internal Keycloak base URL, without a trailing slash.
- `KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME` and `KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD`: one-time master-realm bootstrap administrator credentials.
- `KEYCLOAK_ADMIN_CLIENT_ID=edu360-admin-cli`
- `KEYCLOAK_ADMIN_CLIENT_SECRET`: shared secret provisioned into the master realm and each managed realm.
- `KEYCLOAK_IDENTITY_EVENTS_CLIENT_ID=edu360-identity-events`
- `KEYCLOAK_IDENTITY_EVENTS_CLIENT_SECRET`: separate secret.
- `KEYCLOAK_FRONTEND_CLIENT_ID=edu360-frontend`
- `KEYCLOAK_FRONTEND_REDIRECT_URIS=https://*.<registered-domain>/*`: use the actual registered domain; include the platform label in the wildcard. No localhost or HTTP entries.
- `KEYCLOAK_TENANT_REALMS`: comma-separated list of existing tenant realm names, obtained from `platform.tenant.keycloak_realm`. The platform realm is included automatically.

Example realm inventory query (run with a read-only database account):

```sql
SELECT keycloak_realm
FROM platform.tenant
WHERE keycloak_realm IS NOT NULL
  AND state NOT IN ('DELETED', 'ARCHIVED')
ORDER BY keycloak_realm;
```

## Run the bootstrap

From the repository root, after setting the environment variables:

```powershell
pwsh -NoProfile -File docker/keycloak/configure-production-clients.ps1
```

The script is idempotent. It updates the existing realm-local admin client secret, ensures the scoped role mappings, updates frontend redirect URIs for all listed realms, and provisions the separate identity-events client. It fails if required realm-management roles or frontend clients are missing.

After it succeeds:

1. Store the two client secrets in the production secret manager.
2. Set `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET`, `KEYCLOAK_IDENTITY_EVENTS_CLIENT_ID`, and `KEYCLOAK_IDENTITY_EVENTS_CLIENT_SECRET` in the API deployment environment.
3. Remove bootstrap admin credentials from the operator shell and rotate/disable the one-time bootstrap credential according to the Keycloak operations policy.
4. Verify client-credentials token issuance in master, the platform realm, and at least one tenant realm. Confirm that a token issued in tenant realm A cannot administer tenant realm B, and confirm the shared client secret is not exposed to tenant applications or users.
5. Run `mvn --batch-mode --no-transfer-progress -f api/pom.xml verify -Pit` against the integration stack.

New realms provisioned by the application embed the tenant-local admin client and its user-management role mappings. Existing realms must be included in `KEYCLOAK_TENANT_REALMS` during the one-time migration; otherwise realm-scoped client-credentials authentication will fail closed for those realms.

## Operational constraints

- Custom-domain redirect URI registration is not part of the wildcard tenant domain flow. Do not broaden the redirect allowlist to arbitrary customer domains; custom-domain ownership and redirect policy remain a separate governance decision.
- Do not grant `realm-admin` or a global `admin` role to the tenant-local service accounts.
- Do not reuse the identity-events secret as the admin client secret.
- Bootstrap credentials and client secrets are secrets, not application configuration defaults.
