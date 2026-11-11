package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.edu360.api.academic.CreateSubjectAllocationRequest;
import com.edu360.api.academic.GradingSchemeService;
import com.edu360.api.academic.GradingSchemeVersion;
import com.edu360.api.academic.GradingSchemeVersionId;
import com.edu360.api.academic.GradingSchemeVersionRepository;
import com.edu360.api.academic.SubjectAllocation;
import com.edu360.api.academic.SubjectAllocationId;
import com.edu360.api.academic.SubjectAllocationRepository;
import com.edu360.api.academic.SubjectAllocationService;
import com.edu360.api.approval.ApprovalBatchRepository;
import com.edu360.api.approval.ApprovalDelegationService;
import com.edu360.api.approval.Delegation;
import com.edu360.api.approval.DelegationId;
import com.edu360.api.approval.DelegationRepository;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLog;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.Department;
import com.edu360.api.institution.DepartmentRepository;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.OfferingDepartmentResolver;
import com.edu360.api.institution.StaffAssignment;
import com.edu360.api.institution.StaffAssignmentRepository;
import com.edu360.api.institution.ProgramRepository;
import com.edu360.api.institution.StructureService;
import com.edu360.api.institution.dto.CreateDepartmentRequest;
import com.edu360.api.tenant.TenantContext;

/**
 * A-05 (Appendix D X3): each action is checked at the scope D3 gives the role. Every SCOPE test
 * uses another institution or department AND a target record that lives there. Real
 * PermissionEvaluatorService over real role/action rows; ALLOW tests assert the saved state and
 * the audit row.
 *
 * Action codes: D2 has no GRADING_SCHEME_ACTIVATE or SUBJECT_ALLOCATION_END action. Activating a
 * grading scheme is GRADING_SCHEME_SET (D2 action 31) and ending an allocation is SUBJECT_ALLOCATE
 * (D2 action 33); the test names are the ones Part 4 A-05 lists.
 */
class ScopeA05Test {

    private RbacTestWorld world;
    private final UUID collegeA = UUID.randomUUID();
    private final UUID collegeB = UUID.randomUUID();
    private final UUID deptA1 = UUID.randomUUID();
    private final UUID deptA2 = UUID.randomUUID();

    // subject allocations
    private SubjectAllocationService allocationService;
    private final Map<UUID, SubjectAllocation> allocations = new HashMap<>();
    private final OfferingDepartmentResolver departments = mock(OfferingDepartmentResolver.class);
    private final StaffAssignmentRepository staffAssignments = mock(StaffAssignmentRepository.class);
    private final UUID offeringInA1 = UUID.randomUUID();
    private final UUID offeringInA2 = UUID.randomUUID();

    // grading schemes
    private GradingSchemeService gradingService;
    private final Map<UUID, GradingSchemeVersion> schemes = new HashMap<>();

    // structure
    private StructureService structureService;
    private final List<Department> savedDepartments = new ArrayList<>();
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);

    // delegation
    private ApprovalDelegationService delegationService;
    private final Map<UUID, Delegation> delegations = new HashMap<>();

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();

        SubjectAllocationRepository allocationRepository = mock(SubjectAllocationRepository.class);
        lenient().when(allocationRepository.save(any())).thenAnswer(inv -> {
            SubjectAllocation a = inv.getArgument(0);
            allocations.put(a.getId().getSubjectAllocationId(), a);
            return a;
        });
        lenient().when(allocationRepository.findByIdTenantIdAndIdSubjectAllocationId(any(), any())).thenAnswer(inv ->
                world.tenantId.equals(inv.getArgument(0)) ? Optional.ofNullable(allocations.get((UUID) inv.getArgument(1))) : Optional.empty());
        lenient().when(staffAssignments.findByTenantIdAndStaffAssignmentId(any(), any())).thenAnswer(inv -> {
            StaffAssignment staff = new StaffAssignment();
            staff.setTenantId(world.tenantId); staff.setStaffAssignmentId(inv.getArgument(1));
            staff.setInstitutionId(collegeA); staff.setStatus("ACTIVE"); return Optional.of(staff);
        });
        allocationService = new SubjectAllocationService(allocationRepository, world.evaluator, world.auditLogService, null, staffAssignments);
        allocationService.setOfferingDepartmentResolver(departments);
        lenient().when(departments.departmentOf(world.tenantId, offeringInA1)).thenReturn(Optional.of(deptA1));
        lenient().when(departments.departmentOf(world.tenantId, offeringInA2)).thenReturn(Optional.of(deptA2));

        GradingSchemeVersionRepository schemeRepository = mock(GradingSchemeVersionRepository.class);
        lenient().when(schemeRepository.save(any())).thenAnswer(inv -> {
            GradingSchemeVersion s = inv.getArgument(0);
            schemes.put(s.getId().getGradingSchemeVersionId(), s);
            return s;
        });
        lenient().when(schemeRepository.findByIdTenantIdAndIdGradingSchemeVersionId(any(), any())).thenAnswer(inv ->
                world.tenantId.equals(inv.getArgument(0)) ? Optional.ofNullable(schemes.get((UUID) inv.getArgument(1))) : Optional.empty());
        gradingService = new GradingSchemeService(schemeRepository, world.evaluator, world.auditLogService);

        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        lenient().when(departmentRepository.save(any())).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            savedDepartments.add(d);
            return d;
        });
        for (UUID college : List.of(collegeA, collegeB)) {
            lenient().when(institutionRepository.findById(new InstitutionId(world.tenantId, college))).thenReturn(Optional.of(
                    Institution.builder().id(new InstitutionId(world.tenantId, college)).institutionType("COLLEGE")
                            .institutionCode("C" + college.toString().substring(0, 4)).institutionName("College").build()));
        }
        structureService = new StructureService(world.evaluator, institutionRepository, departmentRepository,
                mock(ProgramRepository.class), world.auditLogService, null, null, null, null, null, null, null, null);

        DelegationRepository delegationRepository = mock(DelegationRepository.class);
        lenient().when(delegationRepository.save(any())).thenAnswer(inv -> {
            Delegation d = inv.getArgument(0);
            delegations.put(d.getId().getDelegationId(), d);
            return d;
        });
        lenient().when(delegationRepository.findByIdTenantIdAndIdDelegationId(any(), any())).thenAnswer(inv ->
                world.tenantId.equals(inv.getArgument(0)) ? Optional.ofNullable(delegations.get((UUID) inv.getArgument(1))) : Optional.empty());
        delegationService = new ApprovalDelegationService(delegationRepository, mock(ApprovalBatchRepository.class),
                world.evaluator, world.auditLogService, world.userAccountRepository,
                mock(com.edu360.api.approval.ApprovalRequestRepository.class), mock(com.edu360.api.approval.ApprovalInboxService.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TenantPrincipal tp(UUID userId) {
        return new TenantPrincipal(world.tenantId, userId, UserType.STAFF, "user", false);
    }

    private AuditLog lastAudit() {
        return world.auditRows.get(world.auditRows.size() - 1);
    }

    private SubjectAllocation existingAllocation(UUID institutionId, UUID offeringId) {
        UUID id = UUID.randomUUID();
        SubjectAllocation a = SubjectAllocation.builder().id(new SubjectAllocationId(world.tenantId, id))
                .institutionId(institutionId).courseOfferingId(offeringId).sectionId(UUID.randomUUID())
                .staffAssignmentId(UUID.randomUUID()).status("ACTIVE").createdAt(Instant.now())
                .createdBy(UUID.randomUUID()).updatedAt(Instant.now()).updatedBy(UUID.randomUUID()).rowVersion(1).build();
        allocations.put(id, a);
        return a;
    }

    private GradingSchemeVersion draftScheme(UUID institutionId) {
        UUID id = UUID.randomUUID();
        GradingSchemeVersion s = GradingSchemeVersion.builder().id(new GradingSchemeVersionId(world.tenantId, id))
                .institutionId(institutionId).schemeName("10-point").versionNo(1).status("DRAFT")
                .createdAt(Instant.now()).createdBy(UUID.randomUUID()).updatedAt(Instant.now())
                .updatedBy(UUID.randomUUID()).rowVersion(1).build();
        schemes.put(id, s);
        return s;
    }

    // ------------------------------------------------------------------ SUBJECT_ALLOCATE (HOD)

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-ALLOW: HOD allocates a subject in their own department")
    void actSubjectAllocateAllow() {
        UUID hod = world.user("HOD A1");
        world.assign(hod, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA1);
        world.signIn(hod);

        var response = allocationService.allocateSubject(world.tenantId, tp(hod),
                new CreateSubjectAllocationRequest(collegeA, offeringInA1, UUID.randomUUID(), null, UUID.randomUUID()));

        SubjectAllocation saved = allocations.get(response.subjectAllocationId());
        assertThat(saved.getInstitutionId()).isEqualTo(collegeA);
        assertThat(saved.getCourseOfferingId()).isEqualTo(offeringInA1);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(lastAudit().getActorRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(lastAudit().getScopeType()).isEqualTo("DEPARTMENT");
    }

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATE-SCOPE: HOD allocating in another department: 403")
    void actSubjectAllocateScope() {
        UUID hod = world.user("HOD A1");
        world.assign(hod, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA1);
        world.signIn(hod);

        // Target record: a course offering of department A2 (same college, other department).
        assertThatThrownBy(() -> allocationService.allocateSubject(world.tenantId, tp(hod),
                new CreateSubjectAllocationRequest(collegeA, offeringInA2, UUID.randomUUID(), null, UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
        // Target record in another college.
        assertThatThrownBy(() -> allocationService.allocateSubject(world.tenantId, tp(hod),
                new CreateSubjectAllocationRequest(collegeB, offeringInA1, UUID.randomUUID(), null, UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(allocations).isEmpty();
    }

    // ------------------------------------------------- GRADING_SCHEME_SET activation (College Admin)

    @Test
    @DisplayName("ACT-GRADING_SCHEME_ACTIVATE-ALLOW: College Admin activates their own college's grading scheme")
    void actGradingSchemeActivateAllow() {
        UUID admin = world.user("College Admin A");
        world.assign(admin, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        GradingSchemeVersion scheme = draftScheme(collegeA);
        world.signIn(admin);

        gradingService.activateGradingScheme(world.tenantId, tp(admin), scheme.getId().getGradingSchemeVersionId());

        assertThat(schemes.get(scheme.getId().getGradingSchemeVersionId()).getStatus()).isEqualTo("ACTIVE");
        assertThat(lastAudit().getActorRoleCode()).isEqualTo("COLLEGE_ADMIN");
        assertThat(lastAudit().getInstitutionId()).isEqualTo(collegeA);
    }

    @Test
    @DisplayName("ACT-GRADING_SCHEME_ACTIVATE-SCOPE: College Admin of A activating college B's scheme: 403")
    void actGradingSchemeActivateScope() {
        UUID admin = world.user("College Admin A");
        world.assign(admin, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        GradingSchemeVersion schemeOfB = draftScheme(collegeB);
        world.signIn(admin);

        assertThatThrownBy(() -> gradingService.activateGradingScheme(world.tenantId, tp(admin),
                schemeOfB.getId().getGradingSchemeVersionId()))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(schemeOfB.getStatus()).isEqualTo("DRAFT");
    }

    // ---------------------------------------------- SUBJECT_ALLOCATE to end an allocation (College Admin)

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATION_END-ALLOW: College Admin ends an allocation in their own college")
    void actSubjectAllocationEndAllow() {
        UUID admin = world.user("College Admin A");
        world.assign(admin, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        SubjectAllocation allocation = existingAllocation(collegeA, offeringInA1);
        world.signIn(admin);

        allocationService.endAllocation(world.tenantId, tp(admin), allocation.getId().getSubjectAllocationId());

        assertThat(allocation.getStatus()).isEqualTo("ENDED");
        assertThat(lastAudit().getEventType()).isEqualTo(AuditEventType.SUBJECT_ALLOCATION_ENDED);
        assertThat(lastAudit().getActorRoleCode()).isEqualTo("COLLEGE_ADMIN");
    }

    @Test
    @DisplayName("ACT-SUBJECT_ALLOCATION_END-SCOPE: College Admin of A ending college B's allocation: 403")
    void actSubjectAllocationEndScope() {
        UUID admin = world.user("College Admin A");
        world.assign(admin, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        SubjectAllocation allocationOfB = existingAllocation(collegeB, UUID.randomUUID());
        world.signIn(admin);

        assertThatThrownBy(() -> allocationService.endAllocation(world.tenantId, tp(admin),
                allocationOfB.getId().getSubjectAllocationId()))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(allocationOfB.getStatus()).isEqualTo("ACTIVE");
    }

    // ------------------------------------------------------------- STRUCTURE_MANAGE (Registrar)

    @Test
    @DisplayName("ACT-STRUCTURE_MANAGE-ALLOW: Registrar manages structure at university scope (department in a college)")
    void actStructureManageAllow() {
        UUID registrar = world.user("Registrar");
        world.assign(registrar, "REGISTRAR", ScopeType.TENANT, null, null);
        assertThat(RbacTestWorld.standardRoleHolds("REGISTRAR", "STRUCTURE_MANAGE")).isTrue();
        world.signIn(registrar);

        structureService.createDepartment(world.principal(registrar), collegeA, new CreateDepartmentRequest("PHY", "Physics"));

        assertThat(savedDepartments).hasSize(1);
        assertThat(savedDepartments.get(0).getInstitutionId()).isEqualTo(collegeA);
        assertThat(lastAudit().getEventType()).isEqualTo(AuditEventType.DEPARTMENT_CREATED);
        assertThat(lastAudit().getActorRoleCode()).isEqualTo("REGISTRAR");
        assertThat(lastAudit().getScopeType()).isEqualTo("TENANT");
    }

    @Test
    @DisplayName("ACT-STRUCTURE_MANAGE-SCOPE: an institution-scoped holder in another college is refused; a record from elsewhere is not found")
    void actStructureManageScope() {
        // College Admin of A (STRUCTURE_MANAGE at institution scope) acting on college B: 403.
        UUID adminA = world.user("College Admin A");
        world.assign(adminA, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        world.signIn(adminA);
        assertThatThrownBy(() -> structureService.createDepartment(world.principal(adminA), collegeB,
                new CreateDepartmentRequest("CHEM", "Chemistry")))
                .isInstanceOf(UnauthorizedException.class);

        // Registrar acting on an institution that is not in this university: 404.
        UUID registrar = world.user("Registrar");
        world.assign(registrar, "REGISTRAR", ScopeType.TENANT, null, null);
        world.signIn(registrar);
        UUID foreignInstitution = UUID.randomUUID();
        assertThatThrownBy(() -> structureService.createDepartment(world.principal(registrar), foreignInstitution,
                new CreateDepartmentRequest("BIO", "Biology")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(savedDepartments).isEmpty();
    }

    // --------------------------------------------------------------- APPROVAL_DELEGATE (Principal)

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-ALLOW: Principal delegates their approvals at their own institution scope")
    void actApprovalDelegateAllow() {
        UUID principal = world.user("Principal A");
        world.assign(principal, "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, collegeA, null);
        UUID vicePrincipal = world.user("Vice Principal A");
        world.assign(vicePrincipal, "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, collegeA, null);
        world.signIn(principal);

        var response = delegationService.createDelegation(world.tenantId, tp(principal), new CreateDelegationRequest(
                vicePrincipal, "DEPUTY", null, Instant.now(), Instant.now().plus(3, ChronoUnit.DAYS)));

        Delegation saved = delegations.get(response.delegationId());
        assertThat(saved.getDelegatorUserId()).isEqualTo(principal);
        assertThat(saved.getDelegateUserId()).isEqualTo(vicePrincipal);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(lastAudit().getEventType()).isEqualTo(AuditEventType.APPROVAL_DELEGATED);
        assertThat(lastAudit().getActorRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-SCOPE: Principal of A cancelling college B's delegation is refused (404)")
    void actApprovalDelegateScope() {
        UUID principalA = world.user("Principal A");
        world.assign(principalA, "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, collegeA, null);
        UUID principalB = world.user("Principal B");
        world.assign(principalB, "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, collegeB, null);

        UUID delegationOfB = UUID.randomUUID();
        delegations.put(delegationOfB, Delegation.builder().id(new DelegationId(world.tenantId, delegationOfB))
                .delegatorUserId(principalB).delegateUserId(UUID.randomUUID()).delegationBasis("DEPUTY")
                .startsAt(Instant.now()).endsAt(Instant.now().plus(2, ChronoUnit.DAYS)).status("ACTIVE")
                .createdBy(principalB).updatedBy(principalB).build());
        world.signIn(principalA);

        assertThatThrownBy(() -> delegationService.cancelDelegation(world.tenantId, delegationOfB, tp(principalA)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(delegations.get(delegationOfB).getStatus()).isEqualTo("ACTIVE");

        // A delegate who is not a user of this tenant is not found either.
        assertThatThrownBy(() -> delegationService.createDelegation(world.tenantId, tp(principalA), new CreateDelegationRequest(
                UUID.randomUUID(), "DEPUTY", null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
