package com.edu360.api.selfservice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UserNotificationId> {
    List<UserNotification> findByIdTenantIdAndRecipientUserIdOrderByCreatedAtDesc(UUID tenantId, UUID recipientUserId);
    Optional<UserNotification> findFirstByIdTenantIdAndDedupeKey(UUID tenantId, String dedupeKey);
    Optional<UserNotification> findByIdTenantIdAndIdNotificationIdAndRecipientUserId(
            UUID tenantId, UUID notificationId, UUID recipientUserId);
    long countByIdTenantIdAndRecipientUserIdAndReadAtIsNull(UUID tenantId, UUID recipientUserId);
}
