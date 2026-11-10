package com.edu360.api.institution.dto;

import java.util.UUID;

public record SchoolClassResponse(
        UUID classId,
        UUID institutionId,
        String className,
        Short displayOrder,
        boolean studentLoginEnabled,
        String status
) {}
