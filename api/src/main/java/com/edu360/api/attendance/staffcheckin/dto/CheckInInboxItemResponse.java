package com.edu360.api.attendance.staffcheckin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckInInboxItemResponse(
        UUID staffAttendanceCheckinId,
        UUID approvalRequestId,
        UUID staffUserId,
        UUID institutionId,
        Instant capturedAt,
        BigDecimal distanceFromInstitutionM,
        BigDecimal gpsAccuracyMeters,
        BigDecimal livenessConfidence,
        String status
) {
}
