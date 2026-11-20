package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Real object-store round trip; set LOCALSTACK_ENDPOINT and S3_FILE_BUCKET to run. */
class S3FileStorageIT {

    @Test
    void storesReadsAndDeletesTenantScopedObjects() {
        String endpoint = System.getenv("LOCALSTACK_ENDPOINT");
        Assumptions.assumeTrue(endpoint != null && !endpoint.isBlank(),
                "Set LOCALSTACK_ENDPOINT to run the S3/LocalStack integration test.");
        String bucket = System.getenv().getOrDefault("S3_FILE_BUCKET", "edu360-files");
        String region = System.getenv().getOrDefault("S3_REGION", "ap-south-1");
        try (S3FileStorage storage = new S3FileStorage(bucket, region, endpoint, "test", "test")) {
            UUID tenantId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            byte[] payload = "EDU360 S3 integration test".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            String key = storage.store(tenantId, fileId, new ByteArrayInputStream(payload));
            assertThat(key).isEqualTo("tenants/" + tenantId + "/" + fileId);
            assertThat(storage.read(key)).containsExactly(payload);

            storage.delete(key);
            assertThatThrownByRead(storage, key);
        }
    }

    private static void assertThatThrownByRead(S3FileStorage storage, String key) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.read(key))
                .isInstanceOf(S3FileStorage.S3FileStorageException.class);
    }
}
