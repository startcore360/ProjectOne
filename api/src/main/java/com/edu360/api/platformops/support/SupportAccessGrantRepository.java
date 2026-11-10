package com.edu360.api.platformops.support;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportAccessGrantRepository extends JpaRepository<SupportAccessGrant, UUID> {
    List<SupportAccessGrant> findByTenantId(UUID tenantId);
    List<SupportAccessGrant> findByTenantIdOrderByGrantedAtDesc(UUID tenantId);
    Optional<SupportAccessGrant> findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(UUID ticketId, Instant now);
    List<SupportAccessGrant> findByExpiresAtAfterAndWithdrawnAtIsNull(Instant now);
}
