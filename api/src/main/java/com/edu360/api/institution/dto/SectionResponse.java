package com.edu360.api.institution.dto;

import java.util.UUID;

public record SectionResponse(
        UUID sectionId,
        UUID institutionId,
        UUID academicSessionId,
        UUID semesterId,
        UUID classId,
        String sectionName,
        String status,
        Integer capacity,
        UUID classTeacherUserId,
        int rowVersion
) {
    public SectionResponse(UUID sectionId, UUID institutionId, UUID academicSessionId, UUID semesterId,
            UUID classId, String sectionName, String status) {
        this(sectionId, institutionId, academicSessionId, semesterId, classId, sectionName, status, null, null, 1);
    }
    public SectionResponse(UUID sectionId, UUID institutionId, UUID academicSessionId, UUID semesterId,
            UUID classId, String sectionName, String status, Integer capacity, UUID classTeacherUserId) {
        this(sectionId, institutionId, academicSessionId, semesterId, classId, sectionName, status,
                capacity, classTeacherUserId, 1);
    }
}
