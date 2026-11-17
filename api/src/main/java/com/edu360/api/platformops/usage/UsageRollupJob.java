package com.edu360.api.platformops.usage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.edu360.api.platformops.UsageMetricDaily;
import com.edu360.api.platformops.UsageMetricDailyRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

/** Materializes tenant-isolated usage for the previous UTC day. */
@Component
public class UsageRollupJob {
    private static final Logger log = LoggerFactory.getLogger(UsageRollupJob.class);
    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final List<String> COMBINED = List.of("LOGINS", "ATTENDANCE_MARKED", "FEES_PAID_ONLINE");
    private final TenantRepository tenants;
    private final TenantUsageMonthlyRepository monthly;
    private final UsageMetricDailyRepository platformDaily;
    private final JdbcTemplate tenantJdbc;

    public UsageRollupJob(TenantRepository tenants, TenantUsageMonthlyRepository monthly,
            UsageMetricDailyRepository platformDaily, JdbcTemplate tenantJdbc) {
        this.tenants = tenants; this.monthly = monthly; this.platformDaily = platformDaily; this.tenantJdbc = tenantJdbc;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void runNightly() { rollup(LocalDate.now(ZoneOffset.UTC)); }

    void rollup(LocalDate today) {
        LocalDate day = today.minusDays(1), month = day.withDayOfMonth(1);
        Timestamp from = Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp to = Timestamp.from(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        Map<String, Long> combined = new LinkedHashMap<>();
        COMBINED.forEach(code -> combined.put(code, 0L));
        boolean complete = true;
        boolean hasIncludedTenant = false;
        List<Tenant> eligible = tenants.findAll().stream()
                .filter(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL).toList();
        for (Tenant tenant : eligible) {
            try {
                TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef());
                Map<String, Long> values = collect(from, to);
                writeDaily(tenant.getTenantId(), day, values);
                writeMonthly(tenant.getTenantId(), month, day);
                if (!tenant.isAnalyticsOptOut()) COMBINED.forEach(c -> combined.merge(c, values.get(c), Long::sum));
            } catch (Exception ex) {
                log.error("Usage rollup failed for tenant {}", tenant.getTenantId(), ex);
                if (!tenant.isAnalyticsOptOut()) complete = false;
            } finally { TenantContext.clear(); }
        }
        // A partial aggregate is never published when an included tenant failed.
        if (complete && hasIncludedTenant) combined.forEach((code, value) -> upsertPlatformDaily(day, code, value));
    }

    private Map<String, Long> collect(Timestamp from, Timestamp to) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("LOGINS", count("SELECT count(*) FROM app.audit_outbox WHERE occurred_at >= ? AND occurred_at < ? AND operation='LOGIN' AND entity_type='LOGIN_SUCCESS' AND outcome='SUCCESS'", from, to));
        m.put("ATTENDANCE_MARKED", count("SELECT count(*) FROM app.attendance_entry WHERE created_at >= ? AND created_at < ?", from, to));
        m.put("FEES_PAID_ONLINE", count("SELECT count(*) FROM app.payment WHERE method='GATEWAY' AND status='PAID' AND paid_at >= ? AND paid_at < ?", from, to));
        m.put("ACTIVE_USERS", scalar("SELECT count(*) FROM app.user_account WHERE status='ACTIVE'"));
        return m;
    }
    private long count(String sql, Timestamp a, Timestamp b) {
        Long v = tenantJdbc.queryForObject(sql, Long.class, a, b); return v == null ? 0L : v;
    }
    private long scalar(String sql) {
        Long v = tenantJdbc.queryForObject(sql, Long.class); return v == null ? 0L : v;
    }
    private void writeDaily(UUID tenantId, LocalDate day, Map<String, Long> values) {
        for (String code : List.of("LOGINS", "ATTENDANCE_MARKED", "FEES_PAID_ONLINE", "ACTIVE_USERS")) {
            tenantJdbc.update("""
                INSERT INTO app.usage_metric_daily
                    (tenant_id, usage_metric_daily_id, institution_id, usage_date, metric_code, value, created_by, updated_by)
                VALUES (?, ?, NULL, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, usage_date, metric_code) WHERE institution_id IS NULL
                DO UPDATE SET value=EXCLUDED.value, updated_by=EXCLUDED.updated_by
                """, tenantId, UUID.randomUUID(), day, code, values.get(code), SYSTEM_USER, SYSTEM_USER);
        }
    }
    private void writeMonthly(UUID tenantId, LocalDate month, LocalDate day) {
        Instant now = Instant.now(); LocalDate next = month.plusMonths(1);
        BigDecimal logins = sum(month, next, "LOGINS");
        BigDecimal attendance = sum(month, next, "ATTENDANCE_MARKED");
        BigDecimal payments = sum(month, next, "FEES_PAID_ONLINE");
        BigDecimal active = max(month, next, "ACTIVE_USERS");
        BigDecimal storage = storageGb();
        boolean finalMonth = day.equals(next.minusDays(1));
        save(tenantId, month, "LOGINS", logins, true, false, finalMonth, now);
        save(tenantId, month, "ATTENDANCE_MARKED", attendance, true, false, finalMonth, now);
        save(tenantId, month, "FEES_PAID_ONLINE", payments, true, false, finalMonth, now);
        save(tenantId, month, "SMS_SENT", BigDecimal.ZERO, false, false, finalMonth, now);
        save(tenantId, month, "STORAGE_GB", storage, true, true, finalMonth, now);
        save(tenantId, month, "ACTIVE_USERS", active, true, true, finalMonth, now);
    }
    private BigDecimal sum(LocalDate a, LocalDate b, String code) {
        Long v = tenantJdbc.queryForObject("SELECT COALESCE(sum(value),0) FROM app.usage_metric_daily WHERE usage_date>=? AND usage_date<? AND metric_code=? AND institution_id IS NULL", Long.class, a, b, code);
        return BigDecimal.valueOf(v == null ? 0L : v);
    }
    private BigDecimal max(LocalDate a, LocalDate b, String code) {
        Long v = tenantJdbc.queryForObject("SELECT COALESCE(max(value),0) FROM app.usage_metric_daily WHERE usage_date>=? AND usage_date<? AND metric_code=? AND institution_id IS NULL", Long.class, a, b, code);
        return BigDecimal.valueOf(v == null ? 0L : v);
    }
    private BigDecimal storageGb() {
        Long v = tenantJdbc.queryForObject("SELECT COALESCE(sum(size_bytes),0) FROM app.file_object WHERE status<>'DELETED'", Long.class);
        return BigDecimal.valueOf(v == null ? 0L : v).divide(BigDecimal.valueOf(1073741824L), 2, RoundingMode.HALF_UP);
    }
    private void save(UUID tenantId, LocalDate month, String code, BigDecimal value, boolean measured,
            boolean peak, boolean finalMonth, Instant now) {
        TenantUsageMonthly row = monthly.findByTenantIdAndUsageMonthAndMetricCode(tenantId, month, code)
            .orElseGet(() -> TenantUsageMonthly.builder().tenantUsageMonthlyId(UUID.randomUUID())
                .tenantId(tenantId).usageMonth(month).metricCode(code).quantity(BigDecimal.ZERO)
                .measured(measured).computedAt(now).isFinal(false).createdAt(now).createdBy(SYSTEM_USER)
                .updatedAt(now).updatedBy(SYSTEM_USER).rowVersion(1).build());
        if (row.isFinal()) return;
        row.setQuantity(peak ? row.getQuantity().max(value) : value);
        row.setMeasured(measured); row.setComputedAt(now); row.setFinal(finalMonth);
        row.setUpdatedAt(now); row.setUpdatedBy(SYSTEM_USER); monthly.save(row);
    }
    private void upsertPlatformDaily(LocalDate day, String code, long value) {
        UsageMetricDaily row = platformDaily.findByUsageDateAndMetricCode(day, code)
            .orElseGet(() -> UsageMetricDaily.builder().usageMetricDailyId(UUID.randomUUID())
                .usageDate(day).metricCode(code).createdAt(Instant.now()).createdBy(SYSTEM_USER)
                .updatedAt(Instant.now()).updatedBy(SYSTEM_USER).rowVersion(1).build());
        row.setValue(value); row.setUpdatedAt(Instant.now()); row.setUpdatedBy(SYSTEM_USER); platformDaily.save(row);
    }
}
