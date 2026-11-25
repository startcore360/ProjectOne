package com.edu360.api.platformops.landing.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicEnquiryResponse(
        UUID enquiryId,
        String enquiryType,
        String status,
        String message,
        Instant submittedAt
) { }
