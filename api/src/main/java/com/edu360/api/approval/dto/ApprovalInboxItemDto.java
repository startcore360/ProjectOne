package com.edu360.api.approval.dto;

import java.time.Instant;
import java.util.UUID;

/** An approval step currently assigned to the authenticated caller. */
public record ApprovalInboxItemDto(
        UUID approvalRequestId,
        short stepNo,
        String approverRoleCode,
        String decision,
        String processCode,
        String subjectType,
        UUID subjectId,
        UUID institutionId,
        String requesterActiveRole,
        Instant requestedAt,
        short currentStepNo,
        String requestStatus
) {}
