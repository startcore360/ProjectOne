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
public class SubjectAllocationService {

    private final SubjectAllocationRepository repository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SubjectAllocationResponse> listAllocations(UUID tenantId, UUID institutionId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, null),
                "SUBJECT_ALLOCATE",
                institutionId != null ? RbacScope.institution(institutionId) : RbacScope.tenant(),
                RbacTarget.NONE
        );

        List<SubjectAllocation> list = institutionId != null
                ? repository.findByIdTenantIdAndInstitutionId(tenantId, institutionId)
                : repository.findByIdTenantId(tenantId);

        return list.stream().map(SubjectAllocationResponse::from).toList();
    }

    @Transactional
    public SubjectAllocationResponse allocateSubject(UUID tenantId, TenantPrincipal principal, CreateSubjectAllocationRequest request) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "SUBJECT_ALLOCATE",
                RbacScope.institution(request.institutionId()),
                RbacTarget.NONE
        );

        // Constraint ck_subject_allocation_r1: exactly one of section_id or subject_group_id
        boolean hasSection = request.sectionId() != null;
        boolean hasGroup = request.subjectGroupId() != null;
        if ((hasSection && hasGroup) || (!hasSection && !hasGroup)) {
            throw new BusinessRuleViolationException("INVALID_ALLOCATION_TARGET",
                    "Subject allocation must target either a section or an elective group, not both and not neither.");
        }

        UUID allocationId = UUID.randomUUID();
        SubjectAllocation allocation = SubjectAllocation.builder()
                .id(new SubjectAllocationId(tenantId, allocationId))
                .institutionId(request.institutionId())
                .courseOfferingId(request.courseOfferingId())
                .sectionId(request.sectionId())
                .subjectGroupId(request.subjectGroupId())
                .staffAssignmentId(request.staffAssignmentId())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedAt(Instant.now())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        allocation = repository.save(allocation);

        auditLogService.record(
                AuditEventType.ROLE_ASSIGNED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Subject allocated: allocation " + allocationId + " to teacher " + request.staffAssignmentId()
        );

        return SubjectAllocationResponse.from(allocation);
    }

    @Transactional
    public SubjectAllocationResponse endAllocation(UUID tenantId, TenantPrincipal principal, UUID allocationId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "SUBJECT_ALLOCATE",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        SubjectAllocation allocation = repository.findByIdTenantIdAndIdSubjectAllocationId(tenantId, allocationId)
                .orElseThrow(() -> new BusinessRuleViolationException("ALLOCATION_NOT_FOUND", "Subject allocation not found"));

        allocation.setStatus("ENDED");
        allocation.setUpdatedAt(Instant.now());
        allocation.setUpdatedBy(principal.userAccountId());
        allocation = repository.save(allocation);

        auditLogService.record(
                AuditEventType.ROLE_ASSIGNMENT_ENDED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Subject allocation ended: " + allocationId
        );

        return SubjectAllocationResponse.from(allocation);
    }
}
