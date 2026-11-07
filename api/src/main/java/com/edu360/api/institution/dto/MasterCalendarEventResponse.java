package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MasterCalendarEventResponse(
        UUID academicCalendarEventId,
        UUID tenantId,
        UUID academicSessionId,
        UUID institutionId,
        String sourceLevel,
        String eventType,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        Integer rowVersion) {
}
