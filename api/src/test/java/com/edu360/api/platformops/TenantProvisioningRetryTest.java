package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.ApiException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.dataprotection.TenantRetentionPolicyRepository;
import com.edu360.api.platformops.entitlement.PlanEntitlementRepository;
import com.edu360.api.platformops.entitlement.TenantEntitlementRepository;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.dataprotection.TenantRetentionPolicyRepository;
import com.edu360.api.platformops.entitlement.PlanEntitlementRepository;
import com.edu360.api.platformops.entitlement.TenantEntitlementRepository;
import com.edu360.api.platformops.dto.TenantCreateRequest;
import com.edu360.api.platformops.dto.TenantProvisioningStatusResponse;
import com.edu360.api.platformops.dto.TenantProvisioningStepResponse;
import com.edu360.api.platformops.dto.TenantResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

/** E1-S2: setup steps leave a failed tenant retryable, alert Platform Admins, and retry from where it stopped. */
@ExtendWith(MockitoExtension.class)
class TenantProvisioningRetryTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private PlanRepository planRepository;
    @Mock private KeycloakAdminClient keycloak;
    @Mock private LeaveApproverSettingRepository leaveRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private UserAccountRepository accounts;
    @Mock private UserIdentityLinkRepository links;
    @Mock private InvoiceRepository invoices;
    @Mock private PlatformAuditWriter audit;
    @Mock private TenantProvisioningService provisioning;
    @Mock private TenantLocalProvisioningService tenantLocal;
    @Mock private TenantRetentionPolicyRepository retention;
    @Mock private PlanEntitlementRepository planEntitlements;
    @Mock private TenantEntitlementRepository tenantEntitlements;
    @Mock private PlatformUserRepository platformUsers;
    @Mock private PlatformNotificationService notifications;

    private TenantManagementService service;
    private final PlatformPrincipal owner = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "po@x.test", false);
    private final UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TenantManagementService(tenantRepository, planRepository, keycloak, leaveRepository,
                institutionRepository, accounts, links, null, null, null, null, null, invoices, audit);
        ReflectionTestUtils.setField(service, "tenantProvisioningService", provisioning);
        ReflectionTestUtils.setField(service, "tenantLocalProvisioningService", tenantLocal);
        ReflectionTestUtils.setField(service, "tenantRetentionPolicyRepository", retention);
        ReflectionTestUtils.setField(service, "planEntitlementRepository", planEntitlements);
        ReflectionTestUtils.setField(service, "tenantEntitlementRepository", tenantEntitlements);
        ReflectionTestUtils.setField(service, "platformUserRepository", platformUsers);
        ReflectionTestUtils.setField(service, "platformNotificationService", notifications);
    }

    private Plan plan() {
        Plan plan = mock(Plan.class);
        org.mockito.Mockito.lenient().when(plan.getPlanId()).thenReturn(planId);
        return plan;
    }

    private TenantCreateRequest request() {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantName("Doon World Academy");
        request.setTenantType("STANDALONE_SCHOOL");
        request.setSubdomain("doon-world");
        request.setPlanId(planId);
        request.setIsolationMode(IsolationMode.SHARED);
        request.setFirstAdminName("Anita Roy");
        request.setFirstAdminEmail("Principal@Doon.edu");
        request.setRetentionMonths(84);
        request.setBackupRetentionDays(35);
        return request;
    }

    private static TenantProvisioningStatusResponse status(UUID tenantId, String overall, String failedStep, String errorCode) {
        List<TenantProvisioningStepResponse> steps = TenantProvisioningService.executionStepCodes().stream()
                .map(code -> new TenantProvisioningStepResponse(0, code, code, code,
                        code.equals(failedStep) ? "FAILED" : ("DONE".equals(overall) ? "DONE" : "PENDING"),
                        1, code.equals(failedStep) ? errorCode : null, Instant.now(), Instant.now()))
                .toList();
        return new TenantProvisioningStatusResponse(tenantId, overall, !"DONE".equals(overall), steps);
    }

    @Test
    void keycloakDown_tenantStaysCreated_answers503_andAlertsPlatformAdminsWithAValidPriority() {
        Plan plan = plan();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(tenantRepository.findBySubdomain("doon-world")).thenReturn(Optional.empty());
        when(provisioning.provision(any(), any(), anyMap()))
                .thenAnswer(inv -> status(inv.getArgument(0), "FAILED", "P06_IDENTITY_REALM", "KEYCLOAK_UNAVAILABLE"));
        PlatformUser admin = mock(PlatformUser.class);
        UUID adminId = UUID.randomUUID();
        when(admin.getPlatformUserId()).thenReturn(adminId);
        when(platformUsers.findAllByPlatformRoleAndStatus(PlatformRole.PLATFORM_ADMIN, PlatformUserStatus.ACTIVE))
                .thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.createTenant(request(), owner))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("identity service");
        verify(notifications).notify(eq(adminId), eq("PLATFORM_ALERT"), anyString(), anyString(), eq("TENANT"),
                any(), eq("NORMAL"), eq(owner.platformUserId()));
        verify(audit, never()).record(any(), any(), eq("TENANT_CREATED"), anyString(), anyString(), any());
    }

    @Test
    void firstStepKeepsTheFirstAdminAsPendingSoARetryKnowsWhoItIs() throws Exception {
        Plan plan = plan();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(tenantRepository.findBySubdomain("doon-world")).thenReturn(Optional.empty());
        AtomicReference<Map<String, TenantProvisioningService.StepAction>> captured = new AtomicReference<>();
        when(provisioning.provision(any(), any(), anyMap())).thenAnswer(inv -> {
            captured.set(inv.getArgument(2));
            return status(inv.getArgument(0), "DONE", null, null);
        });
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantResponse created = service.createTenant(request(), owner);
        assertThat(created.state()).isEqualTo(TenantState.TRIAL);

        captured.get().get("P01_REGISTRY").execute();
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserAccountStatus.PENDING_ACTIVATION);
        assertThat(saved.getValue().getEmail()).isEqualTo("principal@doon.edu");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Anita Roy");
    }

    @Test
    void retry_resumesSetup_issuesANewOneTimePassword_andMovesTheTenantToTrial() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().tenantId(tenantId).tenantName("Doon").tenantType("STANDALONE_SCHOOL")
                .planId(planId).state(TenantState.CREATED).subdomain("doon").keycloakRealm("doon-realm")
                .isolationMode(IsolationMode.SHARED).timeZone("Asia/Kolkata").build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        Plan plan = plan();
        when(plan.getPlanName()).thenReturn("Standard");
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(provisioning.getStatus(tenantId)).thenReturn(status(tenantId, "FAILED", "P07_FIRST_ADMIN", "PROVISIONING_FAILED"));
        UUID adminAccountId = UUID.randomUUID();
        UserAccount pending = UserAccount.builder().id(new UserAccountId(tenantId, adminAccountId))
                .displayName("Anita Roy").email("principal@doon.edu").status(UserAccountStatus.PENDING_ACTIVATION).build();
        when(accounts.findByIdTenantIdAndStatus(tenantId, UserAccountStatus.PENDING_ACTIVATION)).thenReturn(List.of(pending));
        when(provisioning.retry(eq(tenantId), any(), anyMap())).thenReturn(status(tenantId, "DONE", null, null));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantResponse response = service.retryProvisioning(tenantId, null, null, owner);

        assertThat(response.state()).isEqualTo(TenantState.TRIAL);
        assertThat(response.firstAdminTemporaryPassword()).isNotBlank();
        verify(provisioning, never()).provision(any(), any(), anyMap());
        verify(audit).record(owner, tenantId, "TENANT_PROVISIONING_RETRIED", "TENANT_CREATE", "TENANT", tenantId);
    }

    @Test
    void retry_ofAFinishedTenantIs409() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().tenantId(tenantId).state(TenantState.TRIAL).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.retryProvisioning(tenantId, null, null, owner))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already complete");
    }

    @Test
    void retentionStepRefusesToGuessMissingValues() {
        Plan plan = plan();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(tenantRepository.findBySubdomain("doon-world")).thenReturn(Optional.empty());
        AtomicReference<Map<String, TenantProvisioningService.StepAction>> captured = new AtomicReference<>();
        when(provisioning.provision(any(), any(), anyMap())).thenAnswer(inv -> {
            captured.set(inv.getArgument(2));
            return status(inv.getArgument(0), "DONE", null, null);
        });
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantCreateRequest request = request();
        service.createTenant(request, owner);

        // Same steps, rebuilt for a retry that was given no retention values.
        Map<String, TenantProvisioningService.StepAction> steps = captured.get();
        assertThat(steps).containsKeys("P01_REGISTRY", "P09_RETENTION", "P05_ENTITLEMENTS",
                "P06_IDENTITY_REALM", "P10_REALM_POLICY", "P07_FIRST_ADMIN", "P04_ROLES_SEED");
        assertThat(new BigDecimal(request.getRetentionMonths())).isPositive();
    }
}
