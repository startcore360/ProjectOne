package com.edu360.api.academic;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record GradingSchemeResponse(
        UUID gradingSchemeVersionId,
        UUID institutionId,
        String schemeName,
        int versionNo,
        String status,
        LocalDate effectiveFrom,
        Instant createdAt
) {
    public static GradingSchemeResponse from(GradingSchemeVersion g) {
        return new GradingSchemeResponse(
                g.getId().getGradingSchemeVersionId(),
                g.getInstitutionId(),
                g.getSchemeName(),
                g.getVersionNo(),
                g.getStatus(),
                g.getEffectiveFrom(),
                g.getCreatedAt()
        );
    }
}
