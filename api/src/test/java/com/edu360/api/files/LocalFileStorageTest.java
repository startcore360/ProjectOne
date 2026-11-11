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
    void rejectsPathTraversalStorageKeys() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        assertThatThrownBy(() -> storage.read("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
