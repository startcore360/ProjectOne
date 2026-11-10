package com.edu360.api.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TopUserCreateRequest(
        @NotBlank(message = "roleCode is required")
        @Pattern(regexp = "^(MANAGEMENT|DATA_PROTECTION_CONTACT|VICE_CHANCELLOR|GOVERNING_BODY_APPROVER)$",
                message = "roleCode must be one of: MANAGEMENT, DATA_PROTECTION_CONTACT, VICE_CHANCELLOR, GOVERNING_BODY_APPROVER")
        @Schema(description = "Top governance role code", example = "VICE_CHANCELLOR")
        String roleCode,

        @NotBlank(message = "displayName is required")
        @Size(min = 1, max = 150, message = "displayName must be between 1 and 150 characters")
        @Schema(description = "Full name of the executive / governance official", example = "Prof. Suresh Chandra")
        String displayName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email format")
        @Size(min = 3, max = 254, message = "email must be between 3 and 254 characters")
        @Schema(description = "Official email address", example = "vc@northbridge.edu.in")
        String email,

        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "mobile must be formatted as E.164 (e.g. +919876543210)")
        @Schema(description = "Phone number in E.164 format", example = "+919876543210")
        String mobile
) {}
