package com.edu360.api.termlock;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermLockEventRepository extends JpaRepository<TermLockEvent, TermLockEventId> {

    List<TermLockEvent> findByIdTenantIdAndTermLockIdOrderByCreatedAtDesc(UUID tenantId, UUID termLockId);
}
