package com.edu360.api.institution.dto;

import java.util.List;
import java.util.UUID;

public record GoLiveCheckResponse(
        boolean ready,
        List<GoLiveBlockerItem> blockers
) {
    public record GoLiveBlockerItem(
            String blocker,
            UUID institutionId
    ) {}
}
