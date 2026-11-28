package com.edu360.api.attendance.staffcheckin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LocationConfigResponse(
        UUID institutionAttendanceLocationId,
        UUID institutionId,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer radiusMeters,
        Integer gpsAccuracyThresholdMeters,
        String timezone,
        Integer retentionDays,
        String approverForPrincipalRole,
        Integer rowVersion
) {
}
