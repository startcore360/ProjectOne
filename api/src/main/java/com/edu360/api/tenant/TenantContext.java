package com.edu360.api.tenant;

import java.util.Set;
import java.util.UUID;

import com.edu360.api.rbac.ScopeType;

/**
 * WHAT: holds "which tenant is this request for" for the lifetime of one request, on one
 * worker thread - a ThreadLocal, not a Spring bean, because the value has to be readable from
 * deep inside the JDBC layer (TenantRoutingDataSource) where there is no HttpServletRequest or
 * Authentication object to pull it from directly.
 *
 * WHY it only ever gets set AFTER JWT verification: see TenantAwareJwtAuthenticationConverter.
 * Nothing in this class enforces that by itself - it is just a holder - so every caller of
 * set(...) is responsible for only calling it once a token (or, for Platform Admin, a verified
 * platform-realm token) has already been cryptographically validated.
 *
 * WHY the empty/no-tenant state matters (Platform Admin): when nothing has been set, every
 * app.* row is correctly invisible, because the RLS policies compare tenant_id against
 * NULLIF(current_setting('app.tenant_id', true), '')::uuid, which is NULL when unset - and
 * "tenant_id = NULL" is never true in SQL for any row. A Platform Admin's request simply never
 * calls set(...), so it never gains ANY app.* access by default - there is no sentinel tenant,
 * no superuser connection, and no code path that treats "no tenant" as "every tenant."
 * Platform-staff access to a specific tenant's data (support tickets, SUPPORT_ACCESS_GRANT) is
 * a deliberate, logged, time-boxed exception built later - never an accident of this class.
 *
 * INSTITUTION SCOPE (OD-03, 2026-11-04): institutionIds is the additional, RESTRICTIVE scoping
 * layer UNDER tenant isolation - sql/03_bindings.sql's p_institution policy on every app.* table
 * that has an institution_id column. The policy's own predicate is:
 *   institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL'
 *     OR institution_id = ANY(string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[])
 *
 * Per D4 §2.2, app.institution_scope = 'ALL' is reserved strictly for tenant-wide roles (University Admin,
 * Management, Vice Chancellor, Registrar, GBA). Institution and department roles receive scope 'LIST' with their
 * specific institution IDs.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private final UUID tenantId;
    private final IsolationMode isolationMode;
    private final String dbConnectionRef;
    private final String institutionScope; // "ALL", "LIST", or "NONE"
    private final Set<UUID> institutionIds;
    private final String activeRoleCode;
    private final ScopeType activeScopeType;
    /** A-02: user_role_assignment_id of the active role (the saved choice, or the only one). */
    private final UUID activeAssignmentId;
    /** A-02: true when the user holds several active assignments and has chosen none. */
    private final boolean activeRoleRequired;

    private TenantContext(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef,
                          String institutionScope, Set<UUID> institutionIds,
                          String activeRoleCode, ScopeType activeScopeType,
                          UUID activeAssignmentId, boolean activeRoleRequired) {
        this.tenantId = tenantId;
        this.isolationMode = isolationMode;
        this.dbConnectionRef = dbConnectionRef;
        this.institutionScope = institutionScope == null ? "NONE" : institutionScope;
        this.institutionIds = institutionIds == null ? Set.of() : Set.copyOf(institutionIds);
        this.activeRoleCode = activeRoleCode;
        this.activeScopeType = activeScopeType;
        this.activeAssignmentId = activeAssignmentId;
        this.activeRoleRequired = activeRoleRequired;
    }

    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef) {
        set(tenantId, isolationMode, dbConnectionRef, "NONE", Set.of(), null, null);
    }

    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef,
                           Set<UUID> institutionIds) {
        String scope = (institutionIds != null && !institutionIds.isEmpty()) ? "LIST" : "NONE";
        set(tenantId, isolationMode, dbConnectionRef, scope, institutionIds, null, null);
    }

    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef,
                           String institutionScope, Set<UUID> institutionIds,
                           String activeRoleCode, ScopeType activeScopeType) {
        set(tenantId, isolationMode, dbConnectionRef, institutionScope, institutionIds,
                activeRoleCode, activeScopeType, null, false);
    }

    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef,
                           String institutionScope, Set<UUID> institutionIds,
                           String activeRoleCode, ScopeType activeScopeType,
                           UUID activeAssignmentId, boolean activeRoleRequired) {
        CURRENT.set(new TenantContext(tenantId, isolationMode, dbConnectionRef,
                institutionScope, institutionIds, activeRoleCode, activeScopeType,
                activeAssignmentId, activeRoleRequired));
    }

    /** MUST be called at the end of every request, success or failure (see
     *  TenantContextClearingFilter) - Tomcat reuses worker threads across unrelated requests,
     *  so leaving a stale value here would leak one request's tenant into the next one that
     *  happens to land on the same thread. This is the single most important line in this
     *  whole package: skipping it on any code path is a cross-tenant data leak. */
    public static void clear() {
        CURRENT.remove();
    }

    /** Null means "no tenant resolved for this request" - either nothing has authenticated yet,
     *  or the caller is a Platform Admin (who is never given a tenant). Never substitute a
     *  default tenant here; callers must handle null explicitly. */
    public static UUID currentTenantId() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.tenantId;
    }

    public static IsolationMode currentIsolationMode() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.isolationMode;
    }

    /** Only meaningful when currentIsolationMode() == ISOLATED; null otherwise. */
    public static String currentDbConnectionRef() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.dbConnectionRef;
    }

    public static String currentInstitutionScope() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? "NONE" : ctx.institutionScope;
    }

    /** Never null - an empty set (no context set at all, or set(...) called without
     *  institutionIds) is the fail-closed default. Callers must never treat empty as "every institution." */
    public static Set<UUID> currentInstitutionIds() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? Set.of() : ctx.institutionIds;
    }

    public static String currentActiveRoleCode() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.activeRoleCode;
    }

    public static ScopeType currentActiveScopeType() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.activeScopeType;
    }

    /** A-02: the active role assignment chosen for this request, or null when none is known. */
    public static UUID currentActiveAssignmentId() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? null : ctx.activeAssignmentId;
    }

    /** A-02: true when the signed-in user holds several active roles and has not chosen one;
     *  ActiveRoleRequiredFilter then answers 409 ACTIVE_ROLE_REQUIRED. */
    public static boolean isActiveRoleRequired() {
        TenantContext ctx = CURRENT.get();
        return ctx != null && ctx.activeRoleRequired;
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }
}
