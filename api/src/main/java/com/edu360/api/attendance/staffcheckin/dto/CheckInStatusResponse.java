package com.edu360.api.attendance.staffcheckin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckInStatusResponse(
        UUID staffAttendanceCheckinId,
        UUID institutionId,
        Instant capturedAt,
        BigDecimal distanceFromInstitutionM,
        BigDecimal gpsAccuracyMeters,
        BigDecimal livenessConfidence,
        String status,
        UUID approvalRequestId,
        Instant reviewedAt,
        String reviewNote
) {
}
