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
    public List<GradingSchemeResponse> listGradingSchemes(UUID tenantId, UUID institutionId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, null),
                "GRADING_SCHEME_SET",
                institutionId != null ? RbacScope.institution(institutionId) : RbacScope.tenant(),
                RbacTarget.NONE
        );
        return repository.findByIdTenantId(tenantId).stream().map(GradingSchemeResponse::from).toList();
    }

    @Transactional
    public GradingSchemeResponse createGradingScheme(UUID tenantId, TenantPrincipal principal, CreateGradingSchemeRequest request) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "GRADING_SCHEME_SET",
                request.institutionId() != null ? RbacScope.institution(request.institutionId()) : RbacScope.tenant(),
                RbacTarget.NONE
        );

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
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "GRADING_SCHEME_SET",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        GradingSchemeVersion scheme = repository.findByIdTenantIdAndIdGradingSchemeVersionId(tenantId, schemeId)
                .orElseThrow(() -> new BusinessRuleViolationException("SCHEME_NOT_FOUND", "Grading scheme version not found"));

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
}
