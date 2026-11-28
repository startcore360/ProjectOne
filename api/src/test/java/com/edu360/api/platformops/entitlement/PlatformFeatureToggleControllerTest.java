package com.edu360.api.platformops.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class PlatformFeatureToggleControllerTest {

    @Test
    void readsPlanFeatureEntitlements() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PlatformPrincipal principal = new PlatformPrincipal(userId, PlatformRole.PLATFORM_ADMIN, "admin@example.test", false);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        PlatformPermissionService permissions = mock(PlatformPermissionService.class);
        PlanEntitlementRepository plans = mock(PlanEntitlementRepository.class);
        TenantEntitlementRepository tenants = mock(TenantEntitlementRepository.class);
        TenantRepository tenantDirectory = mock(TenantRepository.class);
        when(plans.findByPlanId(planId)).thenReturn(List.of(PlanEntitlement.builder()
                .planEntitlementId(UUID.randomUUID()).planId(planId).moduleCode("ATTENDANCE")
                .featureCode("BIOMETRIC").enabled(true).createdAt(Instant.now()).createdBy(userId)
                .updatedAt(Instant.now()).updatedBy(userId).rowVersion(1).build()));

        PlatformFeatureToggleController controller = new PlatformFeatureToggleController(
                permissions, plans, tenants, tenantDirectory, mock(PlatformAuditWriter.class));
        ResponseEntity<PlatformFeatureToggleController.EntitlementsResponse> response =
                controller.getPlanEntitlements(planId, auth);

        assertThat(response.getBody().features()).containsEntry("ATTENDANCE:BIOMETRIC", true);
        assertThat(response.getBody().targetType()).isEqualTo("PLAN");
        verify(permissions).authorize(principal, "FEATURE_TOGGLE");
    }

    @Test
    void readsTenantFeatureEntitlements() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PlatformPrincipal principal = new PlatformPrincipal(userId, PlatformRole.PRODUCT_OWNER, "owner@example.test", false);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        PlatformPermissionService permissions = mock(PlatformPermissionService.class);
        PlanEntitlementRepository plans = mock(PlanEntitlementRepository.class);
        TenantEntitlementRepository tenants = mock(TenantEntitlementRepository.class);
        TenantRepository tenantDirectory = mock(TenantRepository.class);
        UUID planId = UUID.randomUUID();
        when(tenantDirectory.findById(tenantId)).thenReturn(java.util.Optional.of(Tenant.builder()
                .tenantId(tenantId).planId(planId).state(TenantState.ACTIVE).build()));
        when(plans.findByPlanId(planId)).thenReturn(List.of(PlanEntitlement.builder()
                .planEntitlementId(UUID.randomUUID()).planId(planId).moduleCode("ATTENDANCE")
                .featureCode("BIOMETRIC").enabled(true).createdAt(Instant.now()).createdBy(userId)
                .updatedAt(Instant.now()).updatedBy(userId).rowVersion(1).build()));
        when(tenants.findByTenantId(tenantId)).thenReturn(List.of(TenantEntitlement.builder()
                .tenantEntitlementId(UUID.randomUUID()).tenantId(tenantId).moduleCode("ATTENDANCE")
                .featureCode("BIOMETRIC").enabled(false).createdAt(Instant.now()).createdBy(userId)
                .updatedAt(Instant.now()).updatedBy(userId).rowVersion(1).build()));

        PlatformFeatureToggleController controller = new PlatformFeatureToggleController(
                permissions, plans, tenants, tenantDirectory, mock(PlatformAuditWriter.class));
        ResponseEntity<PlatformFeatureToggleController.TenantEntitlementsResponse> response =
                controller.getTenantEntitlements(tenantId, auth);

        assertThat(response.getBody().features()).containsEntry("ATTENDANCE:BIOMETRIC", false);
        assertThat(response.getBody().plan()).containsEntry("ATTENDANCE:BIOMETRIC", true);
        assertThat(response.getBody().override()).containsEntry("ATTENDANCE:BIOMETRIC", false);
        assertThat(response.getBody().effective()).containsEntry("ATTENDANCE:BIOMETRIC", false);
        assertThat(response.getBody().targetType()).isEqualTo("TENANT");
        verify(permissions).authorize(principal, "FEATURE_TOGGLE");
    }
}
