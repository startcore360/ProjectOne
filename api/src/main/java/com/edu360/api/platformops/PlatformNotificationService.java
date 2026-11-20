package com.edu360.api.platformops;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Platform notification write side only. Listing, bell state and mark-read endpoints are owned by
 * the notification UI/API workstream and are intentionally not introduced here.
 */
@Service
@RequiredArgsConstructor
public class PlatformNotificationService {

    private final PlatformNotificationRepository repository;

    @Transactional(transactionManager = "platformTransactionManager")
    public UUID notify(
            UUID recipientPlatformUserId,
            String triggerCode,
            String title,
            String body,
            String entityType,
            UUID entityId,
            String priorityClass,
            UUID actorPlatformUserId) {
        if (recipientPlatformUserId == null || actorPlatformUserId == null) {
            throw new IllegalArgumentException("Platform notification recipient and actor are required.");
        }
        if (triggerCode == null || triggerCode.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("Platform notification trigger and title are required.");
        }

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        repository.save(PlatformNotification.builder()
                .platformNotificationId(id)
                .recipientPlatformUserId(recipientPlatformUserId)
                .triggerCode(triggerCode)
                .title(title)
                .body(body)
                .entityType(entityType)
                .entityId(entityId)
                .priorityClass(priorityClass == null ? "NORMAL" : priorityClass)
                .readAt(null)
                .createdAt(now)
                .createdBy(actorPlatformUserId)
                .updatedAt(now)
                .updatedBy(actorPlatformUserId)
                .rowVersion(1)
                .build());
        return id;
    }
}
