package com.edu360.api.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection details for AWS Secrets Manager. endpointOverride is the one property that
 * differs between environments: blank in production (the AWS SDK then talks to the real
 * regional AWS endpoint), set to the local LocalStack container's URL in dev/CI (see
 * docker/localstack/docker-compose.yml and application-dev.properties) - everything else about
 * this client's code is identical in both cases, which is the whole point of using LocalStack
 * instead of a separate hand-rolled fake.
 */
@Component
@ConfigurationProperties(prefix = "app.secrets")
public class SecretsManagerProperties {

    private String region = "ap-south-1";
    private String endpointOverride = "";

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }
}
