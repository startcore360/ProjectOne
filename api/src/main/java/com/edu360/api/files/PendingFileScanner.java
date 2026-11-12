package com.edu360.api.files;

import org.springframework.stereotype.Component;

/** DEC-7 bounded exception: no malware scanner is configured, so new files remain PENDING. */
@Component
public class PendingFileScanner implements FileScanner {
    @Override
    public ScanResult scan(byte[] content) {
        return ScanResult.PENDING;
    }
}
