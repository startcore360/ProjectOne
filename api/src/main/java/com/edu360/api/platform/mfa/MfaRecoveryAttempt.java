package com.edu360.api.platform.mfa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One backup-code sign-in attempt (MFA-01). Used to limit wrong guesses on every API node. */
@Entity
@Table(schema = "platform", name = "mfa_recovery_attempt")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryAttempt {
    @Id
    @Column(name = "mfa_recovery_attempt_id", nullable = false)
    private UUID mfaRecoveryAttemptId;

    @Column(name = "realm", nullable = false)
    private String realm;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "succeeded", nullable = false)
    private boolean succeeded;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;
}
