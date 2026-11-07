package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.CreatePlanRequest;
import com.edu360.api.platformops.dto.PlanResponse;
import com.edu360.api.platformops.dto.TenantActivationRequest;
import com.edu360.api.platformops.dto.TenantCreateRequest;
import com.edu360.api.platformops.dto.TenantLifecycleActionRequest;
import com.edu360.api.platformops.dto.TenantResponse;
import com.edu360.api.platformops.dto.TenantSettingsResponse;
import com.edu360.api.platformops.dto.UpdatePlanRequest;
import com.edu360.api.platformops.dto.UpdateTenantSettingsRequest;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

/**
 * Action & Guard tests for TENANT_CREATE (Sprint 1 Story #48, CL-06, TE-01, TE-19).
 * Tests:
 * - ACT-TENANT_CREATE-ALLOW
 * - ACT-TENANT_CREATE-DENY
 * - ACT-TENANT_CREATE-SCOPE
 * - ACT-TENANT_CREATE-GUARD
 */
class PlatformTenantControllerTest {

    private TenantRepository tenantRepository;
    private PlanRepository planRepository;
    private KeycloakAdminClient keycloakAdminClient;
    private AuditLogService auditLogService;
    private LeaveApproverSettingRepository leaveApproverSettingRepository;
    private InstitutionRepository institutionRepository;
    private PlatformPermissionService platformPermissionService;
    private TenantManagementService tenantManagementService;
    private PlatformTenantController controller;

    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        planRepository = mock(PlanRepository.class);
        keycloakAdminClient = mock(KeycloakAdminClient.class);
        auditLogService = mock(AuditLogService.class);
        leaveApproverSettingRepository = mock(LeaveApproverSettingRepository.class);
        institutionRepository = mock(InstitutionRepository.class);
        platformPermissionService = new PlatformPermissionService(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());

        tenantManagementService = new TenantManagementService(
                tenantRepository,
                planRepository,
                keycloakAdminClient,
                auditLogService,
                leaveApproverSettingRepository,
                institutionRepository
        );

        controller = new PlatformTenantController(platformPermissionService, tenantManagementService);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(PLATFORM_USER_ID, role, "admin@edu360-platform.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private TenantCreateRequest sampleRequest() {
        return TenantCreateRequest.builder()
                .tenantName("Delhi World Public School")
                .tenantType("STANDALONE_SCHOOL")
                .planId(PLAN_ID)
                .subdomain("dwps-delhi")
                .isolationMode(IsolationMode.ISOLATED)
                .firstAdminName("Dr. Sunita Sharma")
                .firstAdminEmail("principal@dwps-delhi.edu")
                .firstAdminMobile("+919876543210")
                .firstAdminNationalIdLast4("4582")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();
    }

    private Plan samplePlan() {
        return Plan.builder()
                .planId(PLAN_ID)
                .planName("Standard School Plan")
                .pricingType("PER_STUDENT_YEAR")
                .priceAmount(new BigDecimal("499.00"))
                .currencyCode("INR")
                .userLimit(1000)
                .storageLimitGb(50)
                .smsLimit(5000)
                .apiLimit(10000)
                .status("ACTIVE")
                .build();
    }

    // -------------------------------------------------------------------------
    // ACT-TENANT_CREATE-ALLOW
    // Platform Admin or Product Owner creates tenant; starts in TRIAL state
    // with 30-day trial and assigned Keycloak realm.
    // -------------------------------------------------------------------------
    @Test
    void act_tenantCreate_allow_forPlatformAdminAndProductOwner() {
        when(tenantRepository.findBySubdomain("dwps-delhi")).thenReturn(Optional.empty());
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantCreateRequest request = sampleRequest();
        ResponseEntity<TenantResponse> response = controller.createTenant(
                request, authFor(PlatformRole.PLATFORM_ADMIN)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TenantResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tenantName()).isEqualTo("Delhi World Public School");
        assertThat(body.subdomain()).isEqualTo("dwps-delhi");
        assertThat(body.state()).isEqualTo(TenantState.TRIAL);
        assertThat(body.keycloakRealm()).isEqualTo("dwps-delhi-realm");
        assertThat(body.isolationMode()).isEqualTo(IsolationMode.ISOLATED);
        assertThat(body.userLimit()).isEqualTo(1000);
        assertThat(body.firstAdminEmail()).isEqualTo("principal@dwps-delhi.edu");
        assertThat(body.firstAdminMobile()).isEqualTo("+919876543210");
        assertThat(body.firstAdminNationalIdLast4()).isEqualTo("4582");
        assertThat(body.trialEndsAt()).isAfter(Instant.now());

        verify(keycloakAdminClient).provisionRealm("dwps-delhi-realm", "Delhi World Public School");
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void act_tenantCreate_provisionsFirstAdminAndRole() {
        com.edu360.api.identity.UserAccountRepository userRepo = mock(com.edu360.api.identity.UserAccountRepository.class);
        com.edu360.api.identity.UserIdentityLinkRepository linkRepo = mock(com.edu360.api.identity.UserIdentityLinkRepository.class);
        com.edu360.api.rbac.RoleDefinitionRepository roleDefRepo = mock(com.edu360.api.rbac.RoleDefinitionRepository.class);
        com.edu360.api.rbac.StandardRoleRepository stdRoleRepo = mock(com.edu360.api.rbac.StandardRoleRepository.class);
        com.edu360.api.rbac.StandardRoleActionRepository stdActionRepo = mock(com.edu360.api.rbac.StandardRoleActionRepository.class);
        com.edu360.api.rbac.RoleActionRepository roleActionRepo = mock(com.edu360.api.rbac.RoleActionRepository.class);
        com.edu360.api.rbac.UserRoleAssignmentRepository assignRepo = mock(com.edu360.api.rbac.UserRoleAssignmentRepository.class);

        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleDefRepo.findByIdTenantIdAndRoleCode(any(), any())).thenReturn(Optional.empty());
        when(roleDefRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assignRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantManagementService serviceWithAdmin = new TenantManagementService(
                tenantRepository, planRepository, keycloakAdminClient, auditLogService,
                leaveApproverSettingRepository, institutionRepository,
                userRepo, linkRepo, roleDefRepo, stdRoleRepo, stdActionRepo, roleActionRepo, assignRepo
        );
        PlatformTenantController testController = new PlatformTenantController(platformPermissionService, serviceWithAdmin);

        when(tenantRepository.findBySubdomain("dwps-delhi")).thenReturn(Optional.empty());
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TenantResponse> response = testController.createTenant(
                sampleRequest(), authFor(PlatformRole.PLATFORM_ADMIN)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TenantResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.firstAdminTemporaryPassword()).isNotNull();
        assertThat(body.firstAdminUserId()).isNotNull();

        verify(userRepo).save(any(com.edu360.api.identity.UserAccount.class));
        verify(linkRepo).save(any(com.edu360.api.identity.UserIdentityLink.class));
        verify(roleDefRepo).save(any(com.edu360.api.rbac.RoleDefinition.class));
        verify(assignRepo).save(any(com.edu360.api.rbac.UserRoleAssignment.class));
    }

    // -------------------------------------------------------------------------
    // ACT-TENANT_CREATE-DENY
    // Unauthorized platform roles (e.g. BILLING_MANAGER, SUPPORT_AGENT)
    // are rejected with 403 / UnauthorizedException.
    // -------------------------------------------------------------------------
    @Test
    void act_tenantCreate_deny_forUnauthorizedPlatformRoles() {
        TenantCreateRequest request = sampleRequest();

        assertThatThrownBy(() -> controller.createTenant(request, authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not hold action TENANT_CREATE");

        assertThatThrownBy(() -> controller.createTenant(request, authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not hold action TENANT_CREATE");
    }

    // -------------------------------------------------------------------------
    // ACT-TENANT_CREATE-SCOPE
    // Non-platform principal or unauthenticated caller is rejected.
    // -------------------------------------------------------------------------
    @Test
    void act_tenantCreate_scope_rejectsNonPlatformPrincipal() {
        TenantCreateRequest request = sampleRequest();
        Authentication nonPlatformAuth = new UsernamePasswordAuthenticationToken("tenant-user-string", null, List.of());

        assertThatThrownBy(() -> controller.createTenant(request, nonPlatformAuth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Platform credentials required");

        assertThatThrownBy(() -> controller.createTenant(request, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    // -------------------------------------------------------------------------
    // ACT-TENANT_CREATE-GUARD
    // 1. Subdomain already taken -> DuplicateResourceException (409 Conflict)
    // 2. Plan does not exist -> IllegalArgumentException (400 Bad Request)
    // 3. Invalid tenant type -> IllegalArgumentException (400 Bad Request)
    // -------------------------------------------------------------------------
    @Test
    void act_tenantCreate_guard_rejectsDuplicateSubdomain() {
        when(tenantRepository.findBySubdomain("dwps-delhi"))
                .thenReturn(Optional.of(Tenant.builder().subdomain("dwps-delhi").build()));

        TenantCreateRequest request = sampleRequest();

        assertThatThrownBy(() -> controller.createTenant(request, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void act_tenantCreate_guard_rejectsMissingPlan() {
        when(tenantRepository.findBySubdomain("dwps-delhi")).thenReturn(Optional.empty());
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        TenantCreateRequest request = sampleRequest();

        assertThatThrownBy(() -> controller.createTenant(request, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void act_tenantCreate_guard_rejectsInvalidTenantType() {
        when(tenantRepository.findBySubdomain("dwps-delhi")).thenReturn(Optional.empty());

        TenantCreateRequest request = sampleRequest();
        request.setTenantType("INVALID_TYPE");

        assertThatThrownBy(() -> controller.createTenant(request, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant type");
    }

    // -------------------------------------------------------------------------
    // Plan listing endpoint: GET /v1/platform/plans
    // -------------------------------------------------------------------------
    @Test
    void listPlans_returnsActivePlans() {
        when(planRepository.findByStatusOrderByPlanNameAsc("ACTIVE"))
                .thenReturn(List.of(samplePlan()));

        ResponseEntity<List<PlanResponse>> response = controller.listPlans(authFor(PlatformRole.PLATFORM_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).planName()).isEqualTo("Standard School Plan");
    }

    // -------------------------------------------------------------------------
    // TENANT_ACTIVATE tests (Story #49, Action Code #203)
    // -------------------------------------------------------------------------
    @Test
    void activateTenant_allowsProductOwnerWhenNoBlockers() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName("Delhi World Public School")
                .tenantType("STANDALONE_SCHOOL")
                .planId(PLAN_ID)
                .state(TenantState.TRIAL)
                .isolationMode(IsolationMode.ISOLATED)
                .subdomain("dwps-delhi")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(institutionRepository.findByIdTenantIdAndStatusIn(eq(tenantId), any())).thenReturn(List.of());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantActivationRequest request = new TenantActivationRequest("PAY-2026-X8841", "Payment confirmed");
        ResponseEntity<TenantResponse> response = controller.activateTenant(
                tenantId, request, authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().state()).isEqualTo(TenantState.ACTIVE);

        verify(auditLogService).record(eq(AuditEventType.TENANT_ACTIVATED), any(), eq("Delhi World Public School"), any(), any());
    }

    @Test
    void activateTenant_deniesWhenPrincipalHasNoPermission() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.activateTenant(
                tenantId, new TenantActivationRequest("PAY-1", "Test"), authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void activateTenant_guardsUniversityGroupAgainstMissingVCLeaveApprover() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName("Northbridge University")
                .tenantType("UNIVERSITY_GROUP")
                .planId(PLAN_ID)
                .state(TenantState.TRIAL)
                .isolationMode(IsolationMode.ISOLATED)
                .subdomain("northbridge")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndStatus(tenantId, "VICE_CHANCELLOR", "ACTIVE"))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.activateTenant(
                tenantId, new TenantActivationRequest("PAY-1", "Test"), authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("VC_LEAVE_APPROVER_MISSING");
    }

    @Test
    void activateTenant_guardsAgainstMissingPrincipalLeaveApprover() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName("Northbridge University")
                .tenantType("STANDALONE_COLLEGE")
                .planId(PLAN_ID)
                .state(TenantState.TRIAL)
                .isolationMode(IsolationMode.ISOLATED)
                .subdomain("northbridge-college")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();

        UUID instId = UUID.randomUUID();
        Institution inst = Institution.builder()
                .id(new InstitutionId(tenantId, instId))
                .institutionName("College of Science")
                .institutionType("COLLEGE")
                .status("ACTIVE")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(institutionRepository.findByIdTenantIdAndStatusIn(eq(tenantId), any())).thenReturn(List.of(inst));
        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(tenantId, "PRINCIPAL", instId, "ACTIVE"))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.activateTenant(
                tenantId, new TenantActivationRequest("PAY-1", "Test"), authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("PRINCIPAL_LEAVE_APPROVER_MISSING");
    }

    @Test
    void ALLOW_productOwnerCreatesPlanWithPricing() {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatePlanRequest request = new CreatePlanRequest(
                "Custom High School Plan",
                "PER_STUDENT_YEAR",
                new BigDecimal("650.00"),
                "INR",
                1200,
                60,
                6000,
                12000,
                2
        );

        ResponseEntity<PlanResponse> response = controller.createPlan(request, authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().planName()).isEqualTo("Custom High School Plan");
        assertThat(response.getBody().priceAmount()).isEqualTo(new BigDecimal("650.00"));
        verify(auditLogService).record(eq(AuditEventType.PLAN_CREATED), any(), any(), any(), any());
    }

    @Test
    void DENY_platformAdminCannotCreatePlanWithPricing_RuleF4() {
        CreatePlanRequest request = new CreatePlanRequest(
                "School Tier A",
                "PER_STUDENT_YEAR",
                new BigDecimal("500.00"),
                "INR",
                1000,
                50,
                5000,
                10000,
                1
        );

        assertThatThrownBy(() -> controller.createPlan(request, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Rule [F-4]");
    }

    @Test
    void ALLOW_platformAdminUpdatesPlanLimitsWithoutPriceChange() {
        UUID planId = UUID.randomUUID();
        Plan existing = samplePlan();
        existing.setPlanId(planId);

        when(planRepository.findById(planId)).thenReturn(Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePlanRequest request = new UpdatePlanRequest(
                "Updated Standard Plan",
                existing.getPriceAmount(), // unchanged price
                "INR",
                2000,
                100,
                10000,
                20000,
                3,
                "ACTIVE"
        );

        ResponseEntity<PlanResponse> response = controller.updatePlan(planId, request, authFor(PlatformRole.PLATFORM_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().planName()).isEqualTo("Updated Standard Plan");
        assertThat(response.getBody().userLimit()).isEqualTo(2000);
        verify(auditLogService).record(eq(AuditEventType.PLAN_UPDATED), any(), any(), any(), any());
    }

    @Test
    void DENY_platformAdminCannotAlterPlanPrice_RuleF4() {
        UUID planId = UUID.randomUUID();
        Plan existing = samplePlan();
        existing.setPlanId(planId);

        when(planRepository.findById(planId)).thenReturn(Optional.of(existing));

        UpdatePlanRequest request = new UpdatePlanRequest(
                "Standard Plan",
                new BigDecimal("9999.00"), // altered price!
                "INR",
                1000,
                50,
                5000,
                10000,
                1,
                "ACTIVE"
        );

        assertThatThrownBy(() -> controller.updatePlan(planId, request, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Rule [F-4]");
    }

    @Test
    void ALLOW_productOwnerSuspendsAndReactivatesTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName("Doon Valley Academy")
                .planId(PLAN_ID)
                .state(TenantState.ACTIVE)
                .subdomain("doon-valley")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        // Suspend
        ResponseEntity<TenantResponse> suspendRes = controller.suspendTenant(
                tenantId, new TenantLifecycleActionRequest("Overdue fee settlement"), authFor(PlatformRole.PRODUCT_OWNER));
        assertThat(suspendRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(suspendRes.getBody().state()).isEqualTo(TenantState.SUSPENDED);
        verify(auditLogService).record(eq(AuditEventType.TENANT_SUSPENDED), any(), any(), any(), any());

        // Reactivate
        ResponseEntity<TenantResponse> reactivateRes = controller.reactivateTenant(
                tenantId, authFor(PlatformRole.PRODUCT_OWNER));
        assertThat(reactivateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reactivateRes.getBody().state()).isEqualTo(TenantState.ACTIVE);
        verify(auditLogService).record(eq(AuditEventType.TENANT_REACTIVATED), any(), any(), any(), any());
    }

    @Test
    void ALLOW_productOwnerArchivesTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName("Old Hilltop School")
                .planId(PLAN_ID)
                .state(TenantState.ACTIVE)
                .subdomain("old-hilltop")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TenantResponse> archiveRes = controller.archiveTenant(
                tenantId, new TenantLifecycleActionRequest("Institution merger closed"), authFor(PlatformRole.PRODUCT_OWNER));
        assertThat(archiveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archiveRes.getBody().state()).isEqualTo(TenantState.ARCHIVED);
        verify(auditLogService).record(eq(AuditEventType.TENANT_ARCHIVED), any(), any(), any(), any());
    }

    @Test
    void ALLOW_platformAdminUpdatesTenantSettings() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName("Northbridge University")
                .planId(PLAN_ID)
                .state(TenantState.ACTIVE)
                .subdomain("northbridge")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .analyticsOptOut(false)
                .userLimit(500)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(samplePlan()));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantSettingsRequest request = new UpdateTenantSettingsRequest(
                "Asia/Kolkata",
                "IN",
                true, // opt out of platform analytics [Rule PV-07]
                "portal.northbridge.edu",
                800,
                100,
                null,
                null
        );

        ResponseEntity<TenantSettingsResponse> response = controller.updateTenantSettings(
                tenantId, request, authFor(PlatformRole.PLATFORM_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().analyticsOptOut()).isTrue();
        assertThat(response.getBody().customDomain()).isEqualTo("portal.northbridge.edu");
        assertThat(response.getBody().userLimit()).isEqualTo(800);
        verify(auditLogService).record(eq(AuditEventType.TENANT_SETTINGS_UPDATED), any(), any(), any(), any());
    }

    @Test
    void DENY_unauthorizedRoleForLifecycleAndPlan() {
        UUID tenantId = UUID.randomUUID();
        Authentication supportAuth = authFor(PlatformRole.SUPPORT_MANAGER);

        assertThatThrownBy(() -> controller.createPlan(
                new CreatePlanRequest("Test", "FLAT", BigDecimal.ZERO, "INR", null, null, null, null, null),
                supportAuth))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> controller.suspendTenant(
                tenantId, new TenantLifecycleActionRequest("test"), supportAuth))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> controller.getTenantSettings(tenantId, supportAuth))
                .isInstanceOf(UnauthorizedException.class);
    }
}
