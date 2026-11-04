package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Request body for POST /api/tenants/{tenantId}/weekly-off-rules (WEEKLY_OFF_SET). */
public record WeeklyOffRuleRequest(
        @NotNull UUID institutionId,
        @NotNull Short dayOfWeek,
        String occurrence,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
