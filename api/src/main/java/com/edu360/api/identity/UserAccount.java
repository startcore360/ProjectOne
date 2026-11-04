package com.edu360.api.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: one row of app.user_account - a login identity, nothing else. No password, no name
 * beyond a display name, no role: that all lives elsewhere (Keycloak for credentials,
 * app.user_role_assignment for roles - see the Foundation Item 7 boundary note in the Sprint 1
 * identity migration brief).
 *
 * WHY tenant_id is a real field here (not hidden behind RLS alone): Hibernate needs it to build
 * the composite key and the WHERE clause of every query - RLS is what stops a MISTAKE or an
 * attack from reading another tenant's rows even if the app's own filtering is somehow wrong,
 * not a replacement for the app filtering correctly in the first place.
 *
 * row_version is mapped read-only for now (DB default 1, DB trigger owns every future increment
 * - see sql/02_rules.sql app.fn_touch()). This entity only needs INSERT/SELECT today; proper
 * optimistic-locking (@Version, or an If-Match header per CLAUDE.md) is deferred to whichever
 * later story first needs to UPDATE a user_account row, rather than guessing a scheme nothing
 * exercises yet.
 */
@Entity
@Table(schema = "app", name = "user_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccount {

    @EmbeddedId
    private UserAccountId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Lower-case; staff and governance log in with it (schema comment). Null for students,
     *  who log in with their student code instead (see UsernameKind). */
    @Column
    private String email;

    /** E.164 format; parents are matched by this number (WF-21). */
    @Column
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserAccountStatus status;

    /** Printed first-login password expiry, 7 days, students only (R2-35). Null for every
     *  other user_type - the DB CHECK constraint ck_user_account_r1 enforces that pairing. */
    @Column(name = "credential_expires_at")
    private Instant credentialExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivated_by")
    private UUID deactivatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
