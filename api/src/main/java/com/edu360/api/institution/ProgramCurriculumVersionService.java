package com.edu360.api.institution;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.dto.CreateCurriculumVersionRequest;
import com.edu360.api.institution.dto.CurriculumVersionResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Service
public class ProgramCurriculumVersionService {
    private final ProgramCurriculumVersionRepository versionRepository;
    private final ProgramRepository programRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    public ProgramCurriculumVersionService(
            ProgramCurriculumVersionRepository versionRepository,
            ProgramRepository programRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService) {
        this.versionRepository = versionRepository;
        this.programRepository = programRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CurriculumVersionResponse create(RbacPrincipal principal, UUID institutionId, UUID programId,
            CreateCurriculumVersionRequest request) {
        authorize(principal, institutionId);
        if (request.curriculumJson() == null || !request.curriculumJson().isObject()) {
            throw new BusinessRuleViolationException("INVALID_CURRICULUM_JSON", "curriculumJson must be a JSON object.");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BusinessRuleViolationException("INVALID_CURRICULUM_DATES", "effectiveTo must be on or after effectiveFrom.");
        }

        Program program = programRepository.findForUpdate(principal.tenantId(), programId)
                .filter(p -> institutionId.equals(p.getInstitutionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Program " + programId + " was not found in this institution."));
        if (!"ACTIVE".equalsIgnoreCase(program.getStatus())) {
            throw new BusinessRuleViolationException("PROGRAM_NOT_ACTIVE", "Curriculum versions can only be added to active programs.");
        }

        int nextVersion = versionRepository.findMaxVersionNumber(principal.tenantId(), programId) + 1;
        ProgramCurriculumVersion version = ProgramCurriculumVersion.builder()
                .tenantId(principal.tenantId())
                .programCurriculumVersionId(UUID.randomUUID())
                .institutionId(institutionId)
                .programId(programId)
                .versionNumber(nextVersion)
                .curriculumJson(request.curriculumJson())
                .effectiveFrom(request.effectiveFrom())
                .effectiveTo(request.effectiveTo())
                .createdBy(principal.userAccountId())
                .build();
        ProgramCurriculumVersion saved = versionRepository.save(version);
        auditLogService.record(AuditEventType.PROGRAM_CURRICULUM_VERSION_CREATED, null,
                program.getProgramCode(), "0.0.0.0",
                "Created immutable curriculum version " + nextVersion + " for program " + programId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CurriculumVersionResponse> list(RbacPrincipal principal, UUID institutionId, UUID programId) {
        authorize(principal, institutionId);
        programRepository.findByTenantIdAndProgramId(principal.tenantId(), programId)
                .filter(p -> institutionId.equals(p.getInstitutionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Program " + programId + " was not found in this institution."));
        return versionRepository.findByTenantIdAndInstitutionIdAndProgramIdOrderByVersionNumberDesc(
                        principal.tenantId(), institutionId, programId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CurriculumVersionResponse get(RbacPrincipal principal, UUID institutionId, UUID programId, UUID versionId) {
        authorize(principal, institutionId);
        return versionRepository.findByTenantIdAndInstitutionIdAndProgramIdAndProgramCurriculumVersionId(
                        principal.tenantId(), institutionId, programId, versionId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum version " + versionId + " was not found."));
    }

    private void authorize(RbacPrincipal principal, UUID institutionId) {
        Objects.requireNonNull(principal, "principal is required");
        permissionEvaluatorService.authorize(principal, "STRUCTURE_MANAGE",
                Objects.requireNonNullElse(permissionEvaluatorService.targetScope(principal, institutionId, null),
                        RbacScope.institution(institutionId)), RbacTarget.NONE);
    }

    private CurriculumVersionResponse toResponse(ProgramCurriculumVersion v) {
        return new CurriculumVersionResponse(v.getProgramCurriculumVersionId(), v.getInstitutionId(), v.getProgramId(),
                v.getVersionNumber(), v.getCurriculumJson(), v.getEffectiveFrom(), v.getEffectiveTo(),
                v.getCreatedAt(), v.getCreatedBy());
    }
}
