package com.edu360.api.platformops.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@Tag(name = "Security Policy & Incidents", description = "Platform security configuration and security incident records")
public class PlatformSecurityPolicyController {

    private final PlatformPermissionService permissionService;
    private final SecurityIncidentRepository incidentRepository;
    private final PlatformSettingValueRepository settingValueRepository;
    private final PlatformAuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final SecurityPolicyService securityPolicyService;
    private final RealmSecuritySyncService realmSecuritySyncService;

    private static final String SETTING_KEY_SECURITY_POLICY = SecurityPolicyService.SETTING_KEY_SECURITY_POLICY;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformSecurityPolicyController(
            PlatformPermissionService permissionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SecurityIncidentRepository incidentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PlatformSettingValueRepository settingValueRepository,
            PlatformAuditWriter auditWriter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SecurityPolicyService securityPolicyService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RealmSecuritySyncService realmSecuritySyncService
    ) {
        this.permissionService = permissionService;
        this.incidentRepository = incidentRepository;
        this.settingValueRepository = settingValueRepository;
        this.auditWriter = auditWriter;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.securityPolicyService = securityPolicyService != null
                ? securityPolicyService : new SecurityPolicyService(settingValueRepository);
        this.realmSecuritySyncService = realmSecuritySyncService;
    }

    public PlatformSecurityPolicyController(
            PlatformPermissionService permissionService,
            SecurityIncidentRepository incidentRepository,
            PlatformSettingValueRepository settingValueRepository,
            PlatformAuditWriter auditWriter,
            ObjectMapper objectMapper
    ) {
        this(permissionService, incidentRepository, settingValueRepository, auditWriter, objectMapper, null, null);
    }

    public record SecurityPolicyResponse(
            int minPasswordLength,
            boolean requireSpecialChar,
            boolean requireDigit,
            int maxFailedLogins,
            int lockoutMinutes,
            int idleSessionTimeoutMinutes,
            boolean mfaRequiredForPrivileged,
            int defaultBackupRetentionDays,
            int passwordRotationDays,
            List<RealmSecuritySyncService.RealmSyncResult> realmSync
    ) {
        static SecurityPolicyResponse of(SecurityPolicyService.SecurityPolicy p,
                                         List<RealmSecuritySyncService.RealmSyncResult> sync) {
            return new SecurityPolicyResponse(p.minPasswordLength(), p.requireSpecialChar(), p.requireDigit(),
                    p.maxFailedLogins(), p.lockoutMinutes(), p.idleSessionTimeoutMinutes(), p.mfaRequiredForPrivileged(),
                    p.defaultBackupRetentionDays(), p.rotationDays(), sync);
        }
    }

    public record UpdateSecurityPolicyRequest(
            int minPasswordLength,
            boolean requireSpecialChar,
            boolean requireDigit,
            int maxFailedLogins,
            int lockoutMinutes,
            int idleSessionTimeoutMinutes,
            boolean mfaRequiredForPrivileged,
            int defaultBackupRetentionDays,
            Integer passwordRotationDays
    ) {
        public UpdateSecurityPolicyRequest(int minPasswordLength, boolean requireSpecialChar, boolean requireDigit,
                                           int maxFailedLogins, int lockoutMinutes, int idleSessionTimeoutMinutes,
                                           boolean mfaRequiredForPrivileged, int defaultBackupRetentionDays) {
            this(minPasswordLength, requireSpecialChar, requireDigit, maxFailedLogins, lockoutMinutes,
                    idleSessionTimeoutMinutes, mfaRequiredForPrivileged, defaultBackupRetentionDays, null);
        }
    }

    public record SecurityIncidentResponse(
            UUID securityIncidentId,
            UUID tenantId,
            String title,
            Instant detectedAt,
            String severity,
            UUID ownerUserId,
            String status,
            boolean externalNotificationRequired,
            Instant externalNotifiedAt,
            String closureNote,
            Instant createdAt
    ) {
        public static SecurityIncidentResponse from(SecurityIncident s) {
            return new SecurityIncidentResponse(
                    s.getSecurityIncidentId(),
                    s.getTenantId(),
                    s.getTitle(),
                    s.getDetectedAt(),
                    s.getSeverity(),
                    s.getOwnerUserId(),
                    s.getStatus(),
                    s.isExternalNotificationRequired(),
                    s.getExternalNotifiedAt(),
                    s.getClosureNote(),
                    s.getCreatedAt()
            );
        }
    }

    public record CreateSecurityIncidentRequest(
            UUID tenantId,
            @NotBlank(message = "Title is required") String title,
            String severity,
            boolean externalNotificationRequired
    ) {}

    public record UpdateSecurityIncidentRequest(
            String status,
            String closureNote
    ) {}

    @GetMapping("/security-policy")
    @Operation(summary = "Get current platform security policy (SECURITY_POLICY_SET)")
    public ResponseEntity<SecurityPolicyResponse> getPolicy(Authentication auth) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "SECURITY_POLICY_SET");
        return ResponseEntity.ok(SecurityPolicyResponse.of(securityPolicyService.current(), List.of()));
    }

    @PutMapping("/security-policy")
    @Operation(summary = "Update platform security policy (SECURITY_POLICY_SET); pushed to every Keycloak realm (I-06)")
    public ResponseEntity<SecurityPolicyResponse> updatePolicy(
            @RequestBody UpdateSecurityPolicyRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "SECURITY_POLICY_SET");

        if (req == null || req.minPasswordLength() < 8) {
            throw new BusinessRuleViolationException("INVALID_MIN_PASSWORD_LENGTH", "Minimum password length must be at least 8.");
        }
        if (req.minPasswordLength() > 64) {
            throw new BusinessRuleViolationException("INVALID_MIN_PASSWORD_LENGTH", "Minimum password length must be at most 64.");
        }
        if (req.maxFailedLogins() <= 0) {
            throw new BusinessRuleViolationException("INVALID_MAX_FAILED_LOGINS", "Max failed logins must be at least 1.");
        }
        if (req.lockoutMinutes() <= 0) {
            throw new BusinessRuleViolationException("INVALID_LOCKOUT_MINUTES", "Lockout duration must be at least 1 minute.");
        }
        if (req.idleSessionTimeoutMinutes() <= 0) {
            throw new BusinessRuleViolationException("INVALID_IDLE_TIMEOUT", "Idle session timeout must be at least 1 minute.");
        }
        if (!req.mfaRequiredForPrivileged()) {
            // D1 Part II §11.0 / NF-09 and PD-8: two-step login is mandatory for the listed
            // roles; the policy cannot switch it off.
            throw new BusinessRuleViolationException("MFA_MANDATORY",
                    "Two-step login is mandatory for platform roles, Admins, Principals, Accountants, HR and Payroll (D1 NF-09, PD-8).");
        }
        if (req.passwordRotationDays() != null && (req.passwordRotationDays() < 1 || req.passwordRotationDays() > 365)) {
            throw new BusinessRuleViolationException("INVALID_ROTATION_DAYS", "Password rotation must be between 1 and 365 days.");
        }

        SecurityPolicyService.SecurityPolicy newPolicy = new SecurityPolicyService.SecurityPolicy(
                req.minPasswordLength(),
                req.requireSpecialChar(),
                req.requireDigit(),
                req.maxFailedLogins(),
                req.lockoutMinutes(),
                req.idleSessionTimeoutMinutes(),
                req.mfaRequiredForPrivileged(),
                req.defaultBackupRetentionDays(),
                req.passwordRotationDays() != null
                        ? req.passwordRotationDays() : SecurityPolicyService.D1_DEFAULT.passwordRotationDays());

        if (settingValueRepository != null) {
            int nextVersion = 1;
            var oldOpt = settingValueRepository.findBySettingKeyAndStatus(SETTING_KEY_SECURITY_POLICY, "ACTIVE");
            if (oldOpt.isPresent()) {
                PlatformSettingValue oldVal = oldOpt.get();
                oldVal.setStatus("SUPERSEDED");
                oldVal.setUpdatedAt(Instant.now());
                oldVal.setUpdatedBy(principal.platformUserId());
                settingValueRepository.save(oldVal);
                nextVersion = oldVal.getVersionNo() + 1;
            }

            Instant now = Instant.now();
            PlatformSettingValue newVal = PlatformSettingValue.builder()
                    .settingValueId(UUID.randomUUID())
                    .settingKey(SETTING_KEY_SECURITY_POLICY)
                    .value(securityPolicyService.toJson(newPolicy))
                    .versionNo(nextVersion)
                    .status("ACTIVE")
                    .changeReason("Updated by Product Owner " + principal.email())
                    .createdAt(now)
                    .createdBy(principal.platformUserId())
                    .updatedAt(now)
                    .updatedBy(principal.platformUserId())
                    .build();
            settingValueRepository.save(newVal);
        }

        auditWriter.record(principal, "CONFIG_CHANGE", "SECURITY_POLICY_SET", "SETTING_VALUE", UUID.randomUUID());

        // I-06: push the saved policy into every Keycloak realm now.
        List<RealmSecuritySyncService.RealmSyncResult> sync = realmSecuritySyncService != null
                ? realmSecuritySyncService.applyToAllRealms(newPolicy)
                : List.of();
        return ResponseEntity.ok(SecurityPolicyResponse.of(newPolicy, sync));
    }

    /** I-06: re-push the current policy and the sign-in baseline to every realm (e.g. after a deployment). */
    @PostMapping("/security-policy/realm-sync")
    @Operation(summary = "Re-apply the security policy to every Keycloak realm (SECURITY_POLICY_SET)")
    public ResponseEntity<List<RealmSecuritySyncService.RealmSyncResult>> syncRealms(Authentication auth) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "SECURITY_POLICY_SET");
        if (realmSecuritySyncService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        List<RealmSecuritySyncService.RealmSyncResult> results = realmSecuritySyncService.applyToAllRealms();
        auditWriter.record(principal, "CONFIG_CHANGE", "SECURITY_POLICY_SET", "SETTING_VALUE", UUID.randomUUID());
        return ResponseEntity.ok(results);
    }

    @GetMapping({"/incidents", "/security-incidents"})
    @Operation(summary = "List security incidents (PLATFORM_AUDIT_VIEW / OD-137)")
    public ResponseEntity<List<SecurityIncidentResponse>> listIncidents(Authentication auth) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorizeAny(principal, List.of("PLATFORM_AUDIT_VIEW", "SECURITY_POLICY_SET"));

        if (incidentRepository == null) {
            return ResponseEntity.ok(List.of());
        }
        List<SecurityIncident> list = incidentRepository.findAllByOrderByDetectedAtDesc();
        return ResponseEntity.ok(list.stream().map(SecurityIncidentResponse::from).toList());
    }

    @PostMapping({"/incidents", "/security-incidents"})
    @Operation(summary = "Report security incident (OD-137)")
    public ResponseEntity<SecurityIncidentResponse> createIncident(
            @Valid @RequestBody CreateSecurityIncidentRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorizeAny(principal, List.of("PLATFORM_AUDIT_VIEW", "SECURITY_POLICY_SET"));

        Instant now = Instant.now();
        SecurityIncident inc = SecurityIncident.builder()
                .securityIncidentId(UUID.randomUUID())
                .tenantId(req.tenantId())
                .title(req.title())
                .detectedAt(now)
                .severity(req.severity() != null ? req.severity() : "MEDIUM")
                .ownerUserId(principal.platformUserId())
                .status("DETECTED")
                .externalNotificationRequired(req.externalNotificationRequired())
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        SecurityIncident saved = incidentRepository.save(inc);
        auditWriter.record(principal, "SECURITY_INCIDENT_REPORTED", "PLATFORM_AUDIT_VIEW", "SECURITY_INCIDENT", saved.getSecurityIncidentId());

        return ResponseEntity.status(HttpStatus.CREATED).body(SecurityIncidentResponse.from(saved));
    }

    @PatchMapping({"/incidents/{id}", "/security-incidents/{id}"})
    @Operation(summary = "Update security incident status or closure note")
    public ResponseEntity<SecurityIncidentResponse> updateIncident(
            @PathVariable UUID id,
            @RequestBody UpdateSecurityIncidentRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorizeAny(principal, List.of("PLATFORM_AUDIT_VIEW", "SECURITY_POLICY_SET"));

        SecurityIncident inc = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Security incident not found: " + id));

        if (req.status() != null) {
            inc.setStatus(req.status());
        }
        if (req.closureNote() != null) {
            inc.setClosureNote(req.closureNote());
        }
        inc.setUpdatedAt(Instant.now());
        inc.setUpdatedBy(principal.platformUserId());

        SecurityIncident saved = incidentRepository.save(inc);
        auditWriter.record(principal, "SECURITY_INCIDENT_UPDATED", "PLATFORM_AUDIT_VIEW", "SECURITY_INCIDENT", saved.getSecurityIncidentId());

        return ResponseEntity.ok(SecurityIncidentResponse.from(saved));
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
