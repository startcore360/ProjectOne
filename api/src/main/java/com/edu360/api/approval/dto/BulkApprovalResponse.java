package com.edu360.api.approval.dto;

import java.util.List;
import java.util.UUID;

/**
 * D5 BulkApproveResponse: approvalBatchId and per-item results (outcome APPROVED, ALREADY_ACTIONED,
 * NOT_CURRENT_APPROVER, NOT_BULK_ELIGIBLE). The counters and error texts are kept for existing callers.
 */
public record BulkApprovalResponse(
        UUID batchId,
        int totalProcessed,
        int succeeded,
        int failed,
        List<String> errors,
        List<ItemResult> results
) {
    public record ItemResult(UUID approvalRequestId, String outcome) {
    }
}
