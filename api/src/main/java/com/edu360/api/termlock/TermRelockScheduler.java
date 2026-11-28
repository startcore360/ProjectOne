package com.edu360.api.termlock;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Hourly, tenant-scoped expiry worker for temporary term unlocks. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TermRelockScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    private record TenantTarget(UUID tenantId, IsolationMode isolationMode, String dbConnectionRef) {}

    @Scheduled(cron = "0 0 * * * *")
    public void relockExpiredTerms() {
        List<TenantTarget> tenants = jdbcTemplate.query(
                "SELECT tenant_id, isolation_mode, db_connection_ref FROM platform.tenant ORDER BY tenant_id",
                (rs, row) -> new TenantTarget(rs.getObject("tenant_id", UUID.class),
                        IsolationMode.valueOf(rs.getString("isolation_mode")), rs.getString("db_connection_ref")));
        int total = 0;
        for (TenantTarget tenant : tenants) {
            TenantContext.set(tenant.tenantId(), tenant.isolationMode(), tenant.dbConnectionRef(),
                    "ALL", Set.of(), null, null);
            try {
                Integer count = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                            String.class, tenant.tenantId().toString());
                    jdbcTemplate.queryForObject("SELECT set_config('app.institution_scope', 'ALL', true)", String.class);
                    jdbcTemplate.queryForObject("SELECT set_config('app.institution_ids', '', true)", String.class);
                    return jdbcTemplate.queryForObject(
                            "SELECT app.fn_relock_expired_terms(?)", Integer.class, tenant.tenantId());
                });
                total += count == null ? 0 : count;
            } catch (RuntimeException ex) {
                log.error("Automatic term relock failed for tenant {}", tenant.tenantId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (total > 0) log.info("Automatically relocked {} expired academic term unlock(s)", total);
    }
}
