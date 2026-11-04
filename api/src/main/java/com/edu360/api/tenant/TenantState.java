package com.edu360.api.tenant;

/** Mirrors ck_tenant_state in sql/01_schema.sql. Only CREATED/TRIAL/ACTIVE tenants may ever
 *  authenticate - SUSPENDED/ARCHIVED/DELETED must be refused at the bootstrap resolver, before
 *  any tenant-scoped query runs (see TenantResolver.isLoginAllowed). */
public enum TenantState {
    CREATED,
    TRIAL,
    ACTIVE,
    SUSPENDED,
    ARCHIVED,
    DELETED
}
