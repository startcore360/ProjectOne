package com.edu360.api.attendance.staffcheckin.dto;

import java.math.BigDecimal;

public record LocationConfigRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        Integer radiusMeters,
        Integer gpsAccuracyThresholdMeters,
        String timezone,
        Integer retentionDays,
        String approverForPrincipalRole,
        Integer expectedRowVersion
) {
}
