package com.edu360.api.files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** DEC-7 bounded exception: no malware scanner is configured, so new files remain PENDING. */
@Component
@ConditionalOnProperty(prefix = "files.scanner.clamav", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PendingFileScanner implements FileScanner {
    @Override
    public ScanResult scan(byte[] content) {
        return ScanResult.PENDING;
    }
}
