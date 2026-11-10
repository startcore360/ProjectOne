package com.edu360.api.rolldown;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolldownService {

    private final RoleRolldownOverrideRepository overrideRepository;
    private final RoleRolldownDefaultRepository defaultRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    private static final Set<String> UNROLLABLE_ROLES = Set.of(
            "APPLICANT", "BILLING_MANAGER", "DATA_PROTECTION_OFFICER", "DRIVER", "GOVERNING_BODY_APPROVER",
            "HOSTEL_STAFF", "HOUSEKEEPING_SUPERVISOR", "PARENT_COLLEGE", "PARENT_SCHOOL", "PLATFORM_ADMIN",
            "PRODUCT_OWNER", "RELEASE_MANAGER", "SECURITY_STAFF", "SECURITY_SUPERVISOR", "STUDENT_COLLEGE",
            "STUDENT_SCHOOL", "SUBJECT_TEACHER", "SUPPORT_MANAGER", "TRANSPORT_STAFF"
    );

    private static final Set<String> PROHIBITED_TARGET_ROLES = Set.of(
            "APPLICANT", "BILLING_MANAGER", "DATA_PROTECTION_OFFICER", "GOVERNING_BODY_APPROVER",
            "PARENT_COLLEGE", "PARENT_SCHOOL", "PLATFORM_ADMIN", "PRODUCT_OWNER", "RELEASE_MANAGER",
            "STUDENT_COLLEGE", "STUDENT_SCHOOL", "SUPPORT_MANAGER"
    );

    @Transactional(readOnly = true)
    public List<RolldownMapEntryDto> getRolldownMap(UUID tenantId, UUID institutionId, String context, TenantPrincipal principal) {
        UUID actorUserId = principal != null ? principal.userAccountId() : null;
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, actorUserId),
                "ROLLDOWN_VIEW",
                institutionId != null ? RbacScope.institution(institutionId) : RbacScope.tenant(),
                RbacTarget.NONE
        );

        String effectiveContext = context != null ? context : (institutionId != null ? "COLLEGE" : "UNIVERSITY_WIDE");
        List<RoleRolldownDefault> defaults = defaultRepository.findByContextOrderByMissingRoleCodeAscPreferenceAsc(effectiveContext);

        List<RoleRolldownOverride> overrides = institutionId != null
                ? overrideRepository.findByIdTenantIdAndInstitutionIdAndStatus(tenantId, institutionId, "ACTIVE")
                : overrideRepository.findByIdTenantIdAndStatus(tenantId, "ACTIVE");

        Map<String, RoleRolldownOverride> overrideMap = overrides.stream()
                .collect(Collectors.toMap(
                        o -> o.getMissingRoleCode() + ":" + o.getPermissionKind(),
                        o -> o,
                        (existing, replacement) -> existing
                ));

        return defaults.stream().map(d -> {
            String key = d.getMissingRoleCode() + ":" + d.getPermissionKind();
            RoleRolldownOverride ov = overrideMap.get(key);
            boolean hasOv = ov != null;
            return new RolldownMapEntryDto(
                    d.getMissingRoleCode(),
                    d.getContext(),
                    d.getPermissionKind(),
                    d.getPreference(),
                    d.getStandInRoleCode(),
                    d.getNote(),
                    hasOv,
                    hasOv ? ov.getMode() : null,
                    hasOv ? ov.getTargetRoleCode() : null
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<RolldownOverrideDto> getOverrides(UUID tenantId, UUID institutionId, TenantPrincipal principal) {
        UUID actorUserId = principal != null ? principal.userAccountId() : null;
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, actorUserId),
                "ROLLDOWN_VIEW",
                institutionId != null ? RbacScope.institution(institutionId) : RbacScope.tenant(),
                RbacTarget.NONE
        );

        List<RoleRolldownOverride> list = institutionId != null
                ? overrideRepository.findByIdTenantIdAndInstitutionIdAndStatus(tenantId, institutionId, "ACTIVE")
                : overrideRepository.findByIdTenantIdAndStatus(tenantId, "ACTIVE");

        return list.stream().map(RolldownOverrideDto::from).toList();
    }

    @Transactional
    public RolldownOverrideDto createOverride(UUID tenantId, TenantPrincipal principal, String activeRole, CreateRolldownOverrideRequest request) {
        // Enforce server-side authorization
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "ROLLDOWN_OVERRIDE_SET",
                request.institutionId() != null ? RbacScope.institution(request.institutionId()) : RbacScope.tenant(),
                RbacTarget.NONE
        );

        String contextActiveRole = TenantContext.currentActiveRoleCode();
        if (activeRole != null && contextActiveRole != null && !activeRole.equalsIgnoreCase(contextActiveRole)) {
            throw new UnauthorizedException("Spoofed role header: X-Active-Role does not match authenticated active role.");
        }
        if (contextActiveRole == null) {
            throw new UnauthorizedException("No active role: choose an active role before setting a roll-down override.");
        }
        String setterRole = contextActiveRole;
        Set<String> allowedSetters = Set.of("MANAGEMENT", "VICE_CHANCELLOR", "UNIVERSITY_ADMIN", "GOVERNING_BODY_APPROVER");
        if (!allowedSetters.contains(setterRole.toUpperCase())) {
            throw new UnauthorizedException("Role " + setterRole + " is not permitted to set rolldown overrides.");
        }

        // Guard RD-G4: Missing role check
        if (UNROLLABLE_ROLES.contains(request.missingRoleCode())) {
            throw new BusinessRuleViolationException("ROLLDOWN_ROLE_FIXED",
                    "Role " + request.missingRoleCode() + " never rolls down [RD-G4, RD-G5]");
        }

        // Guard RD-G2: An approval action can only be given to another role, never withheld
        if ("WITHHOLD".equalsIgnoreCase(request.mode()) && "APPROVE".equalsIgnoreCase(request.permissionKind())) {
            throw new BusinessRuleViolationException("ROLLDOWN_WITHHOLD_APPROVAL",
                    "An approval can only be given to another role, never withheld (requests would wait forever)");
        }

        // Guard RD-G5: Prohibited target roles
        if ("ASSIGN".equalsIgnoreCase(request.mode())) {
            if (request.targetRoleCode() == null || request.targetRoleCode().isBlank()) {
                throw new BusinessRuleViolationException("TARGET_ROLE_REQUIRED",
                        "Target role code is required when mode is ASSIGN");
            }
            if (request.targetRoleCode().equalsIgnoreCase(request.missingRoleCode())
                    || PROHIBITED_TARGET_ROLES.contains(request.targetRoleCode())) {
                throw new BusinessRuleViolationException("ROLLDOWN_TARGET_NOT_ALLOWED",
                        "Role " + request.targetRoleCode() + " cannot act for another role [RD-G5]");
            }
        }

        // University Admin guard: non-approval actions only
        if ("UNIVERSITY_ADMIN".equalsIgnoreCase(setterRole) && "APPROVE".equalsIgnoreCase(request.permissionKind())) {
            throw new BusinessRuleViolationException("ROLLDOWN_SETTER_NOT_ALLOWED",
                    "The University Admin may change non-approval roll-down only");
        }

        UUID overrideId = UUID.randomUUID();
        RoleRolldownOverride override = RoleRolldownOverride.builder()
                .id(new RoleRolldownOverrideId(tenantId, overrideId))
                .institutionId(request.institutionId())
                .missingRoleCode(request.missingRoleCode())
                .permissionKind(request.permissionKind())
                .actionCode(request.actionCode())
                .mode(request.mode().toUpperCase())
                .targetRoleCode(request.targetRoleCode())
                .reason(request.reason())
                .setByUserId(principal.userAccountId())
                .setByRole(setterRole)
                .status("ACTIVE")
                .effectiveFrom(Instant.now())
                .createdAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedAt(Instant.now())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        override = overrideRepository.save(override);

        auditLogService.record(
                AuditEventType.ROLE_ASSIGNED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Rolldown override " + overrideId + " set for missing role " + request.missingRoleCode() + " to mode " + request.mode()
        );

        log.info("Rolldown override {} set by {} ({}) for missing role {} in tenant {}",
                overrideId, principal.userAccountId(), setterRole, request.missingRoleCode(), tenantId);

        return RolldownOverrideDto.from(override);
    }

    @Transactional
    public RolldownOverrideDto revokeOverride(UUID tenantId, TenantPrincipal principal, UUID overrideId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "ROLLDOWN_OVERRIDE_SET",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        RoleRolldownOverride override = overrideRepository.findByIdTenantIdAndIdRoleRolldownOverrideId(tenantId, overrideId)
                .orElseThrow(() -> new BusinessRuleViolationException("OVERRIDE_NOT_FOUND",
                        "Rolldown override " + overrideId + " not found"));

        override.setStatus("REVOKED");
        override.setUpdatedAt(Instant.now());
        override.setUpdatedBy(principal.userAccountId());
        override = overrideRepository.save(override);

        auditLogService.record(
                AuditEventType.ROLE_ASSIGNMENT_ENDED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Rolldown override " + overrideId + " revoked"
        );

        return RolldownOverrideDto.from(override);
    }
}
