package com.edu360.api.platformops;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import jakarta.persistence.criteria.Predicate;

public interface PlatformAuditOutboxEventRepository
        extends JpaRepository<PlatformAuditOutboxEvent, UUID>, JpaSpecificationExecutor<PlatformAuditOutboxEvent> {

    Page<PlatformAuditOutboxEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            String outcome, Instant fromAt, Instant toAt);

    Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

    /**
     * E6-S2 audit log search, newest first. Only the filters that were given become conditions.
     *
     * WHY not "(:param IS NULL OR column = :param)" in JPQL: PostgreSQL cannot infer the type of a
     * parameter that is only compared with NULL, so an empty date or id filter failed with
     * "could not determine data type of parameter" and the audit log returned 503 on every unfiltered
     * request. H2 (unit tests) accepts that form, which is why the tests did not catch it.
     */
    default Page<PlatformAuditOutboxEvent> findFiltered(UUID tenantId, UUID actorUserId, String actionCode,
            String outcome, Instant fromAt, Instant toAt, UUID excludeEntityId, Pageable pageable) {
        Pageable newestFirst = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST)
                : Pageable.unpaged(NEWEST_FIRST);
        return findAll(filter(tenantId, actorUserId, actionCode, outcome, fromAt, toAt, excludeEntityId), newestFirst);
    }

    default long countFiltered(UUID tenantId, UUID actorUserId, String actionCode,
            String outcome, Instant fromAt, Instant toAt, UUID excludeEntityId) {
        return count(filter(tenantId, actorUserId, actionCode, outcome, fromAt, toAt, excludeEntityId));
    }

    static Specification<PlatformAuditOutboxEvent> filter(UUID tenantId, UUID actorUserId, String actionCode,
            String outcome, Instant fromAt, Instant toAt, UUID excludeEntityId) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (tenantId != null) conditions.add(cb.equal(root.get("tenantId"), tenantId));
            if (actorUserId != null) conditions.add(cb.equal(root.get("actorUserId"), actorUserId));
            if (actionCode != null) conditions.add(cb.equal(root.get("actionCode"), actionCode));
            if (outcome != null) conditions.add(cb.equal(root.get("outcome"), outcome));
            if (fromAt != null) conditions.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromAt));
            if (toAt != null) conditions.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toAt));
            // Same meaning as the old query: only applied when an id is given.
            if (excludeEntityId != null) conditions.add(cb.notEqual(root.get("entityId"), excludeEntityId));
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
