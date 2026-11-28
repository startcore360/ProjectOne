package com.edu360.api.finance;

import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly multi-tenant runner for PO-T03 late-fee accrual. */
@Component
@RequiredArgsConstructor
public class LateFeeCalculationScheduler {
    private static final Logger log = LoggerFactory.getLogger(LateFeeCalculationScheduler.class);
    private final TenantRepository tenants;
    private final NamedParameterJdbcTemplate jdbc;
    private final LateFeeCalculationService service;

    @Scheduled(cron = "0 15 1 * * *", zone = "UTC")
    public void calculateNightly() {
        for (Tenant tenant : tenants.findAllByOrderByTenantNameAsc()) {
            if (tenant.getState() != TenantState.ACTIVE && tenant.getState() != TenantState.TRIAL) {
                continue;
            }
            try {
                TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                        "ALL", Set.of(), "SYSTEM", ScopeType.TENANT);
                UUID systemActorId = jdbc.query("""
                    SELECT user_account_id
                      FROM app.user_account
                     WHERE tenant_id=:tenantId AND user_type='SYSTEM' AND status='ACTIVE'
                     ORDER BY created_at
                     LIMIT 1
                    """, java.util.Map.of("tenantId", tenant.getTenantId()),
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
                if (systemActorId == null) {
                    log.warn("Skipping late-fee run for tenant {}: no active SYSTEM actor is provisioned.",
                            tenant.getTenantId());
                    continue;
                }
                ZoneId zone;
                try {
                    zone = ZoneId.of(tenant.getTimeZone());
                } catch (RuntimeException invalidZone) {
                    zone = ZoneId.of("Asia/Kolkata");
                }
                int inserted = service.calculateForTenant(LocalDate.now(zone), systemActorId);
                log.info("PO-T03 late-fee run tenant={} insertedLedgerEntries={}", tenant.getTenantId(), inserted);
            } catch (Exception ex) {
                log.error("PO-T03 late-fee run failed for tenant {}.", tenant.getTenantId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
