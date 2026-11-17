package com.edu360.api.platformops;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformNotificationReadService {

    private final PlatformNotificationRepository repository;

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<PlatformNotificationResponse> list(UUID recipientPlatformUserId) {
        return repository.findTop50ByRecipientPlatformUserIdOrderByCreatedAtDesc(recipientPlatformUserId)
                .stream()
                .map(PlatformNotificationResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void markRead(UUID recipientPlatformUserId, UUID notificationId) {
        repository.markReadForRecipient(notificationId, recipientPlatformUserId, Instant.now());
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void markAllRead(UUID recipientPlatformUserId) {
        repository.markAllReadForRecipient(recipientPlatformUserId, Instant.now());
    }
}
