package com.edu360.api.approval.dto;

import java.util.List;
import java.util.UUID;

public record ApprovalSummaryDto(
        UUID approvalRequestId,
        String processCode,
        String status,
        short currentStepNo,
        SubjectRef subject,
        List<ApprovalStepDto> steps
) {
    public record SubjectRef(String type, UUID id) {}
}
