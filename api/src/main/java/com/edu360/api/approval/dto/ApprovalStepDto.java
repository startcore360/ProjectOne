package com.edu360.api.approval.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalStepDto(
        short stepNo,
        String approverRoleCode,
        String decision,
        UUID actedBy,
        String actedActiveRole,
        String remark,
        String skipReason,
        Instant decidedAt
) {}
