package com.edu360.api.approval.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record BulkApprovalRequest(
        @NotEmpty List<UUID> approvalRequestIds,
        @NotNull String decision,
        String comment,
        String processCategory
) {
    public BulkApprovalRequest(List<UUID> approvalRequestIds, String decision, String comment) {
        this(approvalRequestIds, decision, comment, null);
    }
}
