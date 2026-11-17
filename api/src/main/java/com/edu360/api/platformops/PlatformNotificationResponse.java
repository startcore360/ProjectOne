package com.edu360.api.platformops;

import java.time.Instant;
import java.util.UUID;

public record PlatformNotificationResponse(
        UUID notificationId,
        String triggerCode,
        String title,
        String body,
        String entityType,
        UUID entityId,
        String priorityClass,
        Instant readAt,
        Instant createdAt) {

    public static PlatformNotificationResponse from(PlatformNotification notification) {
        return new PlatformNotificationResponse(
                notification.getPlatformNotificationId(),
                notification.getTriggerCode(),
                notification.getTitle(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getPriorityClass(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
