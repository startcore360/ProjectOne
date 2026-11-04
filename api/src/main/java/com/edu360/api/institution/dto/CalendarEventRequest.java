package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/tenants/{tenantId}/calendar-events (CALENDAR_LOCAL_SET).
 * institutionId is required here (not in the path) the same way CreateUserCheckRequest takes
 * institutionId/departmentId in the body - UserCreationController's own javadoc explains why:
 * the authenticated principal's tenantId is trusted, everything else about WHERE the action
 * applies is caller-supplied scope the evaluator checks for real.
 *
 * eventType is restricted to LOCAL_HOLIDAY/LOCAL_EVENT by CalendarEventService before it ever
 * reaches the database - SEMESTER_DATES/UNIVERSITY_HOLIDAY belong to the separate, not-yet-built
 * CALENDAR_MASTER_SET story.
 */
public record CalendarEventRequest(
        @NotNull UUID institutionId,
        @NotNull UUID academicSessionId,
        @NotNull String eventType,
        @NotNull String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
