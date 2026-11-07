package com.edu360.api.approval.dto;

import java.util.List;
import java.util.UUID;

public record BulkApprovalResponse(
        UUID batchId,
        int totalProcessed,
        int succeeded,
        int failed,
        List<String> errors
) {
}
