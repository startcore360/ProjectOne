package com.edu360.api.selfservice;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.edu360.api.identity.UserAccountId;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted active role selection per user session [ROLE_SWITCH, Rule HI-15, PO-3].
 */
@Entity
@Table(name = "user_active_role", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActiveRole implements Serializable {

    @EmbeddedId
    private UserAccountId id;

    @Column(name = "user_role_assignment_id", nullable = false)
    private UUID userRoleAssignmentId;

    @Column(name = "switched_at", nullable = false)
    private Instant switchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
