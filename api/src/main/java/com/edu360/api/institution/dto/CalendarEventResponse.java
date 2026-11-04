package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Response shape for a local calendar event (CALENDAR_LOCAL_SET read/write). */
public record CalendarEventResponse(
        UUID academicCalendarEventId,
        UUID institutionId,
        UUID academicSessionId,
        String sourceLevel,
        String eventType,
        String title,
        LocalDate startDate,
        LocalDate endDate) {
}
