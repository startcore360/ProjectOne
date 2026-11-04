package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Response shape for a weekly-off rule (WEEKLY_OFF_SET read/write). */
public record WeeklyOffRuleResponse(
        UUID weeklyOffRuleId,
        UUID institutionId,
        Short dayOfWeek,
        String occurrence,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
