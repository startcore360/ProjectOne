package com.edu360.api.institution.dto;

import java.util.UUID;

public record ProgramResponse(
        UUID programId,
        UUID institutionId,
        UUID departmentId,
        String programCode,
        String programName,
        Short durationYears,
        Short semestersPerYear,
        String status
) {}
