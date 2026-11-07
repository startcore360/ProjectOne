package com.edu360.api.rbac.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.rbac.ScopeType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
        @Schema(description = "Existing role definition ID, or null if roleCode is provided")
        UUID roleDefinitionId,

        @Schema(description = "Standard or existing role code (e.g., 'TEACHER', 'HOD', 'COLLEGE_ADMIN')")
        String roleCode,

        @NotNull(message = "scopeType is required (TENANT, INSTITUTION, DEPARTMENT)")
        @Schema(description = "Scope level for this assignment", allowableValues = {"TENANT", "INSTITUTION", "DEPARTMENT"})
        ScopeType scopeType,

        @Schema(description = "Institution ID. Required when scopeType is INSTITUTION or DEPARTMENT")
        UUID institutionId,

        @Schema(description = "Department ID. Required when scopeType is DEPARTMENT")
        UUID departmentId,

        @Schema(description = "Effective start time. Defaults to now if omitted")
        Instant effectiveFrom,

        @Schema(description = "Effective end time, or null for indefinite assignment")
        Instant effectiveTo
) {}
