package com.edu360.api.institution.dto;

import java.util.UUID;

public record CourseOfferingResponse(
        UUID courseOfferingId,
        UUID institutionId,
        UUID subjectId,
        UUID academicSessionId,
        UUID semesterId,
        UUID classId
) {}
