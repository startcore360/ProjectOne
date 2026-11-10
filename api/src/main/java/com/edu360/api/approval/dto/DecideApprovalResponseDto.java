package com.edu360.api.approval.dto;

import java.util.List;

public record DecideApprovalResponseDto(
        ApprovalSummaryDto approval,
        String subjectNewStatus,
        List<String> warnings
) {}
