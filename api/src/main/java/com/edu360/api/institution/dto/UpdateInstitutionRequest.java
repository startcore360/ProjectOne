package com.edu360.api.institution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable profile fields. Institution type, code and lifecycle state have dedicated rules. */
public record UpdateInstitutionRequest(
        @NotBlank(message = "institutionName is required")
        @Size(min = 2, max = 200, message = "institutionName must be between 2 and 200 characters")
        String institutionName,

        @Size(max = 300, message = "addressLine must not exceed 300 characters")
        String addressLine,

        @Size(max = 100, message = "city must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "stateName must not exceed 100 characters")
        String stateName,

        @Size(min = 3, max = 12, message = "postalCode must be between 3 and 12 characters")
        String postalCode,

        @Email(message = "principalInviteEmail must be a valid email address")
        @Size(max = 254, message = "principalInviteEmail must not exceed 254 characters")
        String principalInviteEmail,

        Boolean externalUniversityMode
) {}
