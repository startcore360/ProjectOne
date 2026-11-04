package com.edu360.api.institution.dto;

import java.util.UUID;

/** Response for a successful HOD_ASSIGN. */
public record HodAssignResponse(UUID departmentId, UUID hodStaffAssignmentId) {
}
