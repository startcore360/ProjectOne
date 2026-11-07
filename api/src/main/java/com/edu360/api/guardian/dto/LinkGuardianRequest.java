package com.edu360.api.guardian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LinkGuardianRequest(
        @NotBlank String fullName,
        @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Mobile must be in international E.164 format") String mobile,
        String email,
        @NotBlank String relationship,
        boolean isPrimary
) {
}
