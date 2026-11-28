package com.edu360.api.platform.mfa;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaRecoveryAttemptRepository extends JpaRepository<MfaRecoveryAttempt, UUID> {

    long countByRealmAndKeycloakUserIdAndSucceededFalseAndAttemptedAtAfter(
            String realm, String keycloakUserId, Instant since);
}
