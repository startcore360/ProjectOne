package com.edu360.api.termlock;

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
@Table(schema = "app", name = "term_lock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermLock {

    @EmbeddedId
    private TermLockId id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "academic_session_id", nullable = false)
    private UUID academicSessionId;

    @Column(name = "semester_id")
    private UUID semesterId;

    @Column(name = "lock_state", nullable = false)
    @Builder.Default
    private String lockState = "LOCKED";

    @Column(name = "unlock_until")
    private Instant unlockUntil;

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

    public UUID getTermLockId() {
        return id != null ? id.getTermLockId() : null;
    }
}
