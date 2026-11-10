package com.edu360.api.institution.dto;

import java.util.UUID;

public record ProgramYearResponse(
        UUID programYearId,
        UUID institutionId,
        UUID programId,
        Short yearNumber,
        String label
) {}
