package com.edu360.api.rbac.dto;

import java.util.UUID;

import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.ScopeType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotNull(message = "userType is required")
        @Schema(description = "Kind of user account", example = "STAFF", allowableValues = {"STAFF", "STUDENT", "GUARDIAN", "GOVERNANCE"})
        UserType userType,

        @NotBlank(message = "displayName is required")
        @Size(min = 1, max = 150, message = "displayName must be between 1 and 150 characters")
        @Schema(description = "Display name of the person", example = "Anita Sharma")
        String displayName,

        @Email(message = "email must be a valid email format")
        @Size(min = 3, max = 254, message = "email must be between 3 and 254 characters")
        @Schema(description = "Official email address (lower-case)", example = "anita.sharma@northbridge.edu.in")
        String email,

        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "mobile must be formatted as E.164 (e.g. +919876543210)")
        @Schema(description = "Phone number in E.164 format", example = "+919876543210")
        String mobile,

        @Pattern(regexp = "^[0-9]{4}$", message = "nationalIdLast4 must be exactly 4 digits per UIDAI guidelines [R2-34]")
        @Schema(description = "Last 4 digits of Aadhaar/National ID for verification (DPDP Act 2023 [R2-34])", example = "4589")
        String nationalIdLast4,

        @Schema(description = "Initial standard role code to assign (e.g. 'TEACHER', 'STUDENT')", example = "TEACHER")
        String initialRoleCode,

        @Schema(description = "Acting scope type for creation check", example = "INSTITUTION")
        ScopeType scopeType,

        @Schema(description = "Institution ID when scope is INSTITUTION or DEPARTMENT")
        UUID institutionId,

        @Schema(description = "Department ID when scope is DEPARTMENT")
        UUID departmentId
) {
    public ScopeType effectiveScopeType() {
        return scopeType != null ? scopeType : (institutionId != null ? ScopeType.INSTITUTION : ScopeType.TENANT);
    }
}
