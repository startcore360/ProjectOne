package com.edu360.api.audit;

import java.time.Instant;

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

// Scoped to authentication/access events only (login, logout, lockout, account creation).
// Full action-level audit coverage (every create/edit/delete/approval/export) is a much
// larger, separately tracked V4.0 gap — see docs/V4.0_SPEC_SUMMARY.md §5.
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
}
