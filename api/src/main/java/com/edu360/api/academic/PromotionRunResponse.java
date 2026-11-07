package com.edu360.api.academic;

import java.time.Instant;
import java.util.UUID;

public record PromotionRunResponse(
        UUID promotionRunId,
        UUID institutionId,
        UUID fromSessionId,
        UUID toSessionId,
        String status,
        UUID approvalRequestId,
        Instant executedAt,
        Instant createdAt
) {
    public static PromotionRunResponse from(PromotionRun p) {
        return new PromotionRunResponse(
                p.getId().getPromotionRunId(),
                p.getInstitutionId(),
                p.getFromSessionId(),
                p.getToSessionId(),
                p.getStatus(),
                p.getApprovalRequestId(),
                p.getExecutedAt(),
                p.getCreatedAt()
        );
    }
}
