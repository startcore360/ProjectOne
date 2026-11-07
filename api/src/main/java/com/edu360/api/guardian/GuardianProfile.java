package com.edu360.api.guardian;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "app", name = "guardian_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardianProfile {

    @EmbeddedId
    private GuardianProfileId id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "mobile", nullable = false)
    private String mobile;

    @Column(name = "mobile_verified_at")
    private Instant mobileVerifiedAt;

    @Column(name = "email")
    private String email;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;

    public UUID getTenantId() {
        return id != null ? id.getTenantId() : null;
    }

    public UUID getGuardianProfileId() {
        return id != null ? id.getGuardianProfileId() : null;
    }
}
