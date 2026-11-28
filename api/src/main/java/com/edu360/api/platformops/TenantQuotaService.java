package com.edu360.api.platformops;

import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.platformops.subscription.PlanComponentRepository;
import com.edu360.api.platformops.subscription.TenantSubscriptionAddonRepository;

/**
 * Serializes tenant-profile creation while checking the limits attached to the subscription.
 * The advisory lock lives in the tenant transaction, so the count and subsequent insert are atomic.
 */
@Service
public class TenantQuotaService {
    private static final int LOCK_NAMESPACE = 36041;

    private final JdbcTemplate tenantJdbc;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final PlanComponentRepository planComponentRepository;
    private final TenantSubscriptionAddonRepository addonRepository;

    public TenantQuotaService(JdbcTemplate tenantJdbc, TenantRepository tenantRepository,
            PlanRepository planRepository, PlanComponentRepository planComponentRepository,
            TenantSubscriptionAddonRepository addonRepository) {
        this.tenantJdbc = tenantJdbc;
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.planComponentRepository = planComponentRepository;
        this.addonRepository = addonRepository;
    }

    @Transactional(readOnly = true)
    public void checkStudentCapacity(UUID tenantId) {
        lockTenant(tenantId);
        Limits limits = limits(tenantId);
        enforceCount("SELECT count(*) FROM app.student_profile WHERE tenant_id = ? AND status = 'ACTIVE'",
                tenantId, limits.maxStudents(), "active student profiles", "students", limits.planName());
    }

    @Transactional(readOnly = true)
    public void checkStaffCapacity(UUID tenantId) {
        lockTenant(tenantId);
        Limits limits = limits(tenantId);
        enforceCount("SELECT count(*) FROM app.staff_profile WHERE tenant_id = ? AND employment_status = 'ACTIVE'",
                tenantId, limits.maxStaff(), "active staff profiles", "staff", limits.planName());
    }

    @Transactional(readOnly = true)
    public void checkInstitutionCapacity(UUID tenantId, String institutionType) {
        lockTenant(tenantId);
        Limits limits = limits(tenantId);
        long all = count("SELECT count(*) FROM app.institution WHERE tenant_id = ? AND status <> 'ARCHIVED'", tenantId);
        if (limits.maxInstitutions() != null && all >= limits.maxInstitutions()) {
            throw limitReached("institutions", limits.maxInstitutions(), limits.planName());
        }

        if ("COLLEGE".equalsIgnoreCase(institutionType)) {
            enforceCount("SELECT count(*) FROM app.institution WHERE tenant_id = ? AND institution_type = 'COLLEGE' AND status <> 'ARCHIVED'",
                    tenantId, limits.maxColleges(), "colleges", "colleges", limits.planName());
        } else if ("SCHOOL".equalsIgnoreCase(institutionType)) {
            enforceCount("SELECT count(*) FROM app.institution WHERE tenant_id = ? AND institution_type = 'SCHOOL' AND status <> 'ARCHIVED'",
                    tenantId, limits.maxSchools(), "schools", "schools", limits.planName());
        }
    }

    private void lockTenant(UUID tenantId) {
        tenantJdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?), ?)")) {
                statement.setString(1, tenantId.toString());
                statement.setInt(2, LOCK_NAMESPACE);
                statement.execute();
            }
            return null;
        });
    }

    private Limits limits(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return new Limits(null, null, null, null, null, null);
        }
        Plan plan = tenant.getPlanId() == null ? null : planRepository.findById(tenant.getPlanId()).orElse(null);
        boolean v2 = "V2".equals(tenant.getSubscriptionEngineVersion());
        return new Limits(
                effectiveCapacity(tenant.getMaxStudents() != null ? tenant.getMaxStudents()
                                : v2 && plan != null ? plan.getMaxStudents() : null,
                        tenant.getPlanId(), tenantId, "STUDENT_PACK", v2),
                effectiveCapacity(tenant.getMaxStaff() != null ? tenant.getMaxStaff()
                                : v2 && plan != null ? plan.getMaxStaff() : null,
                        tenant.getPlanId(), tenantId, "STAFF_PACK", v2),
                effectiveCapacity(tenant.getMaxColleges() != null ? tenant.getMaxColleges()
                                : v2 && plan != null ? plan.getMaxColleges() : null,
                        tenant.getPlanId(), tenantId, "COLLEGE", v2),
                effectiveCapacity(tenant.getMaxSchools() != null ? tenant.getMaxSchools()
                                : v2 && plan != null ? plan.getMaxSchools() : null,
                        tenant.getPlanId(), tenantId, "SCHOOL", v2),
                effectiveInstitutionCapacity(plan == null ? null : plan.getMaxInstitutions(),
                        tenant.getPlanId(), tenantId, v2),
                plan == null ? "subscription" : plan.getPlanName());
    }

    private Integer effectiveCapacity(Integer base, UUID planId, UUID tenantId, String unitCode, boolean v2) {
        if (base == null || planId == null) {
            return base;
        }
        BigDecimal included = v2 ? planComponentRepository.includedCapacity(planId, unitCode) : BigDecimal.ZERO;
        BigDecimal purchased = addonRepository.activeCapacity(tenantId, unitCode, java.time.LocalDate.now());
        int additional = included.add(purchased).intValueExact();
        return Math.addExact(base, additional);
    }

    private Integer effectiveInstitutionCapacity(Integer base, UUID planId, UUID tenantId, boolean v2) {
        if (base == null) return null;
        BigDecimal additional = addonRepository.activeCapacity(tenantId, "INSTITUTION", java.time.LocalDate.now());
        if (v2 && planId != null) {
            additional = additional.add(planComponentRepository.includedCapacity(planId, "INSTITUTION"));
            additional = additional.add(planComponentRepository.includedCapacity(planId, "COLLEGE"));
            additional = additional.add(planComponentRepository.includedCapacity(planId, "SCHOOL"));
        }
        additional = additional.add(addonRepository.activeCapacity(tenantId, "COLLEGE", java.time.LocalDate.now()));
        additional = additional.add(addonRepository.activeCapacity(tenantId, "SCHOOL", java.time.LocalDate.now()));
        return Math.addExact(base, additional.intValueExact());
    }

    private long count(String sql, UUID tenantId) {
        Long count = tenantJdbc.queryForObject(sql, Long.class, tenantId);
        return count == null ? 0L : count;
    }

    private void enforceCount(String sql, UUID tenantId, Integer cap, String description,
            String label, String planName) {
        if (cap != null && count(sql, tenantId) >= cap) {
            throw limitReached(label, cap, planName);
        }
    }

    private static BusinessRuleViolationException limitReached(String label, int cap, String planName) {
        return new BusinessRuleViolationException("PLAN_LIMIT_REACHED",
                "Plan limit reached: maximum " + cap + " " + label + " allowed under plan '" + planName + "'.");
    }

    private record Limits(Integer maxStudents, Integer maxStaff, Integer maxColleges,
            Integer maxSchools, Integer maxInstitutions, String planName) { }
}
