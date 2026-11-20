package com.edu360.api.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * WHAT: stores a file's bytes on local disk and returns the relative path to record as
 * app.file_object.storage_key. This provider is selected for local/test profiles only; production
 * selects S3FileStorage. The key remains server-side and is never returned by an API.
 *
 * HOW: tenant files are isolated below tenants/{tenant_id}/ and identified by random UUIDs.
 * Every read normalizes the key, checks lexical containment, resolves symlinks, and checks the
 * real target remains beneath the real storage root before opening it.
 *
 * WHY: the local provider preserves tenant partitioning and prevents path traversal for tests
 * and local development. Production object storage is provided by S3FileStorage; expiring
 * download links remain a separate follow-up.
 */
@Component
@ConditionalOnProperty(prefix = "app.file-storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path baseDir;

    public LocalFileStorage(@Value("${app.file-storage.base-dir:./data/uploads}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String store(UUID tenantId, UUID fileId, InputStream content) {
        Path tenantsDir = baseDir.resolve("tenants").normalize();
        Path tenantDir = tenantsDir.resolve(tenantId.toString()).normalize();
        if (!tenantDir.startsWith(tenantsDir) || !tenantsDir.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid tenant storage path.");
        }
        try {
            Files.createDirectories(baseDir);
            Files.createDirectories(tenantDir);
            Path realBase = baseDir.toRealPath();
            Path realTenantDir = tenantDir.toRealPath();
            if (!realTenantDir.startsWith(realBase)) {
                throw new IllegalArgumentException("Tenant storage directory escapes the storage root.");
            }
            Path target = realTenantDir.resolve(fileId.toString()).normalize();
            if (!target.startsWith(realTenantDir)) throw new IllegalArgumentException("Invalid file storage path.");
            Files.copy(content, target);
            return baseDir.relativize(tenantDir.resolve(fileId.toString())).toString().replace('\\', '/');
        } catch (IOException ex) {
            throw new UncheckedFileStorageException("Could not store uploaded file", ex);
        }
    }

    public InputStream openRead(String storageKey) {
        Path target = baseDir.resolve(storageKey).normalize();
        if (!target.startsWith(baseDir)) throw new IllegalArgumentException("Invalid storage key.");
        try {
            Path realBase = baseDir.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realBase) || !Files.isRegularFile(realTarget)) {
                throw new IllegalArgumentException("Invalid storage key.");
            }
            return Files.newInputStream(realTarget);
        } catch (IOException ex) {
            throw new UncheckedFileStorageException("Could not read stored file", ex);
        }
    }

    public byte[] read(String storageKey) {
        try (InputStream input = openRead(storageKey)) {
            return input.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedFileStorageException("Could not read stored file", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = baseDir.resolve(storageKey).normalize();
        if (!target.startsWith(baseDir)) throw new IllegalArgumentException("Invalid storage key.");
        try {
            if (!Files.exists(target)) return; // idempotent
            Path realBase = baseDir.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realBase) || !Files.isRegularFile(realTarget)) {
                throw new IllegalArgumentException("Invalid storage key.");
            }
            Files.deleteIfExists(realTarget);
        } catch (IOException ex) {
            throw new UncheckedFileStorageException("Could not delete stored file", ex);
        }
    }

    public static class UncheckedFileStorageException extends RuntimeException {
        public UncheckedFileStorageException(String message, Throwable cause) { super(message, cause); }
    }
}
