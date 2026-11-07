package com.edu360.api.approval.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record BulkApproveRequestDto(
        @NotEmpty List<BulkApproveItem> items,
        String comment
) {
    public record BulkApproveItem(UUID approvalRequestId, short stepNo, Integer expectedVersion) {}
}
