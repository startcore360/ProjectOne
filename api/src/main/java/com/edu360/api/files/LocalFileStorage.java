package com.edu360.api.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WHAT: stores a file's bytes on local disk and returns the relative path to record as
 * app.file_object.storage_key. This is a deliberate interim implementation, not the real
 * object-store the V4.0 schema's own comment on file_object.storage_key describes ("Object-store
 * key prefixed tenants/{tenant_id}/") - there is no S3/equivalent configured anywhere in this
 * codebase yet, and building that integration is separate scope from "let a user upload a
 * profile photo." storage_key itself still never leaves the server (same invariant the schema
 * comment requires) - only FileObjectController's own stream endpoint ever reads it back.
 *
 * baseDir defaults to ./data/uploads (dev-friendly, gitignored) - override with
 * FILE_STORAGE_BASE_DIR in any environment that needs a different location.
 */
@Component
public class LocalFileStorage {

    private final Path baseDir;

    public LocalFileStorage(@Value("${app.file-storage.base-dir:./data/uploads}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String store(UUID tenantId, UUID fileId, InputStream content) {
        Path tenantDir = baseDir.resolve(tenantId.toString());
        try {
            Files.createDirectories(tenantDir);
            Path target = tenantDir.resolve(fileId.toString());
            Files.copy(content, target);
            return baseDir.relativize(target).toString().replace('\\', '/');
        } catch (IOException ex) {
            throw new UncheckedFileStorageException("Could not store uploaded file", ex);
        }
    }

    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(baseDir.resolve(storageKey).normalize());
        } catch (IOException ex) {
            throw new UncheckedFileStorageException("Could not read stored file", ex);
        }
    }

    public static class UncheckedFileStorageException extends RuntimeException {
        public UncheckedFileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
