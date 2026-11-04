package com.edu360.api.tenant;

/** What an ISOLATED tenant's secret actually holds once resolved - see
 *  AwsSecretsManagerTenantSecretsResolver for the exact JSON shape expected in Secrets Manager. */
public record TenantDatabaseCredentials(String jdbcUrl, String username, String password) {
}
