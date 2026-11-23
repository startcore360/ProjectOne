package com.edu360.api.institution.dto;

import java.util.UUID;

public record DepartmentResponse(
        UUID departmentId,
        UUID institutionId,
        String departmentCode,
        String departmentName,
        UUID hodStaffAssignmentId,
        String status,
        long facultyCount,
        long studentCount,
        int rowVersion
) {
    public DepartmentResponse(UUID departmentId, UUID institutionId, String departmentCode,
            String departmentName, UUID hodStaffAssignmentId, String status) {
        this(departmentId, institutionId, departmentCode, departmentName, hodStaffAssignmentId, status, 0, 0, 1);
    }
}
