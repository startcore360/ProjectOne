package com.edu360.api.institution.dto;

import java.util.UUID;

public record SectionResponse(
        UUID sectionId,
        UUID institutionId,
        UUID academicSessionId,
        UUID semesterId,
        UUID classId,
        String sectionName,
        String status
) {}
