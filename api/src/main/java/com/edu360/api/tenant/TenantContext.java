package com.edu360.api.tenant;

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
 */
public final class TenantContext {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private final UUID tenantId;
    private final IsolationMode isolationMode;
    private final String dbConnectionRef;

    private TenantContext(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef) {
        this.tenantId = tenantId;
        this.isolationMode = isolationMode;
        this.dbConnectionRef = dbConnectionRef;
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
     */
    public static void set(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef) {
        CURRENT.set(new TenantContext(tenantId, isolationMode, dbConnectionRef));
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

    public static boolean isSet() {
        return CURRENT.get() != null;
    }
}
