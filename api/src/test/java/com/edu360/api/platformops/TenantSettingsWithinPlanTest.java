package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.ApiException;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.dto.TenantSettingsResponse;
import com.edu360.api.platformops.dto.UpdateTenantSettingsRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/** E1-S3 (WF-06, R2-14): limits above the plan are Product Owner only; custom domains need the plan. */
@ExtendWith(MockitoExtension.class)
class TenantSettingsWithinPlanTest {

    @Mock private TenantRepository tenants;
    @Mock private PlanRepository plans;
    @Mock private KeycloakAdminClient keycloak;
    @Mock private LeaveApproverSettingRepository leave;
    @Mock private InstitutionRepository institutions;
    @Mock private InvoiceRepository invoices;
    @Mock private PlatformAuditWriter audit;

    private TenantManagementService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private Tenant tenant;

    private final PlatformPrincipal admin = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN, "pa@x.test", false);
    private final PlatformPrincipal owner = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "po@x.test", false);

    @BeforeEach
    void setUp() {
        service = new TenantManagementService(tenants, plans, keycloak, leave, institutions, null, null, null, null,
                null, null, null, invoices, audit);
        tenant = Tenant.builder().tenantId(tenantId).tenantName("Doon").planId(planId).userLimit(1000)
                .storageLimitGb(50).build();
        Plan plan = Plan.builder().planId(planId).planName("Standard").userLimit(1000).storageLimitGb(50)
                .customDomainAllowed(false).build();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(plans.findById(planId)).thenReturn(Optional.of(plan));
    }

    private static UpdateTenantSettingsRequest users(int limit) {
        return new UpdateTenantSettingsRequest(null, null, null, null, limit, null, null, null);
    }

    @Test
    void platformAdminCannotGoAboveThePlan_andTheRefusalIsAudited() {
        assertThatThrownBy(() -> service.updateTenantSettings(tenantId, users(1500), admin))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only the Product Owner");
        verify(audit).record(eq(admin), eq(tenantId), eq("TENANT_SETTINGS_UPDATED"), eq("UPDATE"),
                eq("TENANT_SETTINGS"), eq("TENANT"), eq(tenantId), eq("DENIED"), anyMap());
        verify(tenants, never()).save(any());
    }

    @Test
    void platformAdminCanChangeWithinThePlan() {
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantSettingsResponse saved = service.updateTenantSettings(tenantId, users(800), admin);
        assertThat(saved.userLimit()).isEqualTo(800);
        assertThat(saved.planUserLimit()).isEqualTo(1000);
    }

    @SuppressWarnings("unchecked")
    @Test
    void productOwnerCanGoAboveThePlan_auditedWithOldAndNewValues() {
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantSettingsResponse saved = service.updateTenantSettings(tenantId, users(1500), owner);

        assertThat(saved.userLimit()).isEqualTo(1500);
        ArgumentCaptor<Map<String, Map<String, Object>>> changes = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(owner), eq(tenantId), eq("TENANT_SETTINGS_UPDATED"), eq("UPDATE"),
                eq("TENANT_SETTINGS"), eq("TENANT"), eq(tenantId), eq(PlatformAuditWriter.SUCCESS), changes.capture());
        assertThat(changes.getValue().get("userLimit")).containsEntry("old", 1000).containsEntry("new", 1500);
    }

    @Test
    void aValueTheProductOwnerAlreadyRaisedCanBeSavedUnchangedOrLoweredByThePlatformAdmin() {
        tenant.setUserLimit(1500);
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.updateTenantSettings(tenantId, users(1500), admin).userLimit()).isEqualTo(1500);
        assertThat(service.updateTenantSettings(tenantId, users(1200), admin).userLimit()).isEqualTo(1200);
    }

    @Test
    void aCustomDomainNeedsAPlanThatIncludesIt_evenForTheProductOwner() {
        UpdateTenantSettingsRequest domain = new UpdateTenantSettingsRequest(null, null, null, "portal.doon.edu",
                null, null, null, null);
        assertThatThrownBy(() -> service.updateTenantSettings(tenantId, domain, owner))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not include a custom domain");
    }
}
