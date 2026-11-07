package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AcademicSessionResponse(
        UUID academicSessionId,
        UUID tenantId,
        UUID institutionId,
        String sessionLabel,
        LocalDate startDate,
        LocalDate endDate,
        Integer academicYear,
        String status,
        Integer rowVersion) {
}
