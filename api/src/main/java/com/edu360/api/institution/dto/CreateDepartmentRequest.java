package com.edu360.api.institution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating an academic department under STRUCTURE_MANAGE.
 * Validates departmentCode per ck_department_department_code: ^[A-Z0-9_-]{2,20}$
 * Validates departmentName per ck_department_department_name: length 2 to 150.
 */
public record CreateDepartmentRequest(
        @NotBlank(message = "Department code is required")
        @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "Department code must be 2-20 uppercase alphanumeric characters, dashes, or underscores")
        String departmentCode,

        @NotBlank(message = "Department name is required")
        @Size(min = 2, max = 150, message = "Department name must be between 2 and 150 characters")
        String departmentName
) {}
