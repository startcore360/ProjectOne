package com.edu360.api.platformops.landing.dto;

import java.util.UUID;

public record LandingMediaUploadResponse(
        UUID mediaId,
        String mediaKind,
        String fileName,
        String contentType,
        long byteSize,
        String scanStatus,
        String publicUrl
) { }
