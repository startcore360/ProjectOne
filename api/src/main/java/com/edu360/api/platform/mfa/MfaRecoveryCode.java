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
import lombok.Setter;

/**
 * One backup code for two-step login (MFA-01, platform.mfa_recovery_code). Keyed by the Keycloak
 * identity it unlocks (realm + user id), because it is read at sign-in, before any tenant context
 * exists. Only a BCrypt hash is kept; the plain code is shown to the user once.
 */
@Entity
@Table(schema = "platform", name = "mfa_recovery_code")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryCode {
    @Id
    @Column(name = "mfa_recovery_code_id", nullable = false)
    private UUID mfaRecoveryCodeId;

    @Column(name = "realm", nullable = false)
    private String realm;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
