package com.edu360.api.platformops;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditOutboxEventRepository extends JpaRepository<PlatformAuditOutboxEvent, UUID> {

    Page<PlatformAuditOutboxEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
