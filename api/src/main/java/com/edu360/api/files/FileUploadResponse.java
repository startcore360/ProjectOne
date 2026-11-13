package com.edu360.api.files;

import java.time.Instant;
import java.util.UUID;

public record FileUploadResponse(
        UUID fileId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        String scanStatus,
        String status,
        Instant createdAt
) {
    public static FileUploadResponse from(FileObject file) {
        return new FileUploadResponse(file.getId().getFileId(), file.getOriginalFilename(),
                file.getContentType(), file.getSizeBytes(), file.getSha256(),
                file.getScanStatus(), file.getStatus(), file.getCreatedAt());
    }
}
