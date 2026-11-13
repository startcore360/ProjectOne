package com.edu360.api.selfservice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UserNotificationId> {
    List<UserNotification> findByIdTenantIdAndRecipientUserIdOrderByCreatedAtDesc(UUID tenantId, UUID recipientUserId);
    Optional<UserNotification> findFirstByIdTenantIdAndDedupeKey(UUID tenantId, String dedupeKey);
    Optional<UserNotification> findByIdTenantIdAndIdNotificationIdAndRecipientUserId(
            UUID tenantId, UUID notificationId, UUID recipientUserId);
    long countByIdTenantIdAndRecipientUserIdAndReadAtIsNull(UUID tenantId, UUID recipientUserId);

    @Query("""
            select n from UserNotification n
             where n.id.tenantId = :tenantId
               and n.recipientUserId = :recipientUserId
               and (n.deliverAfter is null or n.deliverAfter <= :now)
             order by n.createdAt desc
            """)
    List<UserNotification> findDeliverable(@Param("tenantId") UUID tenantId,
            @Param("recipientUserId") UUID recipientUserId, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select count(n) from UserNotification n
             where n.id.tenantId = :tenantId
               and n.recipientUserId = :recipientUserId
               and n.readAt is null
               and (n.deliverAfter is null or n.deliverAfter <= :now)
            """)
    long countUnreadDeliverable(@Param("tenantId") UUID tenantId,
            @Param("recipientUserId") UUID recipientUserId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserNotification n
               set n.readAt = :now, n.updatedBy = :userId
             where n.id.tenantId = :tenantId
               and n.recipientUserId = :userId
               and n.readAt is null
               and (n.deliverAfter is null or n.deliverAfter <= :now)
            """)
    int markAllDeliveredRead(@Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId, @Param("now") Instant now);
}
