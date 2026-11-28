package com.edu360.api.attendance.staffcheckin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: day-end expiry of staff self-check-ins. Any PROVISIONAL check-in captured before local
 * midnight of the current day - in ITS institution's own configured timezone
 * (app.institution_attendance_location.timezone) - becomes CHECK_IN_ONLY and its approval request
 * escalates to the next higher authority (StaffCheckInService.expireProvisional).
 *
 * HOW: runs hourly (minute 5, UTC) rather than once a day, so every timezone is processed within
 * about an hour of its own midnight; the work is idempotent because only PROVISIONAL rows qualify.
 * Same multi-tenant shape as finance/LateFeeCalculationScheduler. Each row is its own transaction
 * and its own failure: one bad row never stops the rest of the tenant.
 *
 * WHY a system principal: AuditLogService refuses a structured tenant event without an
 * authenticated tenant actor, so without it every expiry would roll back. The job acts as the
 * tenant's provisioned SYSTEM user (PTD-23, the same actor LateFeeCalculationScheduler uses for
 * created_by) and clears the security context afterwards.
 */
@Component
@RequiredArgsConstructor
public class StaffCheckinExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(StaffCheckinExpiryScheduler.class);
    static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    private final TenantRepository tenants;
    private final NamedParameterJdbcTemplate jdbc;
    private final InstitutionRepository institutions;
    private final InstitutionAttendanceLocationRepository locations;
    private final StaffAttendanceCheckinRepository checkins;
    private final StaffCheckInService service;

    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    public void expireProvisionalHourly() {
        for (Tenant tenant : tenants.findAllByOrderByTenantNameAsc()) {
            if (tenant.getState() != TenantState.ACTIVE && tenant.getState() != TenantState.TRIAL) {
                continue;
            }
            SecurityContext previous = SecurityContextHolder.getContext();
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
                    log.warn("Skipping staff check-in expiry for tenant {}: no active SYSTEM actor is provisioned.",
                            tenant.getTenantId());
                    continue;
                }
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                        new TenantPrincipal(tenant.getTenantId(), systemActorId, UserType.SYSTEM,
                                "Staff check-in day-end job", false),
                        null, List.of()));
                SecurityContextHolder.setContext(context);
                int expired = expireForTenant(tenant.getTenantId(), systemActorId, Instant.now());
                if (expired > 0) {
                    log.info("Staff check-in day-end expiry tenant={} expired={}", tenant.getTenantId(), expired);
                }
            } catch (Exception ex) {
                log.error("Staff check-in expiry failed for tenant {}", tenant.getTenantId(), ex);
            } finally {
                SecurityContextHolder.setContext(previous);
                TenantContext.clear();
            }
        }
    }

    /** Expires every qualifying row of one tenant; package-visible for tests. */
    int expireForTenant(UUID tenantId, UUID systemActorId, Instant now) {
        int expired = 0;
        for (InstitutionAttendanceLocation location : locations.findByTenantId(tenantId)) {
            Institution institution = institutions
                    .findByIdTenantIdAndIdInstitutionId(tenantId, location.getInstitutionId()).orElse(null);
            Instant cutoff = localMidnightCutoff(location.getTimezone(), now);
            for (StaffAttendanceCheckin row : checkins.findByTenantIdAndInstitutionIdAndStatusAndCapturedAtBefore(
                    tenantId, location.getInstitutionId(), StaffCheckInService.STATUS_PROVISIONAL, cutoff)) {
                try {
                    if (service.expireProvisional(tenantId, row.getStaffAttendanceCheckinId(), systemActorId,
                            location, institution)) {
                        expired++;
                    }
                } catch (RuntimeException ex) {
                    log.error("Staff check-in expiry failed for tenant {} check-in {}", tenantId,
                            row.getStaffAttendanceCheckinId(), ex);
                }
            }
        }
        return expired;
    }

    /**
     * Local midnight of the institution's current day, in its own timezone: anything captured
     * before it and still PROVISIONAL has missed its same-day review window. An invalid stored
     * zone (the config endpoint rejects new ones) falls back to the column default.
     */
    static Instant localMidnightCutoff(String timezone, Instant now) {
        ZoneId zone = attendanceZone(timezone);
        ZonedDateTime localMidnightToday = ZonedDateTime.ofInstant(now, zone).toLocalDate().atStartOfDay(zone);
        return localMidnightToday.toInstant();
    }

    /**
     * The institution's check-in timezone; an invalid stored zone falls back to the column
     * default. Shared with StaffCheckInService so the "one check-in per day" date and the day-end
     * cutoff always agree on what "today" is.
     */
    static ZoneId attendanceZone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone);
        } catch (RuntimeException ex) {
            log.warn("Invalid attendance timezone '{}'; using {}", timezone, DEFAULT_TIMEZONE);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
