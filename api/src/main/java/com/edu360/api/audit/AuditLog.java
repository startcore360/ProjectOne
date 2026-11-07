package com.edu360.api.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OD-09 Append-Only Audit Log Entity.
 * Tracks operational and security actions with active role, scope, institution, and correlation ID.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventType eventType;

    // May be null when the attempted identifier does not match any account.
    @Column
    private Long userId;

    @Column(nullable = false)
    private String email;

    @Column
    private String ipAddress;

    @Column(nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column
    private String details;

    @Column(name = "actor_role_code")
    private String actorRoleCode;

    @Column(name = "scope_type")
    private String scopeType;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "correlation_id")
    private String correlationId;
}
