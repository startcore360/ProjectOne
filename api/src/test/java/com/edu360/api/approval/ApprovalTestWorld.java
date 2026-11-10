package com.edu360.api.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.academic.PromotionRun;
import com.edu360.api.academic.PromotionRunId;
import com.edu360.api.academic.PromotionRunRepository;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.guardian.GuardianLinkService;
import com.edu360.api.guardian.GuardianProfile;
import com.edu360.api.guardian.GuardianProfileRepository;
import com.edu360.api.guardian.StudentGuardianLink;
import com.edu360.api.guardian.StudentGuardianLinkId;
import com.edu360.api.guardian.StudentGuardianLinkRepository;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.InstitutionService;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RbacTestWorld;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rolldown.RoleRolldownDefault;
import com.edu360.api.rolldown.RoleRolldownDefaultRepository;
import com.edu360.api.rolldown.RoleRolldownOverride;
import com.edu360.api.rolldown.RoleRolldownOverrideRepository;
import com.edu360.api.rolldown.RolldownService;
import com.edu360.api.tenant.TenantContext;

/**
 * Test fixture for B2: the REAL evaluator, routing, inbox, delegation, guardian and institution
 * services (rule R3) over in-memory stores. Only storage is simulated; every permission and
 * routing decision under test is made by production code. Roles are the real D3 rows from
 * sql/05_ref_data.sql through {@link RbacTestWorld}.
 */
final class ApprovalTestWorld {

    final RbacTestWorld w = new RbacTestWorld();
    final UUID tenantId = w.tenantId;

    final Map<UUID, ApprovalRequest> requests = new LinkedHashMap<>();
    final List<ApprovalStep> steps = new ArrayList<>();
    final List<Delegation> delegations = new ArrayList<>();
    final Map<UUID, Institution> institutions = new LinkedHashMap<>();
    final Map<UUID, StudentProfile> students = new LinkedHashMap<>();
    final Map<UUID, StudentGuardianLink> links = new LinkedHashMap<>();
    final Map<UUID, PromotionRun> runs = new LinkedHashMap<>();
    final List<RoleRolldownOverride> overrides = new ArrayList<>();
    final List<RoleRolldownDefault> rolldownDefaults = new ArrayList<>();
    final Set<UUID> leaveApproverInstitutions = new HashSet<>();

    final ApprovalRouting routing;
    final ApprovalInboxService inbox;
    final ApprovalDelegationService delegation;
    final GuardianLinkService guardian;
    final InstitutionService institutionService;
    final RolldownService rolldown;
    final PromotionRunRepository promotionRunRepository = mock(PromotionRunRepository.class);
    final ApprovalRequestRepository requestRepository = mock(ApprovalRequestRepository.class);
    final ApprovalStepRepository stepRepository = mock(ApprovalStepRepository.class);

    ApprovalTestWorld() {
        DelegationRepository delegationRepository = mock(DelegationRepository.class);
        InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
        StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
        StudentGuardianLinkRepository linkRepository = mock(StudentGuardianLinkRepository.class);
        GuardianProfileRepository profileRepository = mock(GuardianProfileRepository.class);
        LeaveApproverSettingRepository leaveRepository = mock(LeaveApproverSettingRepository.class);
        RoleRolldownOverrideRepository overrideRepository = mock(RoleRolldownOverrideRepository.class);
        RoleRolldownDefaultRepository defaultRepository = mock(RoleRolldownDefaultRepository.class);
        ApprovalBatchRepository batchRepository = mock(ApprovalBatchRepository.class);

        // -- approval requests and steps
        lenient().when(requestRepository.findByIdTenantIdAndIdApprovalRequestId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(requests.get((UUID) inv.getArgument(1))).filter(r -> r.getId().getTenantId().equals(inv.getArgument(0))));
        lenient().when(requestRepository.save(any())).thenAnswer(inv -> {
            ApprovalRequest r = inv.getArgument(0);
            requests.put(r.getId().getApprovalRequestId(), r);
            return r;
        });
        lenient().when(requestRepository.findByIdTenantIdAndSubjectTypeAndSubjectIdAndStatus(any(), any(), any(), any()))
                .thenAnswer(inv -> requests.values().stream().filter(r -> r.getId().getTenantId().equals(inv.getArgument(0))
                        && r.getSubjectType().equals(inv.getArgument(1)) && r.getSubjectId().equals(inv.getArgument(2))
                        && r.getStatus().equals(inv.getArgument(3))).toList());
        lenient().when(stepRepository.save(any())).thenAnswer(inv -> {
            ApprovalStep s = inv.getArgument(0);
            steps.removeIf(x -> x.getId().getApprovalStepId().equals(s.getId().getApprovalStepId()));
            steps.add(s);
            return s;
        });
        lenient().when(stepRepository.findByIdTenantIdAndDecision(any(), any())).thenAnswer(inv ->
                steps.stream().filter(s -> s.getId().getTenantId().equals(inv.getArgument(0))
                        && s.getDecision().equals(inv.getArgument(1))).toList());
        lenient().when(stepRepository.findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(any(), any())).thenAnswer(inv ->
                steps.stream().filter(s -> s.getId().getTenantId().equals(inv.getArgument(0))
                        && s.getApprovalRequestId().equals(inv.getArgument(1)))
                        .sorted((a, b) -> Short.compare(a.getStepNo(), b.getStepNo())).toList());
        lenient().when(stepRepository.findByIdTenantIdAndApprovalRequestIdAndStepNo(any(), any(), org.mockito.ArgumentMatchers.anyShort()))
                .thenAnswer(inv -> steps.stream().filter(s -> s.getId().getTenantId().equals(inv.getArgument(0))
                        && s.getApprovalRequestId().equals(inv.getArgument(1))
                        && s.getStepNo() == (short) inv.getArgument(2)).findFirst());

        // -- delegations
        lenient().when(delegationRepository.save(any())).thenAnswer(inv -> {
            Delegation d = inv.getArgument(0);
            delegations.removeIf(x -> x.getId().getDelegationId().equals(d.getId().getDelegationId()));
            delegations.add(d);
            return d;
        });
        lenient().when(delegationRepository.findByIdTenantIdAndDelegatorUserId(any(), any())).thenAnswer(inv ->
                delegations.stream().filter(d -> d.getDelegatorUserId().equals(inv.getArgument(1))).toList());
        lenient().when(delegationRepository.findByIdTenantIdAndDelegateUserId(any(), any())).thenAnswer(inv ->
                delegations.stream().filter(d -> d.getDelegateUserId().equals(inv.getArgument(1))).toList());
        lenient().when(delegationRepository.findByIdTenantIdAndIdDelegationId(any(), any())).thenAnswer(inv ->
                delegations.stream().filter(d -> d.getId().getDelegationId().equals(inv.getArgument(1))).findFirst());

        // -- institutions, students, guardians, promotion
        lenient().when(institutionRepository.findByIdTenantIdAndIdInstitutionId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(institutions.get((UUID) inv.getArgument(1))));
        lenient().when(institutionRepository.save(any())).thenAnswer(inv -> {
            Institution i = inv.getArgument(0);
            institutions.put(i.getInstitutionId(), i);
            return i;
        });
        lenient().when(studentRepository.findById(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(students.get((UUID) inv.getArgument(0))));
        lenient().when(linkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(links.get((UUID) inv.getArgument(1))));
        lenient().when(linkRepository.save(any())).thenAnswer(inv -> {
            StudentGuardianLink l = inv.getArgument(0);
            links.put(l.getId().getStudentGuardianLinkId(), l);
            return l;
        });
        lenient().when(linkRepository.findByIdTenantIdAndStudentProfileId(any(), any())).thenAnswer(inv ->
                links.values().stream().filter(l -> l.getStudentProfileId().equals(inv.getArgument(1))).toList());
        lenient().when(linkRepository.countByIdTenantIdAndStudentProfileId(any(), any())).thenAnswer(inv ->
                links.values().stream().filter(l -> l.getStudentProfileId().equals(inv.getArgument(1))).count());
        lenient().when(profileRepository.findByIdTenantIdAndMobile(any(), any())).thenReturn(Optional.empty());
        lenient().when(profileRepository.findByIdTenantIdAndIdGuardianProfileId(any(), any())).thenReturn(Optional.empty());
        lenient().when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(runs.get((UUID) inv.getArgument(1))));
        lenient().when(promotionRunRepository.save(any())).thenAnswer(inv -> {
            PromotionRun r = inv.getArgument(0);
            runs.put(r.getId().getPromotionRunId(), r);
            return r;
        });
        lenient().when(leaveRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(any(), any(), any(), any()))
                .thenAnswer(inv -> leaveApproverInstitutions.contains((UUID) inv.getArgument(2)));

        // -- roll-down
        lenient().when(overrideRepository.findByIdTenantIdAndStatus(any(), any())).thenAnswer(inv ->
                overrides.stream().filter(o -> o.getStatus().equals(inv.getArgument(1))).toList());
        lenient().when(overrideRepository.save(any())).thenAnswer(inv -> {
            RoleRolldownOverride o = inv.getArgument(0);
            overrides.add(o);
            return o;
        });
        lenient().when(defaultRepository.findByContextOrderByMissingRoleCodeAscPreferenceAsc(any())).thenAnswer(inv ->
                rolldownDefaults.stream().filter(d -> d.getContext().equals(inv.getArgument(0))).toList());
        lenient().when(w.userRoleAssignmentRepository.findByIdTenantIdAndRoleDefinitionIdAndStatus(any(), any(), any()))
                .thenAnswer(inv -> w.assignments.stream().filter(a ->
                        a.getRoleDefinitionId().equals(inv.getArgument(1)) && a.getStatus() == inv.getArgument(2)).toList());

        routing = new ApprovalRouting(delegationRepository, w.evaluator, w.roleDefinitionRepository,
                w.userRoleAssignmentRepository, overrideRepository, defaultRepository, institutionRepository);
        inbox = new ApprovalInboxService(requestRepository, stepRepository, w.auditLogService, linkRepository,
                institutionRepository, promotionRunRepository, w.evaluator, routing, leaveRepository);
        delegation = new ApprovalDelegationService(delegationRepository, batchRepository, w.evaluator,
                w.auditLogService, w.userAccountRepository, requestRepository, inbox);
        guardian = new GuardianLinkService(linkRepository, profileRepository, w.evaluator, w.auditLogService,
                requestRepository, stepRepository, studentRepository, institutionRepository, inbox);
        institutionService = new InstitutionService(institutionRepository, leaveRepository,
                mock(com.edu360.api.tenant.TenantRepository.class), mock(com.edu360.api.platform.PlanRepository.class),
                w.evaluator, w.auditLogService, requestRepository, stepRepository, inbox);
        rolldown = new RolldownService(overrideRepository, defaultRepository, w.evaluator, w.auditLogService);

        // ref.role_rolldown_default row 5 (sql/10d_v40_rolldown_ref.sql): the Data Protection Contact's
        // approvals roll down to the Principal in a college.
        rolldownDefaults.add(new RoleRolldownDefault(5, "DATA_PROTECTION_CONTACT", "COLLEGE", "APPROVE", (short) 1,
                "PRINCIPAL_COLLEGE", "RL-08"));
    }

    // ------------------------------------------------------------------ builders

    UUID institution(String type, String status) {
        UUID id = UUID.randomUUID();
        institutions.put(id, Institution.builder().id(new InstitutionId(tenantId, id)).institutionType(type)
                .institutionCode("I" + id.toString().substring(0, 6)).institutionName("Inst " + id.toString().substring(0, 6))
                .status(status).rowVersion(1).build());
        return id;
    }

    /** A user holding one standard role at the scope. */
    UUID staff(String name, String roleCode, ScopeType scope, UUID institutionId) {
        UUID u = w.user(name);
        w.assign(u, roleCode, scope, institutionId, null);
        return u;
    }

    StudentProfile student(UUID institutionId) {
        StudentProfile s = new StudentProfile();
        s.setStudentProfileId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setInstitutionId(institutionId);
        students.put(s.getStudentProfileId(), s);
        return s;
    }

    StudentGuardianLink link(UUID studentId) {
        UUID id = UUID.randomUUID();
        StudentGuardianLink l = StudentGuardianLink.builder().id(new StudentGuardianLinkId(tenantId, id))
                .studentProfileId(studentId).guardianProfileId(UUID.randomUUID()).relationship("FATHER")
                .status("ACTIVE").rowVersion(1).build();
        links.put(id, l);
        return l;
    }

    /** A pending guardian-change request for the link, one step per approver role, raised by the requester. */
    ApprovalRequest guardianRequest(StudentGuardianLink link, UUID institutionId, UUID requester, String... stepRoles) {
        return request("GUARDIAN_CHANGE", "STUDENT_GUARDIAN_LINK", link.getId().getStudentGuardianLinkId(),
                institutionId, requester, stepRoles);
    }

    ApprovalRequest request(String process, String subjectType, UUID subjectId, UUID institutionId, UUID requester, String... stepRoles) {
        UUID id = UUID.randomUUID();
        ApprovalRequest r = ApprovalRequest.builder().id(new ApprovalRequestId(tenantId, id)).institutionId(institutionId)
                .processCode(process).subjectType(subjectType).subjectId(subjectId).requestedByUserId(requester)
                .requesterActiveRole("REQUESTER").status("PENDING").currentStepNo((short) 1).rowVersion(1).build();
        requests.put(id, r);
        short n = 1;
        for (String role : stepRoles) {
            steps.add(ApprovalStep.builder().id(new ApprovalStepId(tenantId, UUID.randomUUID())).approvalRequestId(id)
                    .stepNo(n++).approverRoleCode(role).decision("PENDING").rowVersion(1).build());
        }
        return r;
    }

    Delegation delegate(UUID delegator, UUID delegate, Instant from, Instant to) {
        Delegation d = Delegation.builder().id(new DelegationId(tenantId, UUID.randomUUID())).delegatorUserId(delegator)
                .delegateUserId(delegate).delegationBasis("LEAVE").startsAt(from).endsAt(to).status("ACTIVE")
                .createdAt(Instant.now()).updatedAt(Instant.now()).rowVersion(1).build();
        delegations.add(d);
        return d;
    }

    ApprovalStep step(ApprovalRequest r, int no) {
        return steps.stream().filter(s -> s.getApprovalRequestId().equals(r.getId().getApprovalRequestId()) && s.getStepNo() == no)
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ calling as a user

    /** Runs the real sign-in lookup, so TenantContext carries the user's active role and scope. */
    TenantPrincipal as(UUID userId) {
        TenantContext.clear();
        w.signIn(userId);
        return new TenantPrincipal(tenantId, userId, UserType.STAFF, "User", false);
    }

    Authentication auth(TenantPrincipal p) {
        return new UsernamePasswordAuthenticationToken(p, "n/a", List.of());
    }

    boolean audited(String fragment) {
        return w.auditRows.stream().anyMatch(a -> a.getDetails() != null && a.getDetails().contains(fragment));
    }

    static HttpStatus statusOf(Throwable t) {
        return t instanceof com.edu360.api.exception.ResourceNotFoundException ? HttpStatus.NOT_FOUND
                : t instanceof com.edu360.api.exception.SelfApprovalForbiddenException ? HttpStatus.FORBIDDEN
                : t instanceof com.edu360.api.exception.DuplicateResourceException ? HttpStatus.CONFLICT
                : t instanceof com.edu360.api.exception.BusinessRuleViolationException ? HttpStatus.UNPROCESSABLE_ENTITY
                : t instanceof com.edu360.api.exception.UnauthorizedException ? HttpStatus.FORBIDDEN
                : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    static PromotionRun run(UUID tenantId, UUID institutionId, String status, UUID createdBy, UUID approvalRequestId) {
        return PromotionRun.builder().id(new PromotionRunId(tenantId, UUID.randomUUID())).institutionId(institutionId)
                .fromSessionId(UUID.randomUUID()).toSessionId(UUID.randomUUID()).status(status).createdBy(createdBy)
                .approvalRequestId(approvalRequestId).build();
    }

    @SuppressWarnings("unused")
    private static AssignmentStatus unused() {
        return AssignmentStatus.ACTIVE;
    }
}
