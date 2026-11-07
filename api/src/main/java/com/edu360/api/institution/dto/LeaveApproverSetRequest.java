package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LeaveApproverSetRequest(
        @NotBlank(message = "approverKind is required")
        @Pattern(regexp = "^(MANAGEMENT|GOVERNING_BODY_APPROVER|REGISTRAR|VICE_CHANCELLOR)$",
                message = "approverKind must be MANAGEMENT, GOVERNING_BODY_APPROVER, REGISTRAR, or VICE_CHANCELLOR")
        String approverKind,

        @NotNull(message = "approverUserId is required")
        UUID approverUserId
) {}
