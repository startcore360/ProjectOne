package com.edu360.api.institution.dto;

import java.util.UUID;

/** A safe display projection for an eligible governance approver. */
public record LeaveApproverCandidateResponse(
        UUID userAccountId,
        String displayName,
        String email
) {}
