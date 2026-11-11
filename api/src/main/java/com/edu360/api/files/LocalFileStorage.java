package com.edu360.api.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage {
    private final Path baseDir;

    public LocalFileStorage(@Value("${app.file-storage.base-dir:./data/uploads}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String store(UUID tenantId, UUID fileId, InputStream content) {
        Path tenantsDir = baseDir.resolve("tenants").normalize();
        Path tenantDir = tenantsDir.resolve(tenantId.toString()).normalize();
        if (!tenantDir.startsWith(tenantsDir) || !tenantsDir.startsWith(baseDir)) throw new IllegalArgumentException("Invalid tenant storage path.");
        try {
            Files.createDirectories(tenantDir);
            Path target = tenantDir.resolve(fileId.toString()).normalize();
            if (!target.startsWith(tenantDir)) throw new IllegalArgumentException("Invalid file storage path.");
            Files.copy(content, target);
            return baseDir.relativize(target).toString().replace('\\', '/');
        } catch (IOException ex) { throw new UncheckedFileStorageException("Could not store uploaded file", ex); }
    }

    public InputStream openRead(String storageKey) {
        Path target = baseDir.resolve(storageKey).normalize();
        if (!target.startsWith(baseDir)) throw new IllegalArgumentException("Invalid storage key.");
        try {
            Path realBase = baseDir.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realBase) || !Files.isRegularFile(realTarget)) throw new IllegalArgumentException("Invalid storage key.");
            return Files.newInputStream(realTarget);
        } catch (IOException ex) { throw new UncheckedFileStorageException("Could not read stored file", ex); }
    }

    public byte[] read(String storageKey) {
        try (InputStream input = openRead(storageKey)) { return input.readAllBytes(); }
        catch (IOException ex) { throw new UncheckedFileStorageException("Could not read stored file", ex); }
    }

    public static class UncheckedFileStorageException extends RuntimeException {
        public UncheckedFileStorageException(String message, Throwable cause) { super(message, cause); }
    }
}
