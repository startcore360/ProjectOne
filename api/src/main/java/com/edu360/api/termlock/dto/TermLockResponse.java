package com.edu360.api.termlock.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.termlock.TermLock;

public record TermLockResponse(
        UUID termLockId,
        UUID tenantId,
        UUID institutionId,
        UUID academicSessionId,
        UUID semesterId,
        String lockState,
        Instant unlockUntil,
        Instant createdAt,
        Instant updatedAt,
        Integer rowVersion
) {
    public static TermLockResponse from(TermLock tl) {
        return new TermLockResponse(
                tl.getTermLockId(),
                tl.getTenantId(),
                tl.getInstitutionId(),
                tl.getAcademicSessionId(),
                tl.getSemesterId(),
                tl.getLockState(),
                tl.getUnlockUntil(),
                tl.getCreatedAt(),
                tl.getUpdatedAt(),
                tl.getRowVersion()
        );
    }
}
