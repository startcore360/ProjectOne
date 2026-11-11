package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {
    @TempDir Path tempDir;

    @Test
    void storesAndReadsOnlyWithinStorageRoot() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        UUID tenant = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        String key = storage.store(tenant, file, new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertThat(storage.read(key)).containsExactly(1, 2, 3);
    }

    @Test
    void refusesTenantDirectorySymlinkThatEscapesStorageRoot() throws Exception {
        Path tenants = tempDir.resolve("tenants");
        Path outside = tempDir.getParent();
        UUID tenant = UUID.randomUUID();
        java.nio.file.Files.createDirectories(tenants);
        java.nio.file.Files.createSymbolicLink(tenants.resolve(tenant.toString()), outside);
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        assertThatThrownBy(() -> storage.store(tenant, UUID.randomUUID(),
                new java.io.ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void rejectsPathTraversalStorageKeys() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        assertThatThrownBy(() -> storage.read("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
