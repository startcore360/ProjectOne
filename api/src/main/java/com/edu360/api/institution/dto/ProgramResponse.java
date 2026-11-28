package com.edu360.api.institution.dto;

import java.util.UUID;
import java.math.BigDecimal;

public record ProgramResponse(
        UUID programId,
        UUID institutionId,
        UUID departmentId,
        String programCode,
        String programName,
        Short durationYears,
        Short semestersPerYear,
        String status,
        String degreeLevel,
        BigDecimal totalCredits,
        Integer curriculumVersion,
        int rowVersion
) {
    public ProgramResponse(UUID programId, UUID institutionId, UUID departmentId, String programCode,
            String programName, Short durationYears, Short semestersPerYear, String status) {
        this(programId, institutionId, departmentId, programCode, programName, durationYears,
                semestersPerYear, status, null, null, 1, 1);
    }
    public ProgramResponse(UUID programId, UUID institutionId, UUID departmentId, String programCode,
            String programName, Short durationYears, Short semestersPerYear, String status,
            String degreeLevel, BigDecimal totalCredits, Integer curriculumVersion) {
        this(programId, institutionId, departmentId, programCode, programName, durationYears,
                semestersPerYear, status, degreeLevel, totalCredits, curriculumVersion, 1);
    }
}
