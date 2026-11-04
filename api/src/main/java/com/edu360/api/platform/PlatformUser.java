package com.edu360.api.platform;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: platform.platform_user - a platform staff account (Product Owner and the five platform
 * roles). Simple single-column primary key, unlike every app.* entity: this table has no
 * tenant_id at all, because it IS outside the tenant model entirely.
 *
 * WHY this is a completely separate entity/table from UserAccount rather than another
 * UserType value: mixing them would make "no tenant" an accidental special case of the tenant
 * user model instead of a genuinely different identity space - exactly the mistake the Sprint 1
 * brief's "never let Platform Admin become an implicit tenant RLS bypass" rule is guarding
 * against. Keeping two distinct Java types makes it a compile error to accidentally pass a
 * PlatformUser where tenant-scoped code expects a UserAccount, or the reverse.
 */
@Entity
@Table(schema = "platform", name = "platform_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformUser {

    @Id
    @Column(name = "platform_user_id")
    private UUID platformUserId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformUserStatus status;

    /** Always true per the DB CHECK constraint (ck_platform_user_mfa_required) - every platform
     *  account requires two-step login, no exceptions (NF-09). */
    @Column(name = "mfa_required", nullable = false)
    private boolean mfaRequired;

    /** Keycloak's "sub" claim in the platform realm (edu360-platform) - see
     *  app.keycloak.platform-realm. Unique per DB constraint. */
    @Column(name = "identity_subject", nullable = false)
    private String identitySubject;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

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
