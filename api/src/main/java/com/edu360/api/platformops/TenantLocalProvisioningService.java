package com.edu360.api.platformops;

import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the shared-database tenant anchor used by app-schema foreign keys and RLS.
 *
 * The caller must establish TenantContext before invoking this service. The routing datasource
 * then applies the tenant GUC to the same connection used for this transaction.
 */
@Service
public class TenantLocalProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    public TenantLocalProvisioningService(@Qualifier("dataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Transactional(transactionManager = "transactionManager")
    public void registerTenant(UUID tenantId, String tenantType, String timeZone) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantType == null || tenantType.isBlank()) {
            throw new IllegalArgumentException("Tenant type is required.");
        }
        String normalizedTimeZone = timeZone == null || timeZone.isBlank() ? "Asia/Kolkata" : timeZone;
        jdbcTemplate.update(
                "INSERT INTO app.tenant_local (tenant_id, tenant_type, time_zone, provisioned_at) "
                        + "VALUES (?, ?, ?, now()) ON CONFLICT (tenant_id) DO NOTHING",
                tenantId, tenantType, normalizedTimeZone);
    }
}
