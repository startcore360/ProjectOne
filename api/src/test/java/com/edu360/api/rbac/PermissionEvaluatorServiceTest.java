package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.UnauthorizedException;

/**
 * Unit tests for PermissionEvaluatorService, with every repository mocked out - no real
 * Postgres/H2 schema is touched (see the test-profile's ddl-auto=none: these entities map onto
 * ref./app. tables H2 cannot create, so exercising them needs mocks, not a loaded context).
 *
 * The codebase's own existing style for this kind of test is descriptive camelCase method
 * names (see AuthServiceTest - there is no "ACT-&lt;ACTION&gt;-ALLOW/DENY/SCOPE/GUARD" naming
 * convention actually in use anywhere in this repository today). Method names and the trailing
 * comment on each test below spell out which of the four required cases (ALLOW/DENY/SCOPE/
 * GUARD) it covers, so the intent is explicit without inventing a prefix style this project
 * does not otherwise use.
 */
@ExtendWith(MockitoExtension.class)
class PermissionEvaluatorServiceTest {

    @Mock private ActionCatalogRepository actionCatalogRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleActionRepository roleActionRepository;
    @Mock private RoleDefinitionRepository roleDefinitionRepository;

    private PermissionEvaluatorService evaluator;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ROLE_DEFINITION_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID OTHER_INSTITUTION_ID = UUID.randomUUID();

    private static final String USER_CREATE = "USER_CREATE";
    private static final String GUARDIAN_CHANGE_APPROVE = "GUARDIAN_CHANGE_APPROVE";

    private void configureGrantedAction(String actionCode, boolean approval, String roleCode, boolean departmentScope) {
        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository,
                roleActionRepository, roleDefinitionRepository, null, null);
        when(actionCatalogRepository.findById(actionCode)).thenReturn(Optional.of(action(actionCode, approval)));
        UserRoleAssignment assignment = departmentScope
                ? UserRoleAssignment.builder()
                    .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                    .userAccountId(USER_ACCOUNT_ID).roleDefinitionId(ROLE_DEFINITION_ID)
                    .scopeType(ScopeType.DEPARTMENT).institutionId(INSTITUTION_ID).departmentId(UUID.randomUUID())
                    .effectiveFrom(Instant.now().minusSeconds(3600)).status(AssignmentStatus.ACTIVE).build()
                : activeAssignment(INSTITUTION_ID);
        UUID departmentId = assignment.getDepartmentId();
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, assignment.getScopeType(), INSTITUTION_ID, departmentId, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID))
                .roleCode(roleCode).roleName(roleCode).roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE).status(RoleDefinitionStatus.ACTIVE).build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID)))
                .thenReturn(Optional.of(role));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                TENANT_ID, ROLE_DEFINITION_ID, actionCode)).thenReturn(true);
    }

    private void init() {
        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository, roleActionRepository);
    }

    private ActionCatalog action(String code, boolean isApproval) {
        ActionCatalog a = new ActionCatalog();
        setField(a, "actionCode", code);
        setField(a, "approvalAction", isApproval);
        return a;
    }

    private UserRoleAssignment activeAssignment(UUID institutionId) {
        ScopeType scopeType = institutionId == null ? ScopeType.TENANT : ScopeType.INSTITUTION;
        return UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ACCOUNT_ID)
                .roleDefinitionId(ROLE_DEFINITION_ID)
                .scopeType(scopeType)
                .institutionId(institutionId)
                .effectiveFrom(Instant.now().minusSeconds(3600))
                .status(AssignmentStatus.ACTIVE)
                .build();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------------------- ALLOW

    @Test
    void can_allowsWhenTheActiveRoleInThatExactScopeHoldsTheAction() {
        init();
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));
        UserRoleAssignment assignment = activeAssignment(INSTITUTION_ID);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_CREATE))
                .thenReturn(true);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_CREATE, RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("ALLOWED");
    }

    // -------------------------------------------------------------------------------- DENY

    @Test
    void can_deniesWhenTheActiveRoleInThatExactScopeDoesNotHoldTheAction() {
        init();
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));
        UserRoleAssignment assignment = activeAssignment(INSTITUTION_ID);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, USER_CREATE))
                .thenReturn(false);

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_CREATE, RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACTION_NOT_GRANTED");
    }

    @Test
    void can_deniesWhenNoUserIsSupplied() {
        init();
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, null), USER_CREATE, RbacScope.tenant(), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_PRINCIPAL");
    }

    @Test
    void can_deniesUnknownActionCode() {
        init();
        when(actionCatalogRepository.findById("NOT_A_REAL_ACTION")).thenReturn(Optional.empty());

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), "NOT_A_REAL_ACTION", RbacScope.tenant(), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("UNKNOWN_ACTION");
    }

    @Test
    void authorize_throwsUnauthorizedExceptionOnDenial_soGlobalExceptionHandlerReturns403() {
        init();
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class, () ->
                evaluator.authorize(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_CREATE, RbacScope.tenant(), RbacTarget.NONE));
    }

    // ------------------------------------------------------------------------------- SCOPE

    @Test
    void can_deniesWhenPrincipalsActiveRoleIsInADifferentScopeThanRequested() {
        init();
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));
        // Principal holds an ACTIVE assignment at INSTITUTION_ID, but the request asks about
        // OTHER_INSTITUTION_ID - the exact-match lookup for the requested scope finds nothing,
        // and Addendum rules 8/9 forbid inferring access from a different, separately-scoped
        // assignment, so this must deny rather than fall back to the other institution's grant.
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, OTHER_INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_CREATE, RbacScope.institution(OTHER_INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
    }

    @Test
    void can_deniesAndDoesNotCombinePermissionsWhenMoreThanOneActiveAssignmentMatchesTheExactScope() {
        init();
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment(INSTITUTION_ID), activeAssignment(INSTITUTION_ID)));

        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), USER_CREATE, RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("AMBIGUOUS_ROLE_ASSIGNMENT");
    }

    @Test
    void can_deniesConfidentialGrievanceActionsForGenericAdministratorEvenIfGranted() {
        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository,
                roleActionRepository, roleDefinitionRepository, null, null);
        when(actionCatalogRepository.findById("GRIEVANCE_CASE_VIEW"))
                .thenReturn(Optional.of(action("GRIEVANCE_CASE_VIEW", false)));
        UserRoleAssignment assignment = activeAssignment(INSTITUTION_ID);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        RoleDefinition genericAdmin = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID))
                .roleCode("COLLEGE_ADMIN").roleName("College Admin")
                .roleOrigin(RoleOrigin.STANDARD).roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE).build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID)))
                .thenReturn(Optional.of(genericAdmin));
        PermissionDecision decision = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "GRIEVANCE_CASE_VIEW", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("POT05_ROLE_FORBIDDEN");
    }

    @Test
    void can_allowsConfidentialGrievanceActionForActiveIccRoleWhenGranted() {
        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository,
                roleActionRepository, roleDefinitionRepository, null, null);
        when(actionCatalogRepository.findById("GRIEVANCE_CASE_VIEW"))
                .thenReturn(Optional.of(action("GRIEVANCE_CASE_VIEW", false)));
        UserRoleAssignment assignment = activeAssignment(INSTITUTION_ID);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        RoleDefinition icc = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID))
                .roleCode("INTERNAL_COMPLAINTS_COMMITTEE").roleName("Internal Complaints Committee")
                .roleOrigin(RoleOrigin.STANDARD).roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE).build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID)))
                .thenReturn(Optional.of(icc));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                TENANT_ID, ROLE_DEFINITION_ID, "GRIEVANCE_CASE_VIEW")).thenReturn(true);

        PermissionDecision decision = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "GRIEVANCE_CASE_VIEW", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void confidentialGrievanceActionsCannotBeAddedToCustomRoles() {
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_CASE_VIEW")).isTrue();
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_CASE_ASSIGN")).isTrue();
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_HEARING_MANAGE")).isTrue();
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_CASE_RESOLVE")).isTrue();
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_EVIDENCE_VIEW")).isTrue();
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_EVIDENCE_UPLOAD")).isTrue();
        assertThat(PermissionEvaluatorService.isLockedForCustomRoles("GRIEVANCE_CASE_SUBMIT")).isFalse();
    }

    // ------------------------------------------------------------------------------- GUARD

    @Test
    void can_deniesSelfApprovalEvenWhenTheRoleWouldOtherwiseGrantTheApprovalAction() {
        init();
        when(actionCatalogRepository.findById(GUARDIAN_CHANGE_APPROVE))
                .thenReturn(Optional.of(action(GUARDIAN_CHANGE_APPROVE, true)));

        // Addendum rule 11: self-approval must be rejected before the role lookup even runs -
        // no repository stub returning "the role holds this action" should be able to matter
        // here, which is why none of the assignment/role-action mocks are stubbed at all: if
        // the guard did not short-circuit, this test would fail with UnfinishedStubbing/NPE
        // rather than silently passing for the wrong reason.
        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                GUARDIAN_CHANGE_APPROVE,
                RbacScope.tenant(),
                RbacTarget.of(USER_ACCOUNT_ID));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void can_allowsApprovingSomeoneElsesRecordWhenRoleGrantsIt() {
        init();
        when(actionCatalogRepository.findById(GUARDIAN_CHANGE_APPROVE))
                .thenReturn(Optional.of(action(GUARDIAN_CHANGE_APPROVE, true)));
        UserRoleAssignment assignment = activeAssignment(null);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, ScopeType.TENANT, null, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(TENANT_ID, ROLE_DEFINITION_ID, GUARDIAN_CHANGE_APPROVE))
                .thenReturn(true);

        UUID someoneElse = UUID.randomUUID();
        PermissionDecision decision = evaluator.can(
                new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID), GUARDIAN_CHANGE_APPROVE, RbacScope.tenant(), RbacTarget.of(someoneElse));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void can_allowsFinanceDirectorOnlyForDesignatedConcessionApprovalAcrossInstitutionScope() {
        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository,
                roleActionRepository, roleDefinitionRepository, null, null);
        when(actionCatalogRepository.findById("CONCESSION_APPROVE"))
                .thenReturn(Optional.of(action("CONCESSION_APPROVE", true)));
        UserRoleAssignment assignment = activeAssignment(null);
        when(userRoleAssignmentRepository.findAllByIdTenantIdAndUserAccountIdAndStatusOrderByEffectiveFromAsc(
                TENANT_ID, USER_ACCOUNT_ID, AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));
        RoleDefinition financeDirector = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID))
                .roleCode("FINANCE_DIRECTOR")
                .roleName("Finance Director")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.UNIVERSITY)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID)))
                .thenReturn(Optional.of(financeDirector));
        when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                TENANT_ID, ROLE_DEFINITION_ID, "CONCESSION_APPROVE")).thenReturn(true);

        PermissionDecision decision = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "CONCESSION_APPROVE", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void canDoesNotGiveFinanceDirectorGeneralTopPostCoverage() {
        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository,
                roleActionRepository, roleDefinitionRepository, null, null);
        when(actionCatalogRepository.findById(USER_CREATE)).thenReturn(Optional.of(action(USER_CREATE, false)));
        UserRoleAssignment assignment = activeAssignment(null);
        when(userRoleAssignmentRepository.findAllByIdTenantIdAndUserAccountIdAndStatusOrderByEffectiveFromAsc(
                TENANT_ID, USER_ACCOUNT_ID, AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));
        RoleDefinition financeDirector = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID))
                .roleCode("FINANCE_DIRECTOR")
                .roleName("Finance Director")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.UNIVERSITY)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEFINITION_ID)))
                .thenReturn(Optional.of(financeDirector));

        PermissionDecision decision = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                USER_CREATE, RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
    }

    @Test
    void idCardIssueIsAllowedOnlyWhenTheActiveRoleHasTheSpecificAction() {
        configureGrantedAction("ID_CARD_ISSUE", false, "RECORDS_OFFICER", false);
        PermissionDecision allowed = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "ID_CARD_ISSUE", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void idCardReplacementApprovalRejectsSelfApproval() {
        configureGrantedAction("ID_CARD_REPLACE", true, "RECORDS_OFFICER", false);
        PermissionDecision denied = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "ID_CARD_REPLACE", RbacScope.institution(INSTITUTION_ID), RbacTarget.of(USER_ACCOUNT_ID));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reasonCode()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void certificateRequestUsesItsOwnStudentAction() {
        configureGrantedAction("CERTIFICATE_REQUEST", false, "STUDENT_COLLEGE", false);
        PermissionDecision allowed = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "CERTIFICATE_REQUEST", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void certificateApprovalRejectsTheApplicantAsApprover() {
        configureGrantedAction("CERTIFICATE_APPROVE", true, "PRINCIPAL_COLLEGE", false);
        PermissionDecision denied = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "CERTIFICATE_APPROVE", RbacScope.institution(INSTITUTION_ID), RbacTarget.of(USER_ACCOUNT_ID));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reasonCode()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void disciplinaryIncidentAccessUsesItsDedicatedDepartmentAction() {
        configureGrantedAction("DISCIPLINARY_INCIDENT_VIEW", false, "HEAD_OF_DEPARTMENT", true);
        UserRoleAssignment assignment = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                        TENANT_ID, USER_ACCOUNT_ID, ScopeType.DEPARTMENT, INSTITUTION_ID,
                        assignmentDepartment(), AssignmentStatus.ACTIVE).get(0);
        PermissionDecision allowed = evaluator.can(new RbacPrincipal(TENANT_ID, USER_ACCOUNT_ID),
                "DISCIPLINARY_INCIDENT_VIEW",
                RbacScope.department(INSTITUTION_ID, assignment.getDepartmentId()), RbacTarget.NONE);
        assertThat(allowed.allowed()).isTrue();
    }

    private UUID assignmentDepartment() {
        // The configured department-scoped assignment is returned from the mocked exact-scope query.
        return userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.eq(USER_ACCOUNT_ID),
                org.mockito.ArgumentMatchers.eq(ScopeType.DEPARTMENT), org.mockito.ArgumentMatchers.eq(INSTITUTION_ID),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(AssignmentStatus.ACTIVE))
                .get(0).getDepartmentId();
    }

}
