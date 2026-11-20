package com.edu360.api.institution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffDocumentVerificationRequest(
        @NotBlank String verificationStatus,
        @Size(max = 1000) String remark
) {}
