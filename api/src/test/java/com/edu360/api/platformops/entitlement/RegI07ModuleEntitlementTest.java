package com.edu360.api.platformops.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.edu360.api.auth.SessionSettingsController;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.GlobalExceptionHandler;
import com.edu360.api.exception.ModuleNotEnabledException;
import com.edu360.api.identity.UserType;
import com.edu360.api.platformops.security.SecurityPolicyService;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sprint 1 B3 / I-07: a module switched off for a tenant blocks its endpoints (403 MODULE_NOT_ENABLED) before
 * the permission decision, and the session settings the menu reads list it. The interceptor is the same one
 * ModuleEntitlementEnforcer wraps around PermissionEvaluatorService.authorize; the entitlement rows are real
 * TenantEntitlement objects returned by a mocked repository.
 */
class RegI07ModuleEntitlementTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String ACTION = "STUDENT_ADMIT";
    private static final String MODULE = "ADMISSIONS";

    private final TenantEntitlementRepository entitlements = mock(TenantEntitlementRepository.class);
    private final ModuleEntitlementService service = new ModuleEntitlementService(
            entitlements, null, mock(TenantRepository.class), Map.of(ACTION, MODULE));

    private void tenantHasModule(boolean enabled) {
        TenantEntitlement row = TenantEntitlement.builder().tenantEntitlementId(UUID.randomUUID())
                .tenantId(TENANT_ID).moduleCode(MODULE).featureCode(null).enabled(enabled).build();
        when(entitlements.findByTenantId(TENANT_ID)).thenReturn(List.of(row));
    }

    @SuppressWarnings("unchecked")
    private Object authorizeCall(MethodInvocation[] holder) throws Throwable {
        ObjectProvider<ModuleEntitlementService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(service);
        MethodInterceptor check = ModuleEntitlementEnforcer.entitlementCheck(provider);
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(PermissionEvaluatorService.class.getMethod(
                "authorize", RbacPrincipal.class, String.class, RbacScope.class, RbacTarget.class));
        when(invocation.getArguments()).thenReturn(new Object[] {new RbacPrincipal(TENANT_ID, UUID.randomUUID()), ACTION, null, null});
        when(invocation.proceed()).thenReturn("permission decision made");
        holder[0] = invocation;
        return check.invoke(invocation);
    }

    @Test
    @DisplayName("REG-I07-1: disable a module and its endpoint gives 403 MODULE_NOT_ENABLED")
    void regI07_1_disabledModuleGives403() throws Throwable {
        tenantHasModule(false);
        MethodInvocation[] holder = new MethodInvocation[1];

        assertThatThrownBy(() -> authorizeCall(holder)).isInstanceOf(ModuleNotEnabledException.class);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tenants/" + TENANT_ID + "/students");
        ResponseEntity<Map<String, Object>> response = new GlobalExceptionHandler()
                .handleModuleNotEnabled(new ModuleNotEnabledException(MODULE), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "MODULE_NOT_ENABLED").containsEntry("moduleCode", MODULE);
    }

    @Test
    @DisplayName("REG-I07-2: enable the module and the call goes through to the permission check")
    void regI07_2_enabledModuleGoesThrough() throws Throwable {
        tenantHasModule(true);
        MethodInvocation[] holder = new MethodInvocation[1];

        Object result = authorizeCall(holder);

        assertThat(result).isEqualTo("permission decision made");
        verify(holder[0]).proceed();
    }

    @Test
    @DisplayName("REG-I07-3 (server half): the session settings the menu reads list the disabled module")
    void regI07_3_sessionSettingsListDisabledModule() {
        tenantHasModule(false);
        SessionSettingsController controller = new SessionSettingsController(new SecurityPolicyService(null), service);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, UUID.randomUUID(), UserType.STAFF, "Admin", false);

        var response = controller.sessionSettings(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(response.getBody().disabledModules()).containsExactly(MODULE);
        assertThat(response.getBody().idleTimeoutMinutes()).isEqualTo(30);
    }
}
