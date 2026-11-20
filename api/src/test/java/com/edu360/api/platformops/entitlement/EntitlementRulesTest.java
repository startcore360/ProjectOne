package com.edu360.api.platformops.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/** E2-S2 acceptance: MODULE_OFF, MODULE_DEPENDENCY, and tenant overrides beyond the plan are Product Owner only. */
class EntitlementRulesTest {

    private static final Map<String, Set<String>> REQUIRES = Map.of("RESULTS", Set.of("EXAMINATIONS"));

    private static Map<String, Boolean> state(Object... pairs) {
        Map<String, Boolean> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], (Boolean) pairs[i + 1]);
        return m;
    }

    @Test
    void aFeatureCannotBeOnWhileItsModuleIsOff() {
        Map<String, Boolean> change = state("FEES:FEES_ONLINE_PAYMENT", true);
        Map<String, Boolean> result = state("FEES", false, "FEES:FEES_ONLINE_PAYMENT", true);
        assertThatThrownBy(() -> EntitlementRules.validate(result, change, REQUIRES))
                .isInstanceOf(ApiException.class).hasMessageStartingWith("MODULE_OFF");
    }

    @Test
    void aModuleCannotBeTurnedOffWhileADependentModuleIsOn_andTheMessageNamesTheDependant() {
        Map<String, Boolean> change = state("EXAMINATIONS", false);
        Map<String, Boolean> result = state("EXAMINATIONS", false, "RESULTS", true);
        assertThatThrownBy(() -> EntitlementRules.validate(result, change, REQUIRES))
                .isInstanceOf(ApiException.class).hasMessageStartingWith("MODULE_DEPENDENCY: RESULTS");
    }

    @Test
    void aModuleCannotBeTurnedOnWithoutWhatItRequires() {
        Map<String, Boolean> change = state("RESULTS", true);
        assertThatThrownBy(() -> EntitlementRules.validate(state("RESULTS", true), change, REQUIRES))
                .isInstanceOf(ApiException.class).hasMessageContaining("needs EXAMINATIONS");
    }

    @Test
    void validChangesPass() {
        Map<String, Boolean> change = state("EXAMINATIONS", true, "RESULTS", true, "FEES", true, "FEES:FEES_ONLINE_PAYMENT", true);
        assertThatCode(() -> EntitlementRules.validate(change, change, REQUIRES)).doesNotThrowAnyException();
    }

    @Test
    void aPlatformAdminCannotOverrideATenantBeyondItsPlan_theProductOwnerCan() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(Tenant.builder().tenantId(tenantId).planId(planId).build()));
        PlanEntitlementRepository planRows = mock(PlanEntitlementRepository.class);
        when(planRows.findByPlanId(planId)).thenReturn(List.of());
        TenantEntitlementRepository tenantRows = mock(TenantEntitlementRepository.class);
        when(tenantRows.findByTenantId(tenantId)).thenReturn(List.of());
        PlatformAuditWriter audit = mock(PlatformAuditWriter.class);
        PlatformFeatureToggleController controller = new PlatformFeatureToggleController(
                mock(PlatformPermissionService.class), planRows, tenantRows, tenants, audit);
        var request = new PlatformFeatureToggleController.EntitlementsUpdateRequest(Map.of("LIBRARY", true));

        assertThatThrownBy(() -> controller.updateTenantEntitlements(tenantId, request, auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(ApiException.class).hasMessageContaining("only the Product Owner");
        verify(audit).record(any(), eq(tenantId), eq("CONFIG_CHANGE"), eq("UPDATE"), eq("FEATURE_TOGGLE"), eq("TENANT"),
                eq(tenantId), eq("DENIED"), anyMap());
        verify(tenantRows, never()).save(any());

        assertThat(controller.updateTenantEntitlements(tenantId, request, auth(PlatformRole.PRODUCT_OWNER)).getStatusCode().value())
                .isEqualTo(200);
        verify(tenantRows).save(any());
    }

    private static Authentication auth(PlatformRole role) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new PlatformPrincipal(UUID.randomUUID(), role, "x@x.test", false));
        return auth;
    }
}
