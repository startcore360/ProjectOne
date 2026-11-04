package com.edu360.api.tenant;

import java.util.Set;
import java.util.UUID;

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
 * so an empty institutionIds set here, combined with TenantRoutingDataSource never setting
 * app.institution_scope to 'ALL', makes every institution-scoped row invisible - the same
 * fail-closed philosophy as the no-tenant-context case above, extended one layer down.
 *
 * WHY it defaults to empty rather than "no restriction": Foundation Item 7 (the 71-role/280-
 * action model) is what will eventually compute "which institutions can this principal see"
 * from the caller's active role assignment - that data does not exist anywhere in the current
 * identity model (UserAccount/UserIdentityLink carry no institution assignment at all). Until
 * Foundation Item 7 exists, every caller of set(...) that does not explicitly pass
 * institutionIds gets the empty set, which is the only honest, fail-closed placeholder: it never
 * grants blanket institution access as a shortcut, and it never leaves the GUC unset either (see
 * TenantRoutingDataSource.applyTenantGucs, which always explicitly SETs app.institution_ids -
 * never relies on it merely being unset - because this is a pooled connection and a bare RESET
 * on one code path while another path forgets to touch it at all would be exactly the kind of
 * cross-request leak this class exists to prevent).
 */
public final class TenantContext {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private final UUID tenantId;
    private final IsolationMode isolationMode;
    private final String dbConnectionRef;
    private final Set<UUID> institutionIds;

    private TenantContext(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef,
                           Set<UUID> institutionIds) {
        this.tenantId = tenantId;
        this.isolationMode = isolationMode;
        this.dbConnectionRef = dbConnectionRef;
        this.institutionIds = institutionIds;
    }

    /**
     * Called exactly once per request, from inside the already-verified JWT converter - which
     * already has the full Tenant row in hand at that point, so it passes dbConnectionRef
     * straight through here rather than making TenantRoutingDataSource look the tenant back up
     * itself.
     *
     * WHY that matters: platform.tenant lives in the shared/central database ALWAYS, even for
     * an ISOLATED tenant whose actual app data lives in its own separate database. If
     * TenantRoutingDataSource re-queried TenantRepository to find an isolated tenant's
     * connection details, that query would recursively ask the SAME routing datasource for a
     * connection to resolve itself - a reentrancy bug. Carrying dbConnectionRef here instead
     * means the routing datasource never needs a repository dependency of its own at all.
     *
     * Overload without institutionIds: delegates to the 4-arg set(...) below with an empty set -
     * see the class comment's INSTITUTION SCOPE section for why empty, not "no restriction", is
     * the correct default until Foundation Item 7 exists. Every current production caller
     * (TenantUserLookup) uses this overload today, since no institution-assignment data exists
     * yet to pass.
     */
    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef) {
        set(tenantId, isolationMode, dbConnectionRef, Set.of());
    }

    /**
     * Full form, for callers that already know which institutions the caller may see (once
     * Foundation Item 7 exists) and for tests exercising institution scoping directly.
     * institutionIds is defensively copied into an immutable set; a null argument is treated as
     * the fail-closed empty set rather than throwing, so a caller that forgets to compute it yet
     * never accidentally widens access either.
     */
    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef,
                            Set<UUID> institutionIds) {
        Set<UUID> safeInstitutionIds = institutionIds == null ? Set.of() : Set.copyOf(institutionIds);
        CURRENT.set(new TenantContext(tenantId, isolationMode, dbConnectionRef, safeInstitutionIds));
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

    /** Never null - an empty set (no context set at all, or set(...) called without
     *  institutionIds) is the fail-closed default described in the class comment's INSTITUTION
     *  SCOPE section, not a sentinel for "unrestricted." Callers must never treat empty as
     *  "every institution." */
    public static Set<UUID> currentInstitutionIds() {
        TenantContext ctx = CURRENT.get();
        return ctx == null ? Set.of() : ctx.institutionIds;
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }
}
