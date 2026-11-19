package com.edu360.api.platformops;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAuditOutboxEventRepository extends JpaRepository<PlatformAuditOutboxEvent, UUID> {
    Page<PlatformAuditOutboxEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            String outcome, Instant fromAt, Instant toAt);

    @Query(value = """
        SELECT e FROM PlatformAuditOutboxEvent e
         WHERE (:tenantId IS NULL OR e.tenantId = :tenantId)
           AND (:actorUserId IS NULL OR e.actorUserId = :actorUserId)
           AND (:actionCode IS NULL OR e.actionCode = :actionCode)
           AND (:outcome IS NULL OR e.outcome = :outcome)
           AND (:fromAt IS NULL OR e.occurredAt >= :fromAt)
           AND (:toAt IS NULL OR e.occurredAt <= :toAt)
           AND (:excludeEntityId IS NULL OR e.entityId <> :excludeEntityId)
         ORDER BY e.occurredAt DESC
        """,
        countQuery = """
        SELECT count(e) FROM PlatformAuditOutboxEvent e
         WHERE (:tenantId IS NULL OR e.tenantId = :tenantId)
           AND (:actorUserId IS NULL OR e.actorUserId = :actorUserId)
           AND (:actionCode IS NULL OR e.actionCode = :actionCode)
           AND (:outcome IS NULL OR e.outcome = :outcome)
           AND (:fromAt IS NULL OR e.occurredAt >= :fromAt)
           AND (:toAt IS NULL OR e.occurredAt <= :toAt)
           AND (:excludeEntityId IS NULL OR e.entityId <> :excludeEntityId)
        """)
    Page<PlatformAuditOutboxEvent> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("actionCode") String actionCode,
            @Param("outcome") String outcome,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("excludeEntityId") UUID excludeEntityId,
            Pageable pageable);

    @Query("""
        SELECT count(e) FROM PlatformAuditOutboxEvent e
         WHERE (:tenantId IS NULL OR e.tenantId = :tenantId)
           AND (:actorUserId IS NULL OR e.actorUserId = :actorUserId)
           AND (:actionCode IS NULL OR e.actionCode = :actionCode)
           AND (:outcome IS NULL OR e.outcome = :outcome)
           AND (:fromAt IS NULL OR e.occurredAt >= :fromAt)
           AND (:toAt IS NULL OR e.occurredAt <= :toAt)
           AND (:excludeEntityId IS NULL OR e.entityId <> :excludeEntityId)
        """)
    long countFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("actionCode") String actionCode,
            @Param("outcome") String outcome,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("excludeEntityId") UUID excludeEntityId);
}
