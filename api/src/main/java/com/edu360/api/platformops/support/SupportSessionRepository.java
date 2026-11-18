package com.edu360.api.platformops.support;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportSessionRepository extends JpaRepository<SupportSession, UUID> {
    Optional<SupportSession> findBySupportSessionIdAndEndedAtIsNull(UUID supportSessionId);
    Optional<SupportSession> findFirstByPlatformUserIdAndEndedAtIsNullOrderByIssuedAtDesc(UUID platformUserId);
    java.util.List<SupportSession> findByGrantIdAndEndedAtIsNull(UUID grantId);
    long countByEndedAtIsNullAndExpiresAtAfter(java.time.Instant now);
}
