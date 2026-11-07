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
public class ProgramRuleService {

    private final ProgramRuleSetRepository repository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProgramRuleSetResponse> listRuleSets(UUID tenantId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, null),
                "PROGRAM_RULES_SET",
                RbacScope.tenant(),
                RbacTarget.NONE
        );
        return repository.findByIdTenantId(tenantId).stream().map(ProgramRuleSetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProgramRuleSetResponse getRuleSet(UUID tenantId, UUID ruleSetId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, null),
                "PROGRAM_RULES_SET",
                RbacScope.tenant(),
                RbacTarget.NONE
        );
        return repository.findByIdTenantIdAndIdProgramRuleSetId(tenantId, ruleSetId)
                .map(ProgramRuleSetResponse::from)
                .orElseThrow(() -> new BusinessRuleViolationException("RULE_SET_NOT_FOUND", "Program rule set not found"));
    }

    @Transactional
    public ProgramRuleSetResponse createRuleSet(UUID tenantId, TenantPrincipal principal, CreateProgramRuleSetRequest request) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROGRAM_RULES_SET",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        UUID ruleSetId = UUID.randomUUID();
        ProgramRuleSet ruleSet = ProgramRuleSet.builder()
                .id(new ProgramRuleSetId(tenantId, ruleSetId))
                .institutionId(request.institutionId())
                .programId(request.programId())
                .versionNo(1)
                .rules(request.rules())
                .effectiveFrom(request.effectiveFrom())
                .status("DRAFT")
                .createdAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedAt(Instant.now())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        ruleSet = repository.save(ruleSet);

        auditLogService.record(
                AuditEventType.PROGRAM_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Program rule set " + ruleSetId + " created in draft"
        );

        return ProgramRuleSetResponse.from(ruleSet);
    }

    @Transactional
    public ProgramRuleSetResponse activateRuleSet(UUID tenantId, TenantPrincipal principal, UUID ruleSetId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROGRAM_RULES_SET",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        ProgramRuleSet ruleSet = repository.findByIdTenantIdAndIdProgramRuleSetId(tenantId, ruleSetId)
                .orElseThrow(() -> new BusinessRuleViolationException("RULE_SET_NOT_FOUND", "Program rule set not found"));

        ruleSet.setStatus("ACTIVE");
        ruleSet.setUpdatedAt(Instant.now());
        ruleSet.setUpdatedBy(principal.userAccountId());
        ruleSet = repository.save(ruleSet);

        auditLogService.record(
                AuditEventType.PROGRAM_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Program rule set " + ruleSetId + " activated"
        );

        return ProgramRuleSetResponse.from(ruleSet);
    }
}
