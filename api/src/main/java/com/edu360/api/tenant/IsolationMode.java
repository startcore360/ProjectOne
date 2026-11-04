package com.edu360.api.tenant;

/** Mirrors ck_tenant_isolation_mode. SHARED = one Postgres database, tenants kept apart by RLS.
 *  ISOLATED = this tenant gets its own database, found via Secrets Manager (dbConnectionRef) -
 *  see TenantRoutingDataSource. */
public enum IsolationMode {
    SHARED,
    ISOLATED
}
