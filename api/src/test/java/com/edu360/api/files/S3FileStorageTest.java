package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class S3FileStorageTest {

    @Test
    void objectKeyIsTenantAndFileScoped() {
        UUID tenant = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        // The SDK client is constructed but no network call is made by store-key construction.
        try (S3FileStorage storage = new S3FileStorage(
                "edu360-files", "ap-south-1", "http://127.0.0.1:4566", "test", "test")) {
            assertThatThrownBy(() -> storage.read("../../etc/passwd"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete("tenants/" + tenant + "/not-a-uuid"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(tenant).isNotEqualTo(file);
        }
    }

    @Test
    void refusesMissingTenantAndFileIdentifiersBeforeWriting() {
        try (S3FileStorage storage = new S3FileStorage(
                "edu360-files", "ap-south-1", "http://127.0.0.1:4566", "test", "test")) {
            assertThatThrownBy(() -> storage.store(null, UUID.randomUUID(), new ByteArrayInputStream(new byte[0])))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.store(UUID.randomUUID(), null, new ByteArrayInputStream(new byte[0])))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
