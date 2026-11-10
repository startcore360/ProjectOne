package com.edu360.api.academic;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.institution.SectionRepository;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.institution.OfferingDepartmentResolver;
import java.util.Objects;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SubjectAllocationService {

    private final SubjectAllocationRepository repository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final SectionRepository sectionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public SubjectAllocationService(
            SubjectAllocationRepository repository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService) {
        this(repository, permissionEvaluatorService, auditLogService, null, null);
    }

    @Autowired
    public SubjectAllocationService(
            SubjectAllocationRepository repository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            @Autowired(required = false) SectionRepository sectionRepository,
            @Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.repository = repository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.sectionRepository = sectionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    /** A-05: resolves the department of a course offering (HOD acts at department scope). */
    @Autowired(required = false)
    public void setOfferingDepartmentResolver(OfferingDepartmentResolver resolver) {
        this.offeringDepartmentResolver = resolver;
    }

    private OfferingDepartmentResolver offeringDepartmentResolver;

    @Transactional(readOnly = true)
    public List<SubjectAllocationResponse> listAllocations(UUID tenantId, TenantPrincipal principal, UUID institutionId) {
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());
        // A-05: an HOD (department-scoped active role) lists only their own department's allocations.
        UserRoleAssignment active = permissionEvaluatorService.activeAssignment(rbac).orElse(null);
        if (active != null && active.getScopeType() == ScopeType.DEPARTMENT) {
            permissionEvaluatorService.authorize(rbac, "SUBJECT_ALLOCATE",
                    PermissionEvaluatorService.scopeOf(active), RbacTarget.NONE);
            return repository.findByIdTenantIdAndInstitutionId(tenantId, active.getInstitutionId()).stream()
                    .filter(a -> active.getDepartmentId().equals(departmentOf(tenantId, a.getCourseOfferingId())))
                    .map(SubjectAllocationResponse::from)
                    .toList();
        }

        permissionEvaluatorService.authorize(
                rbac,
                "SUBJECT_ALLOCATE",
                scopeFor(rbac, institutionId, null),
                RbacTarget.NONE
        );

        List<SubjectAllocation> list = institutionId != null
                ? repository.findByIdTenantIdAndInstitutionId(tenantId, institutionId)
                : repository.findByIdTenantId(tenantId);

        return list.stream().map(SubjectAllocationResponse::from).toList();
    }

    @Transactional
    public SubjectAllocationResponse allocateSubject(UUID tenantId, TenantPrincipal principal, CreateSubjectAllocationRequest request) {
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());
        // A-05: checked at the scope D3 gives the caller's role, against the target's own
        // institution and department (HOD: own department; College/School Admin: own institution).
        permissionEvaluatorService.authorize(
                rbac,
                "SUBJECT_ALLOCATE",
                scopeFor(rbac, request.institutionId(), departmentOf(tenantId, request.courseOfferingId())),
                RbacTarget.NONE
        );

        // Constraint ck_subject_allocation_r1: exactly one of section_id or subject_group_id
        boolean hasSection = request.sectionId() != null;
        boolean hasGroup = request.subjectGroupId() != null;
        if ((hasSection && hasGroup) || (!hasSection && !hasGroup)) {
            throw new BusinessRuleViolationException("INVALID_ALLOCATION_TARGET",
                    "Subject allocation must target either a section or an elective group, not both and not neither.");
        }

        // BS-20: verify that section and staff belong to the institution
        if (sectionRepository != null && request.sectionId() != null) {
            sectionRepository.findByTenantIdAndSectionId(tenantId, request.sectionId()).ifPresent(sec -> {
                if (request.institutionId() != null && sec.getInstitutionId() != null && !request.institutionId().equals(sec.getInstitutionId())) {
                    throw new BusinessRuleViolationException("SECTION_INSTITUTION_MISMATCH",
                            "Section does not belong to the specified institution.");
                }
            });
        }
        if (userRoleAssignmentRepository != null && request.staffAssignmentId() != null) {
            userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(tenantId, request.staffAssignmentId()).ifPresent(staff -> {
                if (request.institutionId() != null && staff.getInstitutionId() != null && !request.institutionId().equals(staff.getInstitutionId())) {
                    throw new BusinessRuleViolationException("STAFF_INSTITUTION_MISMATCH",
                            "Staff assignment does not belong to the specified institution.");
                }
            });
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
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());

        SubjectAllocation allocation = repository.findByIdTenantIdAndIdSubjectAllocationId(tenantId, allocationId)
                .orElseThrow(() -> new BusinessRuleViolationException("ALLOCATION_NOT_FOUND", "Subject allocation not found"));

        // A-05: ending an allocation is SUBJECT_ALLOCATE at the allocation's own institution
        // (and department, for an HOD) - never a tenant-wide check.
        permissionEvaluatorService.authorize(
                rbac,
                "SUBJECT_ALLOCATE",
                scopeFor(rbac, allocation.getInstitutionId(), departmentOf(tenantId, allocation.getCourseOfferingId())),
                RbacTarget.NONE
        );

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

    private UUID departmentOf(UUID tenantId, UUID courseOfferingId) {
        return offeringDepartmentResolver == null ? null
                : offeringDepartmentResolver.departmentOf(tenantId, courseOfferingId).orElse(null);
    }

    /** A-05 scope for a target record; the institution scope only if the evaluator gives none. */
    private RbacScope scopeFor(RbacPrincipal rbac, UUID institutionId, UUID departmentId) {
        RbacScope fallback = institutionId != null ? RbacScope.institution(institutionId) : RbacScope.tenant();
        return Objects.requireNonNullElse(
                permissionEvaluatorService.targetScope(rbac, institutionId, departmentId), fallback);
    }
}
