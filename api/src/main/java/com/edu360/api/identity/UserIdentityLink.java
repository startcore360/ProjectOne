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
 * WHAT: the link between a user_account and its Keycloak identity (one row per login method a
 * user has) - app.user_identity_link in the schema.
 *
 * WHY this table exists separately from user_account instead of a keycloak_id column on
 * user_account directly (like the old single-tenant User entity used to do): a user can have
 * more than one login method over time (e.g. a revoked old email plus a new one), and the
 * schema's own comment says this table exists specifically to make that link "explicit and
 * unique" (fixes a finding from the V4.0 review). identityRealm + identitySubject together
 * identify exactly one Keycloak account; this is the only place that pairing is looked up.
 *
 * HOW the realm→tenant bootstrap resolver uses this (see TenantResolver, step 2): once the
 * resolver already knows the tenant (from platform.tenant, BEFORE this table is ever queried -
 * RLS would block it otherwise) and the JWT's issuer has been cryptographically verified, it
 * looks up this table by (tenantId, identityRealm, identitySubject) to find which user_account
 * the verified token belongs to. The realm/subject values used in that lookup must always come
 * from the verified JWT, never from a header or request body the caller could forge.
 */
@Entity
@Table(schema = "app", name = "user_identity_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentityLink {

    @EmbeddedId
    private UserIdentityLinkId id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    /** The Keycloak realm name (realm-per-tenant, OD-10) - e.g. "abc-university". */
    @Column(name = "identity_realm", nullable = false)
    private String identityRealm;

    /** Keycloak's own "sub" claim - stable even if the user's email changes. */
    @Column(name = "identity_subject", nullable = false)
    private String identitySubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "username_kind", nullable = false)
    private UsernameKind usernameKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdentityLinkStatus status;

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
