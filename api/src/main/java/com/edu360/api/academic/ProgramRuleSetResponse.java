package com.edu360.api.academic;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record ProgramRuleSetResponse(
        UUID programRuleSetId,
        UUID institutionId,
        UUID programId,
        int versionNo,
        String rules,
        LocalDate effectiveFrom,
        String status,
        Instant createdAt
) {
    public static ProgramRuleSetResponse from(ProgramRuleSet s) {
        return new ProgramRuleSetResponse(
                s.getId().getProgramRuleSetId(),
                s.getInstitutionId(),
                s.getProgramId(),
                s.getVersionNo(),
                s.getRules(),
                s.getEffectiveFrom(),
                s.getStatus(),
                s.getCreatedAt()
        );
    }
}
