package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffServiceRecordRequest(
        @NotBlank String eventType,
        @NotNull LocalDate effectiveOn,
        @NotBlank @Size(min = 1, max = 2000) String details,
        UUID staffAssignmentId,
        UUID documentFileId
) {}
