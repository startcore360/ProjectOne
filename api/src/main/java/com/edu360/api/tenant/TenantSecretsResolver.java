package com.edu360.api.tenant;

/**
 * WHAT: turns platform.tenant.db_connection_ref (a Vault/Secrets-Manager reference, per the
 * schema's own comment - "never a credential") into the real JDBC URL and credential for that
 * ISOLATED tenant's own database.
 *
 * WHY an interface at all, given there is only one real implementation (AWS Secrets Manager):
 * so tests can substitute a fake resolver without needing network access or real AWS/LocalStack
 * infrastructure to run - see how AuthService's old direct-Keycloak-call design kept Keycloak
 * itself swappable in the same way.
 */
public interface TenantSecretsResolver {
    TenantDatabaseCredentials resolve(String dbConnectionRef);
}
