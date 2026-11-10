package com.edu360.api.academic;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GradingSchemeService {

    private final GradingSchemeVersionRepository repository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<GradingSchemeResponse> listGradingSchemes(UUID tenantId, TenantPrincipal principal, UUID institutionId) {
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());
        RbacScope scope = scopeFor(rbac, institutionId);
        permissionEvaluatorService.authorize(rbac, "GRADING_SCHEME_SET", scope, RbacTarget.NONE);
        // An institution-level caller sees its own institution's schemes and the university-wide ones.
        return repository.findByIdTenantId(tenantId).stream()
                .filter(s -> scope.scopeType() == com.edu360.api.rbac.ScopeType.TENANT
                        || s.getInstitutionId() == null
                        || s.getInstitutionId().equals(scope.institutionId()))
                .map(GradingSchemeResponse::from)
                .toList();
    }

    @Transactional
    public GradingSchemeResponse createGradingScheme(UUID tenantId, TenantPrincipal principal, CreateGradingSchemeRequest request) {
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());
        permissionEvaluatorService.authorize(rbac, "GRADING_SCHEME_SET", scopeFor(rbac, request.institutionId()), RbacTarget.NONE);

        UUID schemeId = UUID.randomUUID();
        GradingSchemeVersion scheme = GradingSchemeVersion.builder()
                .id(new GradingSchemeVersionId(tenantId, schemeId))
                .institutionId(request.institutionId())
                .schemeName(request.schemeName())
                .versionNo(1)
                .status("DRAFT")
                .effectiveFrom(request.effectiveFrom())
                .createdAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedAt(Instant.now())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        scheme = repository.save(scheme);

        auditLogService.record(
                AuditEventType.PLAN_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Grading scheme " + schemeId + " created: " + request.schemeName()
        );

        return GradingSchemeResponse.from(scheme);
    }

    @Transactional
    public GradingSchemeResponse activateGradingScheme(UUID tenantId, TenantPrincipal principal, UUID schemeId) {
        GradingSchemeVersion scheme = repository.findByIdTenantIdAndIdGradingSchemeVersionId(tenantId, schemeId)
                .orElseThrow(() -> new BusinessRuleViolationException("SCHEME_NOT_FOUND", "Grading scheme version not found"));

        // A-05: activation is checked at the scheme's own institution (College/School Admin) or the
        // university (Registrar), never as a blanket tenant-wide check.
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());
        permissionEvaluatorService.authorize(rbac, "GRADING_SCHEME_SET", scopeFor(rbac, scheme.getInstitutionId()), RbacTarget.NONE);

        scheme.setStatus("ACTIVE");
        scheme.setUpdatedAt(Instant.now());
        scheme.setUpdatedBy(principal.userAccountId());
        scheme = repository.save(scheme);

        auditLogService.record(
                AuditEventType.PLAN_UPDATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Grading scheme " + schemeId + " activated"
        );

        return GradingSchemeResponse.from(scheme);
    }

    /** A-05 scope for a target record; the institution scope only if the evaluator gives none. */
    private RbacScope scopeFor(RbacPrincipal rbac, UUID institutionId) {
        RbacScope fallback = institutionId != null ? RbacScope.institution(institutionId) : RbacScope.tenant();
        return Objects.requireNonNullElse(permissionEvaluatorService.targetScope(rbac, institutionId, null), fallback);
    }
}
