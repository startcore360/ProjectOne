package com.edu360.api.live;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LiveEventPublisher {
    void publishToUser(UUID tenantId, UUID userId, String type, Map<String, Object> payload);
    void publishEntityChanged(UUID tenantId, List<UUID> authorizedUserIds, String entityType,
            UUID entityId, UUID institutionId, String changeType);
}
