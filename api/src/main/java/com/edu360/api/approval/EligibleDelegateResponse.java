package com.edu360.api.approval;

import java.util.UUID;

/** Display-safe account option for an approver's eligible delegation targets. */
public record EligibleDelegateResponse(UUID userAccountId, String displayName, String email) {}
