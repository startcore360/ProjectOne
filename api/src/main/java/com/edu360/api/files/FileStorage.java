package com.edu360.api.files;

import java.io.InputStream;
import java.util.UUID;

/**
 * Storage boundary for uploaded file content.
 *
 * Implementations own the physical storage mechanism; callers persist only the opaque storage
 * key returned by {@link #store(UUID, UUID, InputStream)}. Implementations must enforce path or
 * object-key isolation and must not expose physical storage locations to API clients.
 */
public interface FileStorage {

    /**
     * Stores content for the given tenant and file identifier.
     *
     * @return opaque storage key suitable for persisting in app.file_object.storage_key
     */
    String store(UUID tenantId, UUID fileId, InputStream content);

    /** Opens a previously stored file as a stream. The caller must close the stream. */
    InputStream openRead(String storageKey);

    /** Reads a previously stored file fully into memory. Use only for bounded content. */
    byte[] read(String storageKey);

    /**
     * Permanently deletes a stored object (E5-S3 export expiry). Idempotent: deleting a key that no longer
     * exists is a no-op. Implementations must validate the key the same way reads do. Every implementation
     * (including the future S3FileStorage, E10-S6) must honour this contract.
     */
    void delete(String storageKey);
}
