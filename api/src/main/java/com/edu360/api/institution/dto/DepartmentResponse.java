package com.edu360.api.institution.dto;

import java.util.UUID;

public record DepartmentResponse(
        UUID departmentId,
        UUID institutionId,
        String departmentCode,
        String departmentName,
        UUID hodStaffAssignmentId,
        String status
) {}
