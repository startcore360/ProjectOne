package com.edu360.api.finance;

import com.edu360.api.tenant.SecretsManagerProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * Fetches gateway keys from AWS Secrets Manager (LocalStack in development/CI).
 * Secret JSON keys: razorpayKeyId, razorpayKeySecret, razorpayWebhookSecret,
 * stripeSecretKey and stripeWebhookSecret. Values are never logged or returned to clients.
 */
@Slf4j
@Component
public class AwsPaymentGatewayCredentialsResolver implements PaymentGatewayCredentialsResolver {
    private final SecretsManagerClient client;
    private final ObjectMapper mapper;
    private final String secretNameTemplate;

    public AwsPaymentGatewayCredentialsResolver(
            SecretsManagerProperties properties,
            ObjectMapper mapper,
            @Value("${app.payments.secret-name-template:edu360/tenants/{tenantId}/institutions/{institutionId}/payments}")
            String secretNameTemplate) {
        var builder = SecretsManagerClient.builder().region(Region.of(properties.getRegion()));
        if (properties.getEndpointOverride() != null && !properties.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpointOverride()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        this.client = builder.build();
        this.mapper = mapper;
        this.secretNameTemplate = secretNameTemplate;
    }

    @Override
    public PaymentGatewayCredentials resolve(UUID tenantId, UUID institutionId) {
        if (tenantId == null || institutionId == null) {
            throw new IllegalArgumentException("Tenant and institution are required to resolve gateway credentials.");
        }
        String secretName = secretNameTemplate
                .replace("{tenantId}", tenantId.toString())
                .replace("{institutionId}", institutionId.toString());
        try {
            String secret = client.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build())
                    .secretString();
            Map<String, String> fields = mapper.readValue(secret, new TypeReference<>() {});
            return new PaymentGatewayCredentials(
                    fields.get("razorpayKeyId"),
                    fields.get("razorpayKeySecret"),
                    fields.get("razorpayWebhookSecret"),
                    fields.get("stripeSecretKey"),
                    fields.get("stripeWebhookSecret"));
        } catch (Exception ex) {
            // Do not expose the secret name, provider credentials, or SDK response details.
            log.warn("Payment gateway credentials are unavailable for tenant {} institution {}.",
                    tenantId, institutionId);
            throw new IllegalStateException("Payment gateway is not configured for this institution.");
        }
    }
}
