package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.dto.GoLiveCheckResponse;
import com.edu360.api.institution.dto.LeaveApproverSetRequest;
import com.edu360.api.institution.dto.LeaveApproverSettingResponse;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

@ExtendWith(MockitoExtension.class)
class LeaveApproverControllerTest {

    @Mock private LeaveApproverSettingRepository leaveApproverSettingRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleDefinitionRepository roleDefinitionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private Authentication authentication;

    private LeaveApproverService leaveApproverService;
    private LeaveApproverController leaveApproverController;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID APPROVER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaveApproverService = new LeaveApproverService(
                leaveApproverSettingRepository,
                institutionRepository,
                tenantRepository,
                userAccountRepository,
                userRoleAssignmentRepository,
                roleDefinitionRepository,
                auditLogService);

        leaveApproverController = new LeaveApproverController(
                leaveApproverService,
                permissionEvaluatorService);
    }

    private void mockPrincipal(UUID tenantId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, USER_ID, UserType.STAFF, "Admin User", false);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    @Test
    @DisplayName("ACT-PRINCIPAL_LEAVE_APPROVER_SET-ALLOW: University Admin sets Principal approver")
    void setPrincipalLeaveApprover_ALLOW_universityAdminSetsPrincipalApprover() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), eq(RbacScope.institution(INSTITUTION_ID)), any()))
                .thenReturn(PermissionDecision.deny("NOT_INST_SCOPE", "Not institution scope"));
        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), eq(RbacScope.tenant()), any()))
                .thenReturn(PermissionDecision.allow());

        Institution inst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionCode("NCS")
                .institutionName("Northbridge College")
                .underUniversity(true)
                .status("DRAFT")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, INSTITUTION_ID))).thenReturn(Optional.of(inst));
        when(userAccountRepository.existsById(new UserAccountId(TENANT_ID, APPROVER_ID))).thenReturn(true);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, APPROVER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        when(leaveApproverSettingRepository.save(any(LeaveApproverSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("MANAGEMENT", APPROVER_ID);
        ResponseEntity<LeaveApproverSettingResponse> response = leaveApproverController.setPrincipalLeaveApprover(
                TENANT_ID, INSTITUTION_ID, reqBody, authentication, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().approverKind()).isEqualTo("MANAGEMENT");
        assertThat(response.getBody().subjectRole()).isEqualTo("PRINCIPAL");
        verify(auditLogService).record(eq(AuditEventType.PRINCIPAL_LEAVE_APPROVER_SET), any(), any(), any(), any());
    }

    @Test
    void setPrincipalLeaveApprover_ALLOW_collegeAdminSetsApproverForOwnInstitution() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), eq(RbacScope.institution(INSTITUTION_ID)), any()))
                .thenReturn(PermissionDecision.allow());

        Institution inst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .institutionCode("NCS")
                .institutionName("Northbridge College")
                .underUniversity(true)
                .status("ACTIVE")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, INSTITUTION_ID))).thenReturn(Optional.of(inst));
        when(userAccountRepository.existsById(new UserAccountId(TENANT_ID, APPROVER_ID))).thenReturn(true);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, APPROVER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        when(leaveApproverSettingRepository.save(any(LeaveApproverSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("REGISTRAR", APPROVER_ID);
        ResponseEntity<LeaveApproverSettingResponse> response = leaveApproverController.setPrincipalLeaveApprover(
                TENANT_ID, INSTITUTION_ID, reqBody, authentication, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().approverKind()).isEqualTo("REGISTRAR");
    }

    @Test
    @DisplayName("ACT-PRINCIPAL_LEAVE_APPROVER_SET-DENY: Unauthorized role is refused with 403")
    void setPrincipalLeaveApprover_DENY_unauthorizedRole() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), eq(RbacScope.institution(INSTITUTION_ID)), any()))
                .thenReturn(PermissionDecision.deny("ACTION_NOT_GRANTED", "Role does not hold action"));
        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), eq(RbacScope.tenant()), any()))
                .thenReturn(PermissionDecision.deny("ACTION_NOT_GRANTED", "Role does not hold action"));

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("MANAGEMENT", APPROVER_ID);
        assertThrows(UnauthorizedException.class, () -> leaveApproverController.setPrincipalLeaveApprover(
                TENANT_ID, INSTITUTION_ID, reqBody, authentication, request));
    }

    @Test
    @DisplayName("ACT-PRINCIPAL_LEAVE_APPROVER_SET-SCOPE: Cross-tenant Principal approver setting rejected")
    void setPrincipalLeaveApprover_SCOPE_crossTenantRejected() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID otherTenantId = UUID.randomUUID();

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("MANAGEMENT", APPROVER_ID);
        assertThrows(UnauthorizedException.class, () -> leaveApproverController.setPrincipalLeaveApprover(
                otherTenantId, INSTITUTION_ID, reqBody, authentication, request));
    }

    @Test
    @DisplayName("ACT-PRINCIPAL_LEAVE_APPROVER_SET-GUARD: Rule C7 Principal self-approving own leave is blocked")
    void setPrincipalLeaveApprover_GUARD_RuleD002_approverIsPrincipalBlocked() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), any(), any()))
                .thenReturn(PermissionDecision.allow());

        Institution inst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .underUniversity(true)
                .status("DRAFT")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, INSTITUTION_ID))).thenReturn(Optional.of(inst));
        when(userAccountRepository.existsById(new UserAccountId(TENANT_ID, APPROVER_ID))).thenReturn(true);

        UUID roleDefId = UUID.randomUUID();
        UserRoleAssignment principalAssignment = UserRoleAssignment.builder()
                .institutionId(INSTITUTION_ID)
                .roleDefinitionId(roleDefId)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, APPROVER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(principalAssignment));

        RoleDefinition roleDef = RoleDefinition.builder()
                .roleCode("PRINCIPAL_COLLEGE")
                .roleOrigin(RoleOrigin.STANDARD)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, roleDefId)))
                .thenReturn(Optional.of(roleDef));

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("MANAGEMENT", APPROVER_ID);
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> leaveApproverController.setPrincipalLeaveApprover(
                        TENANT_ID, INSTITUTION_ID, reqBody, authentication, request));

        assertThat(ex.getErrorCode()).isEqualTo("APPROVER_IS_SUBJECT");
    }

    @Test
    void setPrincipalLeaveApprover_GUARD_RuleC6_standaloneCannotPickRegistrar() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(permissionEvaluatorService.can(any(), eq("PRINCIPAL_LEAVE_APPROVER_SET"), any(), any()))
                .thenReturn(PermissionDecision.allow());

        Institution standaloneInst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .underUniversity(false) // Standalone!
                .status("DRAFT")
                .build();
        when(institutionRepository.findById(new InstitutionId(TENANT_ID, INSTITUTION_ID))).thenReturn(Optional.of(standaloneInst));
        when(userAccountRepository.existsById(new UserAccountId(TENANT_ID, APPROVER_ID))).thenReturn(true);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, APPROVER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("REGISTRAR", APPROVER_ID);
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> leaveApproverController.setPrincipalLeaveApprover(
                        TENANT_ID, INSTITUTION_ID, reqBody, authentication, request));

        assertThat(ex.getErrorCode()).isEqualTo("APPROVER_KIND_MISMATCH");
    }

    @Test
    @DisplayName("ACT-VC_LEAVE_APPROVER_SET-ALLOW: University Admin sets VC leave approver")
    void setVcLeaveApprover_ALLOW_universityAdminSetsVcApprover() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .tenantType("UNIVERSITY_GROUP")
                .state(TenantState.TRIAL)
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.existsById(new UserAccountId(TENANT_ID, APPROVER_ID))).thenReturn(true);
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, APPROVER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        when(leaveApproverSettingRepository.save(any(LeaveApproverSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("GOVERNING_BODY_APPROVER", APPROVER_ID);
        ResponseEntity<LeaveApproverSettingResponse> response = leaveApproverController.setVcLeaveApprover(
                TENANT_ID, reqBody, authentication, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().subjectRole()).isEqualTo("VICE_CHANCELLOR");
        assertThat(response.getBody().approverKind()).isEqualTo("GOVERNING_BODY_APPROVER");
        verify(auditLogService).record(eq(AuditEventType.VC_LEAVE_APPROVER_SET), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACT-VC_LEAVE_APPROVER_SET-DENY: Unauthorized role refused")
    void setVcLeaveApprover_DENY_unauthorizedRole() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        doThrow(new AccessDeniedException("Forbidden")).when(permissionEvaluatorService)
                .authorize(any(), eq("VC_LEAVE_APPROVER_SET"), eq(RbacScope.tenant()), any());

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("MANAGEMENT", APPROVER_ID);
        assertThrows(AccessDeniedException.class, () -> leaveApproverController.setVcLeaveApprover(
                TENANT_ID, reqBody, authentication, request));
    }

    @Test
    @DisplayName("ACT-VC_LEAVE_APPROVER_SET-SCOPE: Cross-tenant VC leave approver request rejected")
    void setVcLeaveApprover_SCOPE_crossTenantRejected() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID otherTenantId = UUID.randomUUID();

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("MANAGEMENT", APPROVER_ID);
        assertThrows(UnauthorizedException.class, () -> leaveApproverController.setVcLeaveApprover(
                otherTenantId, reqBody, authentication, request));
    }

    @Test
    @DisplayName("ACT-VC_LEAVE_APPROVER_SET-GUARD: VC approver must be Management or GBA")
    void setVcLeaveApprover_GUARD_vcApproverMustBeManagementOrGba() {
        mockPrincipal(TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .tenantType("UNIVERSITY_GROUP")
                .state(TenantState.TRIAL)
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.existsById(new UserAccountId(TENANT_ID, APPROVER_ID))).thenReturn(true);

        LeaveApproverSetRequest reqBody = new LeaveApproverSetRequest("REGISTRAR", APPROVER_ID);
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> leaveApproverController.setVcLeaveApprover(
                        TENANT_ID, reqBody, authentication, request));

        assertThat(ex.getErrorCode()).isEqualTo("APPROVER_KIND_MISMATCH");
    }

    @Test
    void getGoLiveCheck_returnsMissingBlockersWhenApproversNotSet() {
        mockPrincipal(TENANT_ID);

        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .tenantType("UNIVERSITY_GROUP")
                .state(TenantState.TRIAL)
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndStatus(TENANT_ID, "VICE_CHANCELLOR", "ACTIVE"))
                .thenReturn(false);

        Institution inst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .status("DRAFT")
                .build();
        when(institutionRepository.findByIdTenantIdAndStatusIn(TENANT_ID, List.of("DRAFT", "ACTIVE")))
                .thenReturn(List.of(inst));

        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                TENANT_ID, "PRINCIPAL", INSTITUTION_ID, "ACTIVE"))
                .thenReturn(false);

        ResponseEntity<GoLiveCheckResponse> response = leaveApproverController.getGoLiveCheck(TENANT_ID, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ready()).isFalse();
        assertThat(response.getBody().blockers()).hasSize(2);
        assertThat(response.getBody().blockers().stream().map(GoLiveCheckResponse.GoLiveBlockerItem::blocker))
                .containsExactlyInAnyOrder("VC_LEAVE_APPROVER_MISSING", "PRINCIPAL_LEAVE_APPROVER_MISSING");
    }

    @Test
    void getGoLiveCheck_returnsReadyTrueWhenAllApproversConfigured() {
        mockPrincipal(TENANT_ID);

        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .tenantType("UNIVERSITY_GROUP")
                .state(TenantState.TRIAL)
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndStatus(TENANT_ID, "VICE_CHANCELLOR", "ACTIVE"))
                .thenReturn(true);

        Institution inst = Institution.builder()
                .id(new InstitutionId(TENANT_ID, INSTITUTION_ID))
                .status("DRAFT")
                .build();
        when(institutionRepository.findByIdTenantIdAndStatusIn(TENANT_ID, List.of("DRAFT", "ACTIVE")))
                .thenReturn(List.of(inst));

        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                TENANT_ID, "PRINCIPAL", INSTITUTION_ID, "ACTIVE"))
                .thenReturn(true);

        ResponseEntity<GoLiveCheckResponse> response = leaveApproverController.getGoLiveCheck(TENANT_ID, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ready()).isTrue();
        assertThat(response.getBody().blockers()).isEmpty();
    }
}
