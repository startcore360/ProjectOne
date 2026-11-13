package com.edu360.api.files;

public interface FileScanner {
    ScanResult scan(byte[] content);

    enum ScanResult {
        PENDING,
        CLEAN,
        INFECTED
    }
}
