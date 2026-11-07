package com.edu360.api.institution.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SubjectResponse(
        UUID subjectId,
        UUID institutionId,
        String subjectCode,
        String subjectName,
        BigDecimal credits,
        boolean isElective,
        String status
) {}
