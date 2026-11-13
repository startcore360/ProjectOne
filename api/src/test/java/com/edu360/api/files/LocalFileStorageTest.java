package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
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
        Path link = tenants.resolve(tenant.toString());
        try {
            java.nio.file.Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable to this test process: " + ex.getMessage());
        }
        Assumptions.assumeTrue(java.nio.file.Files.isSymbolicLink(link), "The test filesystem did not create a symbolic link.");
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
