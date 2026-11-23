package com.edu360.api.files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.edu360.api.exception.ApiException;

/**
 * Fail-closed fallback used only when ClamAV is explicitly disabled or not configured.
 * An upload must never be persisted with an indefinitely pending scan result.
 */
@Component
@ConditionalOnProperty(prefix = "files.scanner.clamav", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PendingFileScanner implements FileScanner {
    @Override
    public ScanResult scan(byte[] content) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Malware scanning is not configured. File uploads are temporarily unavailable.");
    }
}
