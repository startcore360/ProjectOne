package com.edu360.api.tenant;

import java.net.URI;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * WHAT: the one real implementation of TenantSecretsResolver - fetches a secret from AWS
 * Secrets Manager (or, in dev/CI, LocalStack standing in for it at the same API) and parses it
 * into the three fields an ISOLATED tenant's datasource needs.
 *
 * HOW the dev/prod split works with ONE code path: SecretsManagerProperties.endpointOverride is
 * blank in prod, so the SDK client below talks to the real regional AWS endpoint; in dev it is
 * set to the LocalStack container's URL (docker/localstack/docker-compose.yml). Nothing else in
 * this class branches on environment - that is the entire point of testing against LocalStack
 * instead of a hand-rolled fake that could silently drift from how the real AWS SDK behaves.
 *
 * Expected secret shape (a JSON object stored as the secret string, created when a tenant is
 * provisioned as ISOLATED - that provisioning flow is a separate Platform-epic story, not part
 * of this identity migration):
 *   {"jdbcUrl": "jdbc:postgresql://host:5432/dbname", "username": "edu_app", "password": "..."}
 */
@Component
public class AwsSecretsManagerTenantSecretsResolver implements TenantSecretsResolver {

    private final SecretsManagerClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AwsSecretsManagerTenantSecretsResolver(SecretsManagerProperties properties) {
        var builder = SecretsManagerClient.builder().region(Region.of(properties.getRegion()));
        if (!properties.getEndpointOverride().isBlank()) {
            // LocalStack does not enforce real AWS auth, but the SDK still requires some
            // credentials to be present before it will even attempt a request - these
            // deliberately fixed, well-known "test"/"test" values match
            // docker/localstack/docker-compose.yml and are never used against real AWS (that
            // path only runs when endpointOverride is blank, which never happens in prod).
            builder.endpointOverride(URI.create(properties.getEndpointOverride()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        }
        this.client = builder.build();
    }

    @Override
    public TenantDatabaseCredentials resolve(String dbConnectionRef) {
        String secretJson = client.getSecretValue(
                GetSecretValueRequest.builder().secretId(dbConnectionRef).build()).secretString();
        try {
            Map<String, String> fields = objectMapper.readValue(secretJson, Map.class);
            return new TenantDatabaseCredentials(
                    fields.get("jdbcUrl"), fields.get("username"), fields.get("password"));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Secret " + dbConnectionRef + " is not in the expected {jdbcUrl, username, password} shape",
                    ex);
        }
    }
}
