package com.edu360.api.institution;

import java.util.UUID;

public record TeacherLookupItem(
        UUID staffProfileId,
        UUID staffAssignmentId,
        String staffCode,
        String fullName,
        String designation,
        UUID departmentId,
        String departmentName
) {}
