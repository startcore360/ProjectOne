package com.edu360.api.institution.dto;

import java.util.UUID;

public record SemesterResponse(
        UUID semesterId,
        UUID institutionId,
        UUID programYearId,
        Short semesterNumber,
        String label
) {}
