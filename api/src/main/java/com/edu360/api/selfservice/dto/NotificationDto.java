package com.edu360.api.selfservice.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID notificationId,
        String triggerCode,
        String title,
        String body,
        String priorityClass,
        Instant createdAt,
        Instant readAt
) {}
