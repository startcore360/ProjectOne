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
import java.util.Set;
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

/** Materializes monthly tenant usage and anonymous platform daily totals under tenant-specific contexts. */
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
        this.tenants = tenants;
        this.monthly = monthly;
        this.platformDaily = platformDaily;
        this.tenantJdbc = tenantJdbc;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void runNightly() { rollup(LocalDate.now(ZoneOffset.UTC)); }

    void rollup(LocalDate today) {
        LocalDate day = today.minusDays(1);
        LocalDate month = day.withDayOfMonth(1);
        Timestamp dayStart = Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp dayEnd = Timestamp.from(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp monthStart = Timestamp.from(month.atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<String, Long> combined = new LinkedHashMap<>();
        COMBINED.forEach(code -> combined.put(code, 0L));
        boolean complete = true;
        boolean hasIncludedTenant = false;

        List<Tenant> eligible = tenants.findAll().stream()
                .filter(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL)
                .toList();

        for (Tenant tenant : eligible) {
            try {
                TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                        "ALL", Set.of(), null, null);
                EventCounts dailyCounts = collectEvents(dayStart, dayEnd);
                MonthlySnapshot monthlySnapshot = collectMonthly(monthStart, dayEnd);
                writeMonthly(tenant.getTenantId(), month, day, monthlySnapshot);

                // Tenant monthly usage remains available to billing. Analytics opt-outs never
                // contribute to platform-wide daily aggregates.
                if (!tenant.isAnalyticsOptOut()) {
                    hasIncludedTenant = true;
                    combined.merge("LOGINS", dailyCounts.logins(), Long::sum);
                    combined.merge("ATTENDANCE_MARKED", dailyCounts.attendanceMarked(), Long::sum);
                    combined.merge("FEES_PAID_ONLINE", dailyCounts.feesPaidOnline(), Long::sum);
                }
            } catch (Exception ex) {
                log.error("Usage rollup failed for tenant {}", tenant.getTenantId(), ex);
                if (!tenant.isAnalyticsOptOut()) complete = false;
            } finally {
                TenantContext.clear();
            }
        }

        // Never publish a partial total or a meaningless zero when no tenant opted into analytics.
        if (complete && hasIncludedTenant) {
            combined.forEach((code, value) -> upsertPlatformDaily(day, code, value));
        }
    }

    private EventCounts collectEvents(Timestamp from, Timestamp to) {
        long logins = count("""
                SELECT count(*) FROM app.audit_outbox
                 WHERE occurred_at >= ? AND occurred_at < ?
                   AND operation = 'LOGIN' AND entity_type = 'LOGIN_SUCCESS' AND outcome = 'SUCCESS'
                """, from, to);
        long attendance = count("""
                SELECT count(*) FROM app.attendance_entry
                 WHERE created_at >= ? AND created_at < ?
                """, from, to);
        long onlinePayments = count("""
                SELECT count(*) FROM app.payment
                 WHERE method = 'GATEWAY' AND status = 'PAID' AND paid_at >= ? AND paid_at < ?
                """, from, to);
        return new EventCounts(logins, attendance, onlinePayments);
    }

    private MonthlySnapshot collectMonthly(Timestamp from, Timestamp to) {
        EventCounts events = collectEvents(from, to);
        long activeUsers = scalar("SELECT count(*) FROM app.user_account WHERE status = 'ACTIVE'");
        long activeStudents = scalar("SELECT count(*) FROM app.student_profile WHERE status = 'ACTIVE'");
        long activeStaff = scalar("SELECT count(*) FROM app.staff_profile WHERE employment_status = 'ACTIVE'");
        long activeColleges = scalar("SELECT count(*) FROM app.institution WHERE institution_type = 'COLLEGE' AND status <> 'ARCHIVED'");
        long activeSchools = scalar("SELECT count(*) FROM app.institution WHERE institution_type = 'SCHOOL' AND status <> 'ARCHIVED'");
        LocalDate snapshotDate = to.toInstant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        Long billableStudents = tenantJdbc.queryForObject("""
                SELECT count(DISTINCT e.student_profile_id)
                  FROM app.student_enrollment e
                  JOIN app.student_profile s
                    ON s.tenant_id = e.tenant_id
                   AND s.student_profile_id = e.student_profile_id
                 WHERE e.outcome = 'IN_PROGRESS'
                   AND e.started_on <= ?
                   AND (e.ended_on IS NULL OR e.ended_on >= ?)
                   AND s.status = 'ACTIVE'
                """, Long.class, snapshotDate, snapshotDate);
        long storageBytes = scalar("SELECT COALESCE(sum(size_bytes), 0) FROM app.file_object WHERE status <> 'DELETED'");
        BigDecimal storageGb = BigDecimal.valueOf(storageBytes)
                .divide(BigDecimal.valueOf(1024L * 1024L * 1024L), 2, RoundingMode.HALF_UP);
        return new MonthlySnapshot(events, activeUsers, activeStudents, activeStaff, activeColleges,
                activeSchools, billableStudents == null ? 0L : billableStudents, storageGb);
    }

    private long count(String sql, Timestamp from, Timestamp to) {
        Long value = tenantJdbc.queryForObject(sql, Long.class, from, to);
        return value == null ? 0L : value;
    }

    private long scalar(String sql) {
        Long value = tenantJdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private void writeMonthly(UUID tenantId, LocalDate month, LocalDate day, MonthlySnapshot snapshot) {
        Instant now = Instant.now();
        boolean finalMonth = day.equals(month.plusMonths(1).minusDays(1));
        saveMonthly(tenantId, month, "LOGINS", BigDecimal.valueOf(snapshot.events().logins()), true, false, finalMonth, now);
        saveMonthly(tenantId, month, "ATTENDANCE_MARKED", BigDecimal.valueOf(snapshot.events().attendanceMarked()), true, false, finalMonth, now);
        saveMonthly(tenantId, month, "FEES_PAID_ONLINE", BigDecimal.valueOf(snapshot.events().feesPaidOnline()), true, false, finalMonth, now);
        // No SMS delivery source is integrated in this branch. Zero is explicitly unmeasured.
        saveMonthly(tenantId, month, "SMS_SENT", BigDecimal.ZERO, false, false, finalMonth, now);
        saveMonthly(tenantId, month, "STORAGE_GB", snapshot.storageGb(), true, true, finalMonth, now);
        saveMonthly(tenantId, month, "ACTIVE_USERS", BigDecimal.valueOf(snapshot.activeUsers()), true, true, finalMonth, now);
        saveMonthly(tenantId, month, "ACTIVE_STUDENTS", BigDecimal.valueOf(snapshot.activeStudents()), true, true, finalMonth, now);
        saveMonthly(tenantId, month, "ACTIVE_STAFF", BigDecimal.valueOf(snapshot.activeStaff()), true, true, finalMonth, now);
        saveMonthly(tenantId, month, "ACTIVE_COLLEGES", BigDecimal.valueOf(snapshot.activeColleges()), true, true, finalMonth, now);
        saveMonthly(tenantId, month, "ACTIVE_SCHOOLS", BigDecimal.valueOf(snapshot.activeSchools()), true, true, finalMonth, now);
        saveMonthly(tenantId, month, "BILLABLE_ACTIVE_STUDENTS",
                BigDecimal.valueOf(snapshot.billableActiveStudents()), true, false, finalMonth, now);
    }

    private void saveMonthly(UUID tenantId, LocalDate month, String code, BigDecimal value,
            boolean measured, boolean peak, boolean finalMonth, Instant now) {
        TenantUsageMonthly row = monthly.findByTenantIdAndUsageMonthAndMetricCode(tenantId, month, code)
                .orElseGet(() -> TenantUsageMonthly.builder()
                        .tenantUsageMonthlyId(UUID.randomUUID()).tenantId(tenantId).usageMonth(month)
                        .metricCode(code).quantity(BigDecimal.ZERO).measured(measured).computedAt(now)
                        .isFinal(false).createdAt(now).createdBy(SYSTEM_USER).updatedAt(now)
                        .updatedBy(SYSTEM_USER).rowVersion(1).build());
        if (row.isFinal()) return;
        row.setQuantity(peak ? row.getQuantity().max(value) : value);
        row.setMeasured(measured);
        row.setComputedAt(now);
        row.setFinal(finalMonth);
        row.setUpdatedAt(now);
        row.setUpdatedBy(SYSTEM_USER);
        monthly.save(row);
    }

    private void upsertPlatformDaily(LocalDate day, String code, long value) {
        UsageMetricDaily row = platformDaily.findByUsageDateAndMetricCode(day, code)
                .orElseGet(() -> UsageMetricDaily.builder()
                        .usageMetricDailyId(UUID.randomUUID()).usageDate(day).metricCode(code)
                        .createdAt(Instant.now()).createdBy(SYSTEM_USER)
                        .updatedAt(Instant.now()).updatedBy(SYSTEM_USER).rowVersion(1).build());
        row.setValue(value);
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(SYSTEM_USER);
        platformDaily.save(row);
    }

    private record EventCounts(long logins, long attendanceMarked, long feesPaidOnline) { }
    private record MonthlySnapshot(EventCounts events, long activeUsers, long activeStudents, long activeStaff,
            long activeColleges, long activeSchools, long billableActiveStudents, BigDecimal storageGb) { }
}
