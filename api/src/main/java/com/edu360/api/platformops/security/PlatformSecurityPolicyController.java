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

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final AuditLogService auditLogService;
    private final SecurityIncidentRepository incidentRepository;
    private final PlatformSettingValueRepository settingValueRepository;
    private final PlatformAuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    private static final String SETTING_KEY_SECURITY_POLICY = "security.policy";

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformSecurityPolicyController(
            PlatformPermissionService permissionService,
            AuditLogService auditLogService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SecurityIncidentRepository incidentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PlatformSettingValueRepository settingValueRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PlatformAuditWriter auditWriter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
        this.incidentRepository = incidentRepository;
        this.settingValueRepository = settingValueRepository;
        this.auditWriter = auditWriter;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    public PlatformSecurityPolicyController(
            PlatformPermissionService permissionService,
            AuditLogService auditLogService
    ) {
        this(permissionService, auditLogService, null, null, null, null);
    }

    public record SecurityPolicyResponse(
            int minPasswordLength,
            boolean requireSpecialChar,
            boolean requireDigit,
            int maxFailedLogins,
            int lockoutMinutes,
            int idleSessionTimeoutMinutes,
            boolean mfaRequiredForPrivileged,
            int defaultBackupRetentionDays
    ) {}

    public record UpdateSecurityPolicyRequest(
            int minPasswordLength,
            boolean requireSpecialChar,
            boolean requireDigit,
            int maxFailedLogins,
            int lockoutMinutes,
            int idleSessionTimeoutMinutes,
            boolean mfaRequiredForPrivileged,
            int defaultBackupRetentionDays
    ) {}

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

    private static final SecurityPolicyResponse DEFAULT_POLICY = new SecurityPolicyResponse(
            12, true, true, 5, 15, 30, true, 90
    );

    @GetMapping("/security-policy")
    @Operation(summary = "Get current platform security policy (SECURITY_POLICY_SET)")
    public ResponseEntity<SecurityPolicyResponse> getPolicy(Authentication auth) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "SECURITY_POLICY_SET");

        if (settingValueRepository != null) {
            var existing = settingValueRepository.findBySettingKeyAndStatus(SETTING_KEY_SECURITY_POLICY, "ACTIVE");
            if (existing.isPresent()) {
                try {
                    return ResponseEntity.ok(objectMapper.readValue(existing.get().getValue(), SecurityPolicyResponse.class));
                } catch (JsonProcessingException ex) {
                    log.warn("Failed to parse security policy from DB: {}", ex.getMessage());
                }
            }
        }

        return ResponseEntity.ok(DEFAULT_POLICY);
    }

    @PutMapping("/security-policy")
    @Operation(summary = "Update platform security policy (SECURITY_POLICY_SET)")
    public ResponseEntity<SecurityPolicyResponse> updatePolicy(
            @RequestBody UpdateSecurityPolicyRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "SECURITY_POLICY_SET");

        SecurityPolicyResponse newPolicy = new SecurityPolicyResponse(
                req.minPasswordLength(),
                req.requireSpecialChar(),
                req.requireDigit(),
                req.maxFailedLogins(),
                req.lockoutMinutes(),
                req.idleSessionTimeoutMinutes(),
                req.mfaRequiredForPrivileged(),
                req.defaultBackupRetentionDays()
        );

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

            try {
                String jsonVal = objectMapper.writeValueAsString(newPolicy);
                Instant now = Instant.now();
                PlatformSettingValue newVal = PlatformSettingValue.builder()
                        .settingValueId(UUID.randomUUID())
                        .settingKey(SETTING_KEY_SECURITY_POLICY)
                        .value(jsonVal)
                        .versionNo(nextVersion)
                        .status("ACTIVE")
                        .changeReason("Updated by Product Owner " + principal.email())
                        .createdAt(now)
                        .createdBy(principal.platformUserId())
                        .updatedAt(now)
                        .updatedBy(principal.platformUserId())
                        .build();
                settingValueRepository.save(newVal);
            } catch (JsonProcessingException ex) {
                log.error("Could not serialize security policy: {}", ex.getMessage());
            }
        }

        if (auditWriter != null) {
            auditWriter.record(principal, "CONFIG_CHANGE", "SECURITY_POLICY_SET", "SETTING_VALUE", UUID.randomUUID());
        }

        return ResponseEntity.ok(newPolicy);
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
        if (auditWriter != null) {
            auditWriter.record(principal, "SECURITY_INCIDENT_REPORTED", "PLATFORM_AUDIT_VIEW", "SECURITY_INCIDENT", saved.getSecurityIncidentId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Security incident reported: " + req.title());

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
        if (auditWriter != null) {
            auditWriter.record(principal, "SECURITY_INCIDENT_UPDATED", "PLATFORM_AUDIT_VIEW", "SECURITY_INCIDENT", saved.getSecurityIncidentId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Security incident " + id + " updated to " + inc.getStatus());

        return ResponseEntity.ok(SecurityIncidentResponse.from(saved));
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
