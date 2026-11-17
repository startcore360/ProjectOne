package com.edu360.api.platformops;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, UUID> {
    boolean existsByRecipientPlatformUserIdAndTriggerCodeAndEntityId(UUID recipientPlatformUserId, String triggerCode, UUID entityId);

    List<PlatformNotification> findTop50ByRecipientPlatformUserIdOrderByCreatedAtDesc(UUID recipientPlatformUserId);

    @Modifying
    @Query("""
            update PlatformNotification n
               set n.readAt = :readAt,
                   n.updatedAt = :readAt,
                   n.updatedBy = :recipientId,
                   n.rowVersion = n.rowVersion + 1
             where n.platformNotificationId = :notificationId
               and n.recipientPlatformUserId = :recipientId
               and n.readAt is null
            """)
    int markReadForRecipient(@Param("notificationId") UUID notificationId,
            @Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("""
            update PlatformNotification n
               set n.readAt = :readAt,
                   n.updatedAt = :readAt,
                   n.updatedBy = :recipientId,
                   n.rowVersion = n.rowVersion + 1
             where n.recipientPlatformUserId = :recipientId
               and n.readAt is null
            """)
    int markAllReadForRecipient(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
