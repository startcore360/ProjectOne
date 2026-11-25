package com.edu360.api.academic;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentRequest(
        @NotNull UUID institutionId,
        @NotNull UUID sectionId,
        @NotNull UUID courseOfferingId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal maxMarks,
        @NotNull Instant dueAt,
        boolean allowLateSubmission,
        @DecimalMin(value = "0.0") BigDecimal latePenaltyPercentPerDay,
        String rubricJson) {
}
