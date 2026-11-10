package com.edu360.api.institution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInstitutionRequest(
        @NotBlank(message = "institutionType is required")
        @Pattern(regexp = "COLLEGE|SCHOOL", message = "institutionType must be COLLEGE or SCHOOL")
        @Schema(description = "Kind of institution", example = "COLLEGE", allowableValues = {"COLLEGE", "SCHOOL"})
        String institutionType,

        @NotBlank(message = "institutionCode is required")
        @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "institutionCode must be 2-20 uppercase alphanumeric characters, dashes or underscores")
        @Schema(description = "Institution code, unique per tenant", example = "NBC-01")
        String institutionCode,

        @NotBlank(message = "institutionName is required")
        @Size(min = 2, max = 200, message = "institutionName must be between 2 and 200 characters")
        @Schema(description = "Full name of the institution", example = "Northbridge College of Science")
        String institutionName,

        @Schema(description = "College whose university is not on EDU 360", example = "false")
        Boolean externalUniversityMode,

        @Size(max = 300, message = "addressLine must not exceed 300 characters")
        @Schema(description = "Street address", example = "124 Knowledge Park, Sector 4")
        String addressLine,

        @Size(max = 100, message = "city must not exceed 100 characters")
        @Schema(description = "City", example = "Bengaluru")
        String city,

        @Size(max = 100, message = "stateName must not exceed 100 characters")
        @Schema(description = "State", example = "Karnataka")
        String stateName,

        @Size(min = 3, max = 12, message = "postalCode must be between 3 and 12 characters")
        @Schema(description = "Postal/PIN code", example = "560001")
        String postalCode,

        @Email(message = "principalInviteEmail must be a valid email address")
        @Size(max = 254, message = "principalInviteEmail must not exceed 254 characters")
        @Schema(description = "Principal invite email address", example = "principal@northbridge.edu.in")
        String principalInviteEmail
) {
    public boolean isExternalUniversityMode() {
        return externalUniversityMode != null && externalUniversityMode;
    }
}
