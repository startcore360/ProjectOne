package com.edu360.api.institution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffDocumentVerificationRequest(
        @NotBlank String verificationStatus,
        @NotBlank @Size(min = 1, max = 2000) String remark
) {}
