package com.edu360.api.attendance.staffcheckin;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.attendance.staffcheckin.dto.LocationConfigRequest;
import com.edu360.api.attendance.staffcheckin.dto.LocationConfigResponse;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.RoleDefinitionRepository;

/** STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE: institution/tenant leadership configures the geofence. */
@Service
public class AttendanceLocationConfigService {

    static final int MAX_RADIUS_METERS = 5000;
    static final int MAX_ACCURACY_METERS = 1000;

    private final PermissionEvaluatorService permissions;
    private final InstitutionRepository institutions;
    private final InstitutionAttendanceLocationRepository locations;
    private final RoleDefinitionRepository roleDefinitions;
    private final AuditLogService audit;

    public AttendanceLocationConfigService(PermissionEvaluatorService permissions, InstitutionRepository institutions,
            InstitutionAttendanceLocationRepository locations, RoleDefinitionRepository roleDefinitions,
            AuditLogService audit) {
        this.permissions = permissions;
        this.institutions = institutions;
        this.locations = locations;
        this.roleDefinitions = roleDefinitions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public LocationConfigResponse get(UUID tenantId, UUID actorId, UUID institutionId) {
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        permissions.authorize(actor, "STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE",
                permissions.targetScope(actor, institutionId, null), RbacTarget.NONE);
        InstitutionAttendanceLocation row = locations.findByTenantIdAndInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance location not configured"));
        return toResponse(row);
    }

    @Transactional
    public LocationConfigResponse upsert(UUID tenantId, UUID actorId, UUID institutionId, LocationConfigRequest request) {
        if (request == null || request.latitude() == null || request.longitude() == null
                || request.radiusMeters() == null || request.radiusMeters() <= 0
                || request.gpsAccuracyThresholdMeters() == null || request.gpsAccuracyThresholdMeters() <= 0
                || request.retentionDays() == null || request.retentionDays() <= 0) {
            throw new BusinessRuleViolationException("INVALID_LOCATION_CONFIG",
                    "Latitude, longitude, a positive radius, accuracy threshold and retention are required.");
        }
        if (request.latitude().abs().compareTo(java.math.BigDecimal.valueOf(90)) > 0
                || request.longitude().abs().compareTo(java.math.BigDecimal.valueOf(180)) > 0) {
            throw new BusinessRuleViolationException("INVALID_LOCATION_CONFIG",
                    "Latitude must be within -90..90 and longitude within -180..180.");
        }
        if (request.radiusMeters() > MAX_RADIUS_METERS || request.gpsAccuracyThresholdMeters() > MAX_ACCURACY_METERS) {
            throw new BusinessRuleViolationException("INVALID_LOCATION_CONFIG",
                    "The geofence radius may be at most " + MAX_RADIUS_METERS + " m and the accuracy threshold at most "
                            + MAX_ACCURACY_METERS + " m.");
        }
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? StaffCheckinExpiryScheduler.DEFAULT_TIMEZONE : request.timezone().trim();
        try {
            java.time.ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new BusinessRuleViolationException("INVALID_LOCATION_CONFIG", "Unknown timezone: use an IANA zone such as Asia/Kolkata.");
        }
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        permissions.authorize(actor, "STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE",
                permissions.targetScope(actor, institutionId, null), RbacTarget.NONE);
        String approverRole = request.approverForPrincipalRole() == null || request.approverForPrincipalRole().isBlank()
                ? null : request.approverForPrincipalRole().trim().toUpperCase(java.util.Locale.ROOT);
        if (approverRole != null) {
            // A higher authority than the Principal, and a role that actually exists in this tenant.
            if (StaffCheckinApproverResolver.isPrincipalRole(approverRole)
                    || roleDefinitions.findByIdTenantIdAndRoleCode(tenantId, approverRole).isEmpty()) {
                throw new BusinessRuleViolationException("INVALID_APPROVER_ROLE",
                        "The Principal's check-in approver must be an existing role above the Principal.");
            }
        }
        if (institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId).isEmpty()) {
            throw new ResourceNotFoundException("Institution not found");
        }
        InstitutionAttendanceLocation existing = locations.findByTenantIdAndInstitutionId(tenantId, institutionId).orElse(null);
        InstitutionAttendanceLocation.InstitutionAttendanceLocationBuilder builder;
        if (existing == null) {
            builder = InstitutionAttendanceLocation.builder()
                    .tenantId(tenantId).institutionAttendanceLocationId(UUID.randomUUID())
                    .institutionId(institutionId).createdBy(actorId).rowVersion(1);
        } else {
            if (request.expectedRowVersion() == null || !request.expectedRowVersion().equals(existing.getRowVersion())) {
                throw new BusinessRuleViolationException("VERSION_CONFLICT", "The location configuration has changed since it was loaded.");
            }
            builder = existing.toBuilder().rowVersion(existing.getRowVersion() + 1);
        }
        InstitutionAttendanceLocation saved = locations.save(builder
                .latitude(request.latitude()).longitude(request.longitude())
                .radiusMeters(request.radiusMeters()).gpsAccuracyThresholdMeters(request.gpsAccuracyThresholdMeters())
                .timezone(timezone)
                .retentionDays(request.retentionDays())
                .approverForPrincipalRole(approverRole)
                .updatedBy(actorId)
                .build());
        audit.record(new AuditEntry(AuditEventType.STAFF_ATTENDANCE_LOCATION_CONFIGURED,
                "STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE", "INSTITUTION_ATTENDANCE_LOCATION",
                saved.getInstitutionAttendanceLocationId(), institutionId, null,
                java.util.Map.of("radiusMeters", saved.getRadiusMeters(), "gpsAccuracyThresholdMeters",
                        saved.getGpsAccuracyThresholdMeters()), 1, null, "SUCCESS"));
        return toResponse(saved);
    }

    private LocationConfigResponse toResponse(InstitutionAttendanceLocation row) {
        return new LocationConfigResponse(row.getInstitutionAttendanceLocationId(), row.getInstitutionId(),
                row.getLatitude(), row.getLongitude(), row.getRadiusMeters(), row.getGpsAccuracyThresholdMeters(),
                row.getTimezone(), row.getRetentionDays(), row.getApproverForPrincipalRole(), row.getRowVersion());
    }
}
