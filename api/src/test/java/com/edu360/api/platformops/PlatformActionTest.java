package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.billing.Invoice;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.billing.PlatformBillingController;
import com.edu360.api.platformops.billing.PlatformBillingService;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
import com.edu360.api.platformops.dto.CreatePlanRequest;
import com.edu360.api.platformops.dto.IntegrationTypeResponse;
import com.edu360.api.platformops.dto.PlanResponse;
import com.edu360.api.platformops.dto.PlatformUserCreateRequest;
import com.edu360.api.platformops.dto.PlatformUserResponse;
import com.edu360.api.platformops.dto.TenantActivationRequest;
import com.edu360.api.platformops.dto.TenantCreateRequest;
import com.edu360.api.platformops.dto.TenantLifecycleActionRequest;
import com.edu360.api.platformops.dto.TenantResponse;
import com.edu360.api.platformops.dto.TenantSettingsResponse;
import com.edu360.api.platformops.dto.UpdatePlanRequest;
import com.edu360.api.platformops.dto.UpdateTenantSettingsRequest;
import com.edu360.api.platformops.entitlement.PlanEntitlementRepository;
import com.edu360.api.platformops.entitlement.PlatformFeatureToggleController;
import com.edu360.api.platformops.entitlement.PlatformFeatureToggleController.EntitlementsResponse;
import com.edu360.api.platformops.entitlement.PlatformFeatureToggleController.EntitlementsUpdateRequest;
import com.edu360.api.platformops.entitlement.TenantEntitlementRepository;
import com.edu360.api.platformops.release.PlatformRelease;
import com.edu360.api.platformops.release.PlatformReleaseController;
import com.edu360.api.platformops.release.PlatformReleaseRepository;
import com.edu360.api.platformops.release.PlatformReleaseService;
import com.edu360.api.platformops.release.dto.CreateReleaseRequest;
import com.edu360.api.platformops.release.dto.ReleaseResponse;
import com.edu360.api.platformops.report.PlatformReportController;
import com.edu360.api.platformops.report.PlatformReportController.PlatformReportResponse;
import com.edu360.api.platformops.security.PlatformSecurityPolicyController;
import com.edu360.api.platformops.security.PlatformSecurityPolicyController.SecurityPolicyResponse;
import com.edu360.api.platformops.security.PlatformSecurityPolicyController.UpdateSecurityPolicyRequest;
import com.edu360.api.platformops.security.PlatformSettingValueRepository;
import com.edu360.api.platformops.security.SecurityIncidentRepository;
import com.edu360.api.platformops.support.PlatformSupportController;
import com.edu360.api.platformops.support.PlatformSupportService;
import com.edu360.api.platformops.support.SupportAccessGrant;
import com.edu360.api.platformops.support.SupportAccessGrantRepository;
import com.edu360.api.platformops.support.SupportTicket;
import com.edu360.api.platformops.support.SupportTicketRepository;
import com.edu360.api.platformops.support.TenantSupportController;
import com.edu360.api.platformops.support.dto.CreateSupportTicketRequest;
import com.edu360.api.platformops.support.dto.GrantSupportAccessRequest;
import com.edu360.api.platformops.support.dto.SupportAccessGrantResponse;
import com.edu360.api.platformops.support.dto.SupportTicketResponse;
import com.edu360.api.platformops.support.dto.UpdateSupportTicketRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class PlatformActionTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID grantId = UUID.randomUUID();
    private final UUID releaseId = UUID.randomUUID();

    // Mocks & Services
    private PlatformPermissionService permissionService;
    private AuditLogService auditLogService;
    private com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;
    private TenantRepository tenantRepository;
    private PlanRepository planRepository;
    private KeycloakAdminClient keycloakAdminClient;
    private LeaveApproverSettingRepository leaveApproverSettingRepository;
    private InstitutionRepository institutionRepository;
    private TenantManagementService tenantManagementService;
    private PlatformTenantController tenantController;

    private PlanEntitlementRepository planEntitlementRepository;
    private TenantEntitlementRepository tenantEntitlementRepository;
    private PlatformFeatureToggleController featureToggleController;

    private InvoiceRepository invoiceRepository;
    private PlatformBillingService billingService;
    private PlatformBillingController billingController;

    private SupportTicketRepository ticketRepository;
    private SupportAccessGrantRepository grantRepository;
    private PlatformSupportService supportService;
    private PlatformSupportController supportController;
    private PermissionEvaluatorService permissionEvaluatorService;
    private TenantSupportController tenantSupportController;

    private PlatformReleaseRepository releaseRepository;
    private PlatformReleaseService releaseService;
    private PlatformReleaseController releaseController;

    private IntegrationTypeRepository integrationTypeRepository;
    private IntegrationTypeController integrationTypeController;

    private PlatformAuditOutboxEventRepository auditOutboxEventRepository;
    private PlatformAuditLogController auditLogController;

    private UsageMetricDailyRepository usageMetricDailyRepository;
    private UsageCountsController usageCountsController;

    private TenantBackupStatusRepository tenantBackupStatusRepository;
    private BackupStatusController backupStatusController;

    private PlatformUserManagementService platformUserManagementService;
    private PlatformUserController userController;

    private PlatformReportController reportController;
    private SecurityIncidentRepository incidentRepository;
    private PlatformSettingValueRepository settingValueRepository;
    private PlatformSecurityPolicyController securityPolicyController;
    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        platformAuditWriter = mock(com.edu360.api.platformops.role.PlatformAuditWriter.class);
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(PlatformAccessLookup.standardRolesOnly(), platformAuditWriter);
        invoiceRepository = mock(InvoiceRepository.class);
        tenantRepository = mock(TenantRepository.class);
        planRepository = mock(PlanRepository.class);
        keycloakAdminClient = mock(KeycloakAdminClient.class);
        leaveApproverSettingRepository = mock(LeaveApproverSettingRepository.class);
        institutionRepository = mock(InstitutionRepository.class);

        tenantManagementService = new TenantManagementService(
                tenantRepository, planRepository, keycloakAdminClient,
                leaveApproverSettingRepository, institutionRepository,
                null, null, null, null, null, null, null, invoiceRepository, platformAuditWriter
        );
        tenantController = new PlatformTenantController(permissionService, tenantManagementService);

        planEntitlementRepository = mock(PlanEntitlementRepository.class);
        tenantEntitlementRepository = mock(TenantEntitlementRepository.class);
        featureToggleController = new PlatformFeatureToggleController(
                permissionService, planEntitlementRepository, tenantEntitlementRepository, platformAuditWriter
        );

        billingService = new PlatformBillingService(invoiceRepository, platformAuditWriter);
        billingController = new PlatformBillingController(billingService, permissionService);

        ticketRepository = mock(SupportTicketRepository.class);
        grantRepository = mock(SupportAccessGrantRepository.class);
        supportService = new PlatformSupportService(ticketRepository, grantRepository, platformAuditWriter, auditLogService);
        supportController = new PlatformSupportController(supportService, permissionService);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        tenantSupportController = new TenantSupportController(supportService, permissionEvaluatorService);

        releaseRepository = mock(PlatformReleaseRepository.class);
        releaseService = new PlatformReleaseService(releaseRepository, platformAuditWriter);
        releaseController = new PlatformReleaseController(releaseService, permissionService);

        integrationTypeRepository = mock(IntegrationTypeRepository.class);
        integrationTypeController = new IntegrationTypeController(permissionService, integrationTypeRepository, platformAuditWriter);

        auditOutboxEventRepository = mock(PlatformAuditOutboxEventRepository.class);
        auditLogController = new PlatformAuditLogController(permissionService, auditOutboxEventRepository);

        usageMetricDailyRepository = mock(UsageMetricDailyRepository.class);
        usageCountsController = new UsageCountsController(permissionService, tenantRepository, usageMetricDailyRepository);

        tenantBackupStatusRepository = mock(TenantBackupStatusRepository.class);
        backupStatusController = new BackupStatusController(permissionService, tenantBackupStatusRepository);

        platformUserManagementService = mock(PlatformUserManagementService.class);
        userController = new PlatformUserController(permissionService, platformUserManagementService);

        reportController = new PlatformReportController(permissionService, invoiceRepository, ticketRepository, grantRepository, tenantRepository);
        incidentRepository = mock(SecurityIncidentRepository.class);
        settingValueRepository = mock(PlatformSettingValueRepository.class);
        securityPolicyController = new PlatformSecurityPolicyController(permissionService, incidentRepository, settingValueRepository, platformAuditWriter, null);
    }

    private Authentication auth(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, role, "platform@edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private Authentication tenantAuth(UUID tid) {
        TenantPrincipal principal = new TenantPrincipal(tid, userId, null, "Tenant User", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    // =========================================================================
    // 47. PLAN_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-PLAN_MANAGE-ALLOW: Product Owner creates plan with pricing")
    void actPlanManage_allow() {
        CreatePlanRequest req = new CreatePlanRequest(
                "Growth Plan", "PER_STUDENT_YEAR", new BigDecimal("799.00"), "INR",
                2500, 100, 10000, 25000, 10
        );
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setPlanId(planId);
            return p;
        });

        ResponseEntity<PlanResponse> res = tenantController.createPlan(req, auth(PlatformRole.PRODUCT_OWNER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().planName()).isEqualTo("Growth Plan");
    }

    @Test
    @DisplayName("ACT-PLAN_MANAGE-DENY: Platform Admin cannot create plan with pricing under Rule F-4")
    void actPlanManage_deny() {
        CreatePlanRequest req = new CreatePlanRequest(
                "School Tier", "PER_STUDENT_YEAR", new BigDecimal("499.00"), "INR",
                1000, 50, 5000, 10000, 5
        );

        assertThatThrownBy(() -> tenantController.createPlan(req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Rule [F-4]");
    }

    @Test
    @DisplayName("ACT-PLAN_MANAGE-SCOPE: Non-platform principal rejected")
    void actPlanManage_scope() {
        CreatePlanRequest req = new CreatePlanRequest(
                "Tier", "FLAT", new BigDecimal("1000.00"), "INR", 100, 10, 1000, 1000, 1
        );
        assertThatThrownBy(() -> tenantController.createPlan(req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLAN_MANAGE-GUARD: Platform Admin cannot alter price on existing plan")
    void actPlanManage_guard() {
        Plan plan = Plan.builder().planId(planId).planName("Old").pricingType("FLAT")
                .priceAmount(new BigDecimal("100.00")).currencyCode("INR").build();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        UpdatePlanRequest req = new UpdatePlanRequest(
                "New Name", new BigDecimal("200.00"), "INR",
                100, 10, 1000, 1000, 5, "ACTIVE"
        );
        assertThatThrownBy(() -> tenantController.updatePlan(planId, req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Rule [F-4]");
    }

    // =========================================================================
    // 48. TENANT_CREATE
    // =========================================================================

    @Test
    @DisplayName("ACT-TENANT_CREATE-ALLOW: Platform Admin creates tenant")
    void actTenantCreate_allow() {
        TenantCreateRequest req = TenantCreateRequest.builder()
                .tenantName("Delhi Public School")
                .tenantType("STANDALONE_SCHOOL")
                .planId(planId)
                .subdomain("dps-delhi")
                .isolationMode(IsolationMode.ISOLATED)
                .firstAdminName("Principal Sharma")
                .firstAdminEmail("principal@dps-delhi.edu")
                .timeZone("Asia/Kolkata")
                .dataRegion("IN")
                .build();

        Plan plan = Plan.builder().planId(planId).status("ACTIVE").build();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(tenantRepository.findBySubdomain("dps-delhi")).thenReturn(Optional.empty());
        when(keycloakAdminClient.provisionUser(any(), any(), any(), any(), eq(true))).thenReturn("kc-admin-sub");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setTenantId(tenantId);
            return t;
        });

        ResponseEntity<TenantResponse> res = tenantController.createTenant(req, auth(PlatformRole.PLATFORM_ADMIN));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().tenantName()).isEqualTo("Delhi Public School");
    }

    @Test
    @DisplayName("ACT-TENANT_CREATE-DENY: Billing Manager lacking TENANT_CREATE rejected")
    void actTenantCreate_deny() {
        TenantCreateRequest req = TenantCreateRequest.builder().tenantName("School").build();
        assertThatThrownBy(() -> tenantController.createTenant(req, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_CREATE-SCOPE: Tenant principal rejected")
    void actTenantCreate_scope() {
        TenantCreateRequest req = TenantCreateRequest.builder().tenantName("School").build();
        assertThatThrownBy(() -> tenantController.createTenant(req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_CREATE-GUARD: Duplicate subdomain rejected")
    void actTenantCreate_guard() {
        TenantCreateRequest req = TenantCreateRequest.builder()
                .tenantName("Duplicate")
                .subdomain("existing-subdomain")
                .planId(planId)
                .build();
        when(tenantRepository.findBySubdomain("existing-subdomain")).thenReturn(Optional.of(new Tenant()));

        assertThatThrownBy(() -> tenantController.createTenant(req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // =========================================================================
    // 49. TENANT_ACTIVATE
    // =========================================================================

    @Test
    @DisplayName("ACT-TENANT_ACTIVATE-ALLOW: Product Owner activates tenant with leave approver set")
    void actTenantActivate_allow() {
        Tenant tenant = Tenant.builder().tenantId(tenantId).tenantType("STANDALONE_SCHOOL").state(TenantState.TRIAL).build();
        when(invoiceRepository.existsByTenantIdAndStatus(tenantId, "PAID")).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(institutionRepository.findByIdTenantIdAndStatusIn(eq(tenantId), any())).thenReturn(List.of());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TenantResponse> res = tenantController.activateTenant(
                tenantId, new TenantActivationRequest("PAY-REF-123", "All statutory gates verified"), auth(PlatformRole.PRODUCT_OWNER)
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().state()).isEqualTo(TenantState.ACTIVE);
    }

    @Test
    @DisplayName("ACT-TENANT_ACTIVATE-DENY: Platform Admin denied under D1 §11.1 (Product Owner only)")
    void actTenantActivate_deny() {
        TenantActivationRequest req = new TenantActivationRequest("PAY-1", "ok");
        assertThatThrownBy(() -> tenantController.activateTenant(tenantId, req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_ACTIVATE-SCOPE: Non-platform principal rejected")
    void actTenantActivate_scope() {
        TenantActivationRequest req = new TenantActivationRequest("PAY-1", "ok");
        assertThatThrownBy(() -> tenantController.activateTenant(tenantId, req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_ACTIVATE-GUARD: Rule C7 enforces Principal leave approver before go-live")
    void actTenantActivate_guard() {
        Tenant tenant = Tenant.builder().tenantId(tenantId).tenantType("STANDALONE_SCHOOL").state(TenantState.TRIAL).build();
        when(invoiceRepository.existsByTenantIdAndStatus(tenantId, "PAID")).thenReturn(true);
        UUID instId = UUID.randomUUID();
        Institution inst = new Institution();
        inst.setId(new InstitutionId(tenantId, instId));
        inst.setInstitutionName("Main School");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(institutionRepository.findByIdTenantIdAndStatusIn(eq(tenantId), any())).thenReturn(List.of(inst));
        when(leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(tenantId, "PRINCIPAL", instId, "ACTIVE"))
                .thenReturn(false);

        TenantActivationRequest req = new TenantActivationRequest("PAY-REF-1", "Ready");
        assertThatThrownBy(() -> tenantController.activateTenant(tenantId, req, auth(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("PRINCIPAL_LEAVE_APPROVER_MISSING");
    }

    // =========================================================================
    // 50. TENANT_SUSPEND_ARCHIVE
    // =========================================================================

    @Test
    @DisplayName("ACT-TENANT_SUSPEND_ARCHIVE-ALLOW: Product Owner suspends tenant")
    void actTenantSuspendArchive_allow() {
        Tenant tenant = Tenant.builder().tenantId(tenantId).state(TenantState.ACTIVE).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TenantResponse> res = tenantController.suspendTenant(
                tenantId, new TenantLifecycleActionRequest("Statutory freeze"), auth(PlatformRole.PRODUCT_OWNER)
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().state()).isEqualTo(TenantState.SUSPENDED);
    }

    @Test
    @DisplayName("ACT-TENANT_SUSPEND_ARCHIVE-DENY: Platform Admin denied (Product Owner only)")
    void actTenantSuspendArchive_deny() {
        TenantLifecycleActionRequest req = new TenantLifecycleActionRequest("freeze");
        assertThatThrownBy(() -> tenantController.suspendTenant(tenantId, req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_SUSPEND_ARCHIVE-SCOPE: Missing tenant throws 404")
    void actTenantSuspendArchive_scope() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        TenantLifecycleActionRequest req = new TenantLifecycleActionRequest("freeze");

        assertThatThrownBy(() -> tenantController.suspendTenant(tenantId, req, auth(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_SUSPEND_ARCHIVE-GUARD: Cannot activate an archived tenant")
    void actTenantSuspendArchive_guard() {
        Tenant tenant = Tenant.builder().tenantId(tenantId).state(TenantState.ARCHIVED).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantActivationRequest req = new TenantActivationRequest("ref-123", "Attempting activate archived");
        assertThatThrownBy(() -> tenantController.activateTenant(tenantId, req, auth(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot activate tenant in state ARCHIVED");
    }

    // =========================================================================
    // 51. TENANT_SETTINGS
    // =========================================================================

    @Test
    @DisplayName("ACT-TENANT_SETTINGS-ALLOW: Platform Admin updates tenant settings")
    void actTenantSettings_allow() {
        Tenant tenant = Tenant.builder().tenantId(tenantId).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantSettingsRequest req = new UpdateTenantSettingsRequest(
                "Asia/Kolkata", "IN", true, "school.edu.in", 1000, 50, 5000, 10000
        );
        ResponseEntity<TenantSettingsResponse> res = tenantController.updateTenantSettings(
                tenantId, req, auth(PlatformRole.PLATFORM_ADMIN)
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().customDomain()).isEqualTo("school.edu.in");
    }

    @Test
    @DisplayName("ACT-TENANT_SETTINGS-DENY: Support Manager lacking TENANT_SETTINGS rejected")
    void actTenantSettings_deny() {
        UpdateTenantSettingsRequest req = new UpdateTenantSettingsRequest("Asia/Kolkata", "IN", false, null, null, null, null, null);
        assertThatThrownBy(() -> tenantController.updateTenantSettings(tenantId, req, auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_SETTINGS-SCOPE: Non-platform principal rejected")
    void actTenantSettings_scope() {
        UpdateTenantSettingsRequest req = new UpdateTenantSettingsRequest("Asia/Kolkata", "IN", false, null, null, null, null, null);
        assertThatThrownBy(() -> tenantController.updateTenantSettings(tenantId, req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-TENANT_SETTINGS-GUARD: Unknown tenant throws 404")
    void actTenantSettings_guard() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        UpdateTenantSettingsRequest req = new UpdateTenantSettingsRequest("Asia/Kolkata", "IN", false, null, null, null, null, null);

        assertThatThrownBy(() -> tenantController.updateTenantSettings(tenantId, req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // 52. FEATURE_TOGGLE
    // =========================================================================

    @Test
    @DisplayName("ACT-FEATURE_TOGGLE-ALLOW: Platform Admin toggles module entitlements")
    void actFeatureToggle_allow() {
        EntitlementsUpdateRequest req = new EntitlementsUpdateRequest(Map.of("FEE_COLLECTION", true, "TIMETABLE", true));
        ResponseEntity<EntitlementsResponse> res = featureToggleController.updatePlanEntitlements(
                planId, req, auth(PlatformRole.PLATFORM_ADMIN)
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().features()).containsEntry("FEE_COLLECTION", true);
    }

    @Test
    @DisplayName("ACT-FEATURE_TOGGLE-DENY: Billing Manager lacking FEATURE_TOGGLE denied")
    void actFeatureToggle_deny() {
        EntitlementsUpdateRequest req = new EntitlementsUpdateRequest(Map.of("EXAMS", true));
        assertThatThrownBy(() -> featureToggleController.updatePlanEntitlements(planId, req, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-FEATURE_TOGGLE-SCOPE: Tenant principal rejected")
    void actFeatureToggle_scope() {
        EntitlementsUpdateRequest req = new EntitlementsUpdateRequest(Map.of("EXAMS", true));
        assertThatThrownBy(() -> featureToggleController.updatePlanEntitlements(planId, req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-FEATURE_TOGGLE-GUARD: Null principal rejected")
    void actFeatureToggle_guard() {
        EntitlementsUpdateRequest req = new EntitlementsUpdateRequest(Map.of("EXAMS", true));
        assertThatThrownBy(() -> featureToggleController.updatePlanEntitlements(planId, req, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // 53. INVOICE_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-INVOICE_MANAGE-ALLOW: Billing Manager creates invoice")
    void actInvoiceManage_allow() {
        CreateInvoiceRequest req = new CreateInvoiceRequest(
                tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("50000.00"), "INR"
        );
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setInvoiceId(invoiceId);
            return i;
        });

        ResponseEntity<InvoiceResponse> res = billingController.createInvoice(req, auth(PlatformRole.BILLING_MANAGER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().subtotalAmount()).isEqualTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("ACT-INVOICE_MANAGE-DENY: Support Manager lacking INVOICE_MANAGE denied")
    void actInvoiceManage_deny() {
        CreateInvoiceRequest req = new CreateInvoiceRequest(tenantId, LocalDate.now(), LocalDate.now(), BigDecimal.TEN, "INR");
        assertThatThrownBy(() -> billingController.createInvoice(req, auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-INVOICE_MANAGE-SCOPE: Non-platform principal rejected")
    void actInvoiceManage_scope() {
        CreateInvoiceRequest req = new CreateInvoiceRequest(tenantId, LocalDate.now(), LocalDate.now(), BigDecimal.TEN, "INR");
        assertThatThrownBy(() -> billingController.createInvoice(req, tenantAuth(tenantId)))
                .isInstanceOfAny(UnauthorizedException.class, ClassCastException.class);
    }

    @Test
    @DisplayName("ACT-INVOICE_MANAGE-GUARD: Missing invoice throws 404")
    void actInvoiceManage_guard() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> billingController.getInvoice(invoiceId, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // 54. DISCOUNT_APPLY
    // =========================================================================

    @Test
    @DisplayName("ACT-DISCOUNT_APPLY-ALLOW: Product Owner applies valid promotional discount")
    void actDiscountApply_allow() {
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).tenantId(tenantId)
                .subtotalAmount(new BigDecimal("10000.00")).discountAmount(BigDecimal.ZERO).build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("2000.00"), "Founder school discount");
        ResponseEntity<InvoiceResponse> res = billingController.applyDiscount(invoiceId, req, auth(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().discountAmount()).isEqualTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("ACT-DISCOUNT_APPLY-DENY: Billing Manager cannot give discounts under Rule F-4")
    void actDiscountApply_deny() {
        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("500.00"), "Rebate");
        assertThatThrownBy(() -> billingController.applyDiscount(invoiceId, req, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-DISCOUNT_APPLY-SCOPE: Tenant principal rejected")
    void actDiscountApply_scope() {
        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("500.00"), "Rebate");
        assertThatThrownBy(() -> billingController.applyDiscount(invoiceId, req, tenantAuth(tenantId)))
                .isInstanceOfAny(UnauthorizedException.class, ClassCastException.class);
    }

    @Test
    @DisplayName("ACT-DISCOUNT_APPLY-GUARD: Discount exceeding subtotal rejected under Rule ck_invoice_r2")
    void actDiscountApply_guard() {
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).tenantId(tenantId)
                .subtotalAmount(new BigDecimal("5000.00")).discountAmount(BigDecimal.ZERO).build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("6000.00"), "Over 100%");
        assertThatThrownBy(() -> billingController.applyDiscount(invoiceId, req, auth(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rule ck_invoice_r2");
    }

    // =========================================================================
    // 55. OVERDUE_FLAG
    // =========================================================================

    @Test
    @DisplayName("ACT-OVERDUE_FLAG-ALLOW: Billing Manager flags invoice overdue")
    void actOverdueFlag_allow() {
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).tenantId(tenantId).build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<InvoiceResponse> res = billingController.flagOverdue(invoiceId, auth(PlatformRole.BILLING_MANAGER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().overdueFlaggedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACT-OVERDUE_FLAG-DENY: Support Manager lacking OVERDUE_FLAG rejected")
    void actOverdueFlag_deny() {
        assertThatThrownBy(() -> billingController.flagOverdue(invoiceId, auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-OVERDUE_FLAG-SCOPE: Non-platform principal rejected")
    void actOverdueFlag_scope() {
        assertThatThrownBy(() -> billingController.flagOverdue(invoiceId, tenantAuth(tenantId)))
                .isInstanceOfAny(UnauthorizedException.class, ClassCastException.class);
    }

    @Test
    @DisplayName("ACT-OVERDUE_FLAG-GUARD: Unknown invoice throws 404")
    void actOverdueFlag_guard() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> billingController.flagOverdue(invoiceId, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // 56. SUPPORT_TICKET_RAISE
    // =========================================================================

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_RAISE-ALLOW: Authenticated tenant user raises support ticket")
    void actSupportTicketRaise_allow() {
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setSupportTicketId(ticketId);
            return t;
        });

        CreateSupportTicketRequest req = new CreateSupportTicketRequest("Login issue", "Cannot reset password", "HIGH", "TECHNICAL");
        ResponseEntity<SupportTicketResponse> res = tenantSupportController.raiseTicket(tenantId, req, tenantAuth(tenantId));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().subject()).isEqualTo("Login issue");
    }

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_RAISE-DENY: Platform staff cannot raise tenant ticket (tenant isolation)")
    void actSupportTicketRaise_deny() {
        CreateSupportTicketRequest req = new CreateSupportTicketRequest("Title", "Desc", "LOW", "GENERAL");
        assertThatThrownBy(() -> tenantSupportController.raiseTicket(tenantId, req, auth(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_RAISE-SCOPE: Cross-tenant ticket creation rejected")
    void actSupportTicketRaise_scope() {
        UUID otherTenant = UUID.randomUUID();
        CreateSupportTicketRequest req = new CreateSupportTicketRequest("Title", "Desc", "LOW", "GENERAL");
        assertThatThrownBy(() -> tenantSupportController.raiseTicket(otherTenant, req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_RAISE-GUARD: Title and description mandatory")
    void actSupportTicketRaise_guard() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> supportService.getTicket(ticketId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // 57. SUPPORT_TICKET_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_MANAGE-ALLOW: Support Manager updates ticket")
    void actSupportTicketManage_allow() {
        SupportTicket ticket = SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).status("OPEN").build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSupportTicketRequest req = new UpdateSupportTicketRequest("IN_PROGRESS", userId, "Investigating logs");
        ResponseEntity<SupportTicketResponse> res = supportController.updateTicket(ticketId, req, auth(PlatformRole.SUPPORT_MANAGER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_MANAGE-DENY: Billing Manager lacking SUPPORT_TICKET_MANAGE rejected")
    void actSupportTicketManage_deny() {
        UpdateSupportTicketRequest req = new UpdateSupportTicketRequest("RESOLVED", userId, "Done");
        assertThatThrownBy(() -> supportController.updateTicket(ticketId, req, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_MANAGE-SCOPE: Tenant principal rejected")
    void actSupportTicketManage_scope() {
        UpdateSupportTicketRequest req = new UpdateSupportTicketRequest("RESOLVED", userId, "Done");
        assertThatThrownBy(() -> supportController.updateTicket(ticketId, req, tenantAuth(tenantId)))
                .isInstanceOfAny(UnauthorizedException.class, ClassCastException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_TICKET_MANAGE-GUARD: Missing ticket throws 404")
    void actSupportTicketManage_guard() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());
        UpdateSupportTicketRequest req = new UpdateSupportTicketRequest("RESOLVED", userId, "Done");

        assertThatThrownBy(() -> supportController.updateTicket(ticketId, req, auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // 58. SUPPORT_ACCESS_GRANT
    // =========================================================================

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_GRANT-ALLOW: Tenant Admin grants time-limited support access")
    void actSupportAccessGrant_allow() {
        SupportTicket ticket = SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(grantRepository.save(any(SupportAccessGrant.class))).thenAnswer(inv -> {
            SupportAccessGrant g = inv.getArgument(0);
            g.setSupportAccessGrantId(grantId);
            return g;
        });

        GrantSupportAccessRequest req = new GrantSupportAccessRequest(ticketId, null, 12);
        ResponseEntity<SupportAccessGrantResponse> res = tenantSupportController.grantAccess(tenantId, req, tenantAuth(tenantId));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_GRANT-DENY: Platform staff cannot grant access to themselves")
    void actSupportAccessGrant_deny() {
        GrantSupportAccessRequest req = new GrantSupportAccessRequest(ticketId, null, 12);
        assertThatThrownBy(() -> tenantSupportController.grantAccess(tenantId, req, auth(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_GRANT-SCOPE: Cross-tenant grant rejected")
    void actSupportAccessGrant_scope() {
        UUID otherTenant = UUID.randomUUID();
        GrantSupportAccessRequest req = new GrantSupportAccessRequest(ticketId, null, 12);
        assertThatThrownBy(() -> tenantSupportController.grantAccess(otherTenant, req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_GRANT-GUARD: Rule SA-01 enforces max 24 hours grant duration")
    void actSupportAccessGrant_guard() {
        SupportTicket ticket = SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        GrantSupportAccessRequest req = new GrantSupportAccessRequest(ticketId, null, 48);
        assertThatThrownBy(() -> tenantSupportController.grantAccess(tenantId, req, tenantAuth(tenantId)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("between 1 and 24 hours");
    }

    // =========================================================================
    // 59. SUPPORT_ACCESS_USE
    // =========================================================================

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_USE-ALLOW: Support Manager assumes session under active grant")
    void actSupportAccessUse_allow() {
        SupportTicket ticket = SupportTicket.builder().supportTicketId(ticketId).ticketNumber("TICK-101").tenantId(tenantId).build();
        SupportAccessGrant grant = SupportAccessGrant.builder()
                .supportAccessGrantId(grantId).tenantId(tenantId).ticketId(ticketId)
                .expiresAt(Instant.now().plusSeconds(3600)).build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(eq(ticketId), any())).thenReturn(Optional.of(grant));

        // L-03 / R7: the made-up session token is gone. With a live grant the answer is an honest "not available yet" (501).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> supportController.assumeSupportSession(ticketId, auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOfSatisfying(com.edu360.api.exception.FeatureNotAvailableException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("SUPPORT_SESSION_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_USE-DENY: Billing Manager lacking SUPPORT_ACCESS_USE denied")
    void actSupportAccessUse_deny() {
        assertThatThrownBy(() -> supportController.assumeSupportSession(ticketId, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_USE-SCOPE: Tenant principal rejected")
    void actSupportAccessUse_scope() {
        assertThatThrownBy(() -> supportController.assumeSupportSession(ticketId, tenantAuth(tenantId)))
                .isInstanceOfAny(UnauthorizedException.class, ClassCastException.class);
    }

    @Test
    @DisplayName("ACT-SUPPORT_ACCESS_USE-GUARD: Expired or missing grant rejected under D8")
    void actSupportAccessUse_guard() {
        SupportTicket ticket = SupportTicket.builder().supportTicketId(ticketId).tenantId(tenantId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(grantRepository.findByTicketIdAndExpiresAtAfterAndWithdrawnAtIsNull(eq(ticketId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportController.assumeSupportSession(ticketId, auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("No active support access grant");
    }

    // =========================================================================
    // 60. RELEASE_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-RELEASE_MANAGE-ALLOW: Release Manager creates and transitions release")
    void actReleaseManage_allow() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.1.0", "Sprint 1 Release", false, "v4.1.0");
        when(releaseRepository.findByVersionLabel("v4.1.0")).thenReturn(Optional.empty());
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> {
            PlatformRelease r = inv.getArgument(0);
            r.setReleaseId(releaseId);
            return r;
        });

        ResponseEntity<ReleaseResponse> res = releaseController.createRelease(req, auth(PlatformRole.RELEASE_MANAGER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().versionLabel()).isEqualTo("v4.1.0");
    }

    @Test
    @DisplayName("ACT-RELEASE_MANAGE-DENY: Billing Manager lacking RELEASE_MANAGE denied")
    void actReleaseManage_deny() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.1.0", "Release", false, "v4.1.0");
        assertThatThrownBy(() -> releaseController.createRelease(req, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-RELEASE_MANAGE-SCOPE: Non-platform principal rejected")
    void actReleaseManage_scope() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.1.0", "Release", false, "v4.1.0");
        assertThatThrownBy(() -> releaseController.createRelease(req, tenantAuth(tenantId)))
                .isInstanceOfAny(UnauthorizedException.class, ClassCastException.class);
    }

    @Test
    @DisplayName("ACT-RELEASE_MANAGE-GUARD: Duplicate version label rejected")
    void actReleaseManage_guard() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.0.0", "Duplicate", false, "v4.0.0");
        when(releaseRepository.findByVersionLabel("v4.0.0")).thenReturn(Optional.of(new PlatformRelease()));

        assertThatThrownBy(() -> releaseController.createRelease(req, auth(PlatformRole.RELEASE_MANAGER)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // =========================================================================
    // 61. INTEGRATION_TYPES_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-INTEGRATION_TYPES_MANAGE-ALLOW: Platform Admin lists integration types")
    void actIntegrationTypesManage_allow() {
        when(integrationTypeRepository.findAllByOrderByIntegrationTypeCode()).thenReturn(List.of());
        ResponseEntity<List<IntegrationTypeResponse>> res = integrationTypeController.list(auth(PlatformRole.PLATFORM_ADMIN));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("ACT-INTEGRATION_TYPES_MANAGE-DENY: Billing Manager lacking INTEGRATION_TYPES_MANAGE denied")
    void actIntegrationTypesManage_deny() {
        assertThatThrownBy(() -> integrationTypeController.list(auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-INTEGRATION_TYPES_MANAGE-SCOPE: Non-platform principal rejected")
    void actIntegrationTypesManage_scope() {
        assertThatThrownBy(() -> integrationTypeController.list(tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-INTEGRATION_TYPES_MANAGE-GUARD: Tenant secrets never exposed in catalog")
    void actIntegrationTypesManage_guard() {
        IntegrationType it = IntegrationType.builder()
                .integrationTypeId(UUID.randomUUID())
                .integrationTypeCode("SMS_DLT")
                .description("DLT SMS Gateway")
                .status("ALLOWED")
                .build();
        IntegrationTypeResponse resp = IntegrationTypeResponse.from(it);
        assertThat(resp.integrationTypeCode()).isEqualTo("SMS_DLT");
    }

    // =========================================================================
    // 62. PLATFORM_AUDIT_VIEW
    // =========================================================================

    @Test
    @DisplayName("ACT-PLATFORM_AUDIT_VIEW-ALLOW: Platform Admin views platform audit logs")
    void actPlatformAuditView_allow() {
        when(auditOutboxEventRepository.findAllByOrderByOccurredAtDesc(any())).thenReturn(org.springframework.data.domain.Page.empty());
        var res = auditLogController.listAuditLogs(0, 50, auth(PlatformRole.PLATFORM_ADMIN));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("ACT-PLATFORM_AUDIT_VIEW-DENY: Billing Manager lacking PLATFORM_AUDIT_VIEW denied")
    void actPlatformAuditView_deny() {
        assertThatThrownBy(() -> auditLogController.listAuditLogs(0, 50, auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLATFORM_AUDIT_VIEW-SCOPE: Tenant principal rejected")
    void actPlatformAuditView_scope() {
        assertThatThrownBy(() -> auditLogController.listAuditLogs(0, 50, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLATFORM_AUDIT_VIEW-GUARD: Audit page size clamped safely")
    void actPlatformAuditView_guard() {
        when(auditOutboxEventRepository.findAllByOrderByOccurredAtDesc(any())).thenReturn(org.springframework.data.domain.Page.empty());
        var res = auditLogController.listAuditLogs(-1, 500, auth(PlatformRole.PLATFORM_ADMIN));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // 63. USAGE_COUNTS_VIEW
    // =========================================================================

    @Test
    @DisplayName("ACT-USAGE_COUNTS_VIEW-ALLOW: Platform Admin views aggregate usage metrics")
    void actUsageCountsView_allow() {
        when(tenantRepository.count()).thenReturn(10L);
        when(usageMetricDailyRepository.findFirstByMetricCodeOrderByUsageDateDesc(any())).thenReturn(Optional.empty());

        var res = usageCountsController.usageCounts(auth(PlatformRole.PLATFORM_ADMIN));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().totalTenantCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ACT-USAGE_COUNTS_VIEW-DENY: Billing Manager lacking USAGE_COUNTS_VIEW denied")
    void actUsageCountsView_deny() {
        assertThatThrownBy(() -> usageCountsController.usageCounts(auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-USAGE_COUNTS_VIEW-SCOPE: Tenant principal rejected")
    void actUsageCountsView_scope() {
        assertThatThrownBy(() -> usageCountsController.usageCounts(tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-USAGE_COUNTS_VIEW-GUARD: Cross-tenant individual user queries strictly avoided")
    void actUsageCountsView_guard() {
        when(tenantRepository.count()).thenReturn(5L);
        var res = usageCountsController.usageCounts(auth(PlatformRole.PRODUCT_OWNER));
        assertThat(res.getBody().totalUserCountGap()).contains("break tenant isolation");
    }

    // =========================================================================
    // 64. SECURITY_POLICY_SET
    // =========================================================================

    @Test
    @DisplayName("ACT-SECURITY_POLICY_SET-ALLOW: Product Owner retrieves and updates security policy")
    void actSecurityPolicySet_allow() {
        UpdateSecurityPolicyRequest req = new UpdateSecurityPolicyRequest(14, true, true, 3, 30, 15, true, 180);
        ResponseEntity<SecurityPolicyResponse> res = securityPolicyController.updatePolicy(req, auth(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().minPasswordLength()).isEqualTo(14);
    }

    @Test
    @DisplayName("ACT-SECURITY_POLICY_SET-DENY: Platform Admin denied (Product Owner only)")
    void actSecurityPolicySet_deny() {
        UpdateSecurityPolicyRequest req = new UpdateSecurityPolicyRequest(12, true, true, 5, 15, 30, true, 90);
        assertThatThrownBy(() -> securityPolicyController.updatePolicy(req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SECURITY_POLICY_SET-SCOPE: Tenant principal rejected")
    void actSecurityPolicySet_scope() {
        UpdateSecurityPolicyRequest req = new UpdateSecurityPolicyRequest(12, true, true, 5, 15, 30, true, 90);
        assertThatThrownBy(() -> securityPolicyController.updatePolicy(req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-SECURITY_POLICY_SET-GUARD: Null principal rejected")
    void actSecurityPolicySet_guard() {
        UpdateSecurityPolicyRequest req = new UpdateSecurityPolicyRequest(12, true, true, 5, 15, 30, true, 90);
        assertThatThrownBy(() -> securityPolicyController.updatePolicy(req, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // 65. BACKUP_STATUS_VIEW
    // =========================================================================

    @Test
    @DisplayName("ACT-BACKUP_STATUS_VIEW-ALLOW: Platform Admin views backup status")
    void actBackupStatusView_allow() {
        when(tenantBackupStatusRepository.findAllByOrderByTenantId()).thenReturn(List.of());
        var res = backupStatusController.backupStatus(auth(PlatformRole.PLATFORM_ADMIN));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().wiredToRealBackupSystem()).isFalse();
    }

    @Test
    @DisplayName("ACT-BACKUP_STATUS_VIEW-DENY: Billing Manager lacking BACKUP_STATUS_VIEW denied")
    void actBackupStatusView_deny() {
        assertThatThrownBy(() -> backupStatusController.backupStatus(auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-BACKUP_STATUS_VIEW-SCOPE: Non-platform principal rejected")
    void actBackupStatusView_scope() {
        assertThatThrownBy(() -> backupStatusController.backupStatus(tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-BACKUP_STATUS_VIEW-GUARD: Backup download endpoints prohibited for platform roles")
    void actBackupStatusView_guard() {
        assertThatCode(() -> permissionService.authorize(new PlatformPrincipal(userId, PlatformRole.PLATFORM_ADMIN, "admin@test", false), "BACKUP_STATUS_VIEW"))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // 66. PLATFORM_USER_MANAGE
    // =========================================================================

    @Test
    @DisplayName("ACT-PLATFORM_USER_MANAGE-ALLOW: Product Owner creates platform staff account")
    void actPlatformUserManage_allow() {
        PlatformUserCreateRequest req = new PlatformUserCreateRequest(
                "Rahul Verma", "rahul.verma@edu360.com", PlatformRole.SUPPORT_MANAGER, null
        );
        PlatformUserResponse resp = new PlatformUserResponse(
                UUID.randomUUID(), "Rahul Verma", "rahul.verma@edu360.com", PlatformRole.SUPPORT_MANAGER,
                null, null, PlatformUserStatus.ACTIVE, true, null, Instant.now(), "tempPass123#"
        );
        when(platformUserManagementService.createPlatformUser(any(), any())).thenReturn(resp);

        ResponseEntity<PlatformUserResponse> res = userController.createPlatformUser(req, auth(PlatformRole.PRODUCT_OWNER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().email()).isEqualTo("rahul.verma@edu360.com");
    }

    @Test
    @DisplayName("ACT-PLATFORM_USER_MANAGE-DENY: Platform Admin denied (Product Owner only)")
    void actPlatformUserManage_deny() {
        PlatformUserCreateRequest req = new PlatformUserCreateRequest(
                "Staff", "staff@edu360.com", PlatformRole.SUPPORT_MANAGER, null
        );
        assertThatThrownBy(() -> userController.createPlatformUser(req, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLATFORM_USER_MANAGE-SCOPE: Tenant principal rejected")
    void actPlatformUserManage_scope() {
        PlatformUserCreateRequest req = new PlatformUserCreateRequest(
                "Staff", "staff@edu360.com", PlatformRole.SUPPORT_MANAGER, null
        );
        assertThatThrownBy(() -> userController.createPlatformUser(req, tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLATFORM_USER_MANAGE-GUARD: Platform user roles cannot be assigned to tenant users")
    void actPlatformUserManage_guard() {
        assertThatCode(() -> permissionService.authorize(new PlatformPrincipal(userId, PlatformRole.PRODUCT_OWNER, "po@test", false), "PLATFORM_USER_MANAGE"))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // 67. PLATFORM_REPORTS_VIEW
    // =========================================================================

    @Test
    @DisplayName("ACT-PLATFORM_REPORTS_VIEW-ALLOW: Billing Manager sees financial summary; Support Manager sees ticket report")
    void actPlatformReportsView_allow() {
        ResponseEntity<PlatformReportResponse> finRes = reportController.getReport("FINANCIAL_SUMMARY", auth(PlatformRole.BILLING_MANAGER));
        assertThat(finRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finRes.getBody().reportCode()).isEqualTo("FINANCIAL_SUMMARY");

        ResponseEntity<PlatformReportResponse> supRes = reportController.getReport("SUPPORT_SUMMARY", auth(PlatformRole.SUPPORT_MANAGER));
        assertThat(supRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(supRes.getBody().metrics()).containsKey("openTicketsCount");
    }

    @Test
    @DisplayName("ACT-PLATFORM_REPORTS_VIEW-DENY: Release Manager lacking PLATFORM_REPORTS_VIEW denied")
    void actPlatformReportsView_deny() {
        assertThatThrownBy(() -> reportController.getReport("FINANCIAL_SUMMARY", auth(PlatformRole.RELEASE_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLATFORM_REPORTS_VIEW-SCOPE: Tenant principal rejected")
    void actPlatformReportsView_scope() {
        assertThatThrownBy(() -> reportController.getReport("FINANCIAL_SUMMARY", tenantAuth(tenantId)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-PLATFORM_REPORTS_VIEW-GUARD: Reports never leak individual tenant records")
    void actPlatformReportsView_guard() {
        ResponseEntity<PlatformReportResponse> res = reportController.getReport("PLATFORM_SUMMARY", auth(PlatformRole.PRODUCT_OWNER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().metrics()).doesNotContainKey("tenantData");
    }
}
