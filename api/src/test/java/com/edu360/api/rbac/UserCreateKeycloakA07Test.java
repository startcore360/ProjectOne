package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProvisioningException;
import com.edu360.api.exception.GlobalExceptionHandler;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.rbac.dto.CreateUserRequest;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

/**
 * A-07 (BS-21): when Keycloak fails during user creation the request fails with 503 and NOTHING
 * is saved - no made-up identity subject, no app.user_account and no app.user_identity_link row.
 * Real PermissionEvaluatorService (College Admin holds USER_CREATE in ref.standard_role_action).
 */
class UserCreateKeycloakA07Test {

    private RbacTestWorld world;
    private UserAccountService service;
    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final UUID collegeA = UUID.randomUUID();
    private UUID admin;

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(world.tenantId)).thenReturn(Optional.of(world.tenant));
        service = new UserAccountService(world.userAccountRepository, world.userIdentityLinkRepository, tenants,
                mock(PlanRepository.class), world.evaluator, keycloak, mock(RoleAssignmentService.class), world.auditLogService);
        admin = world.user("College Admin A");
        world.assign(admin, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        assertThat(RbacTestWorld.standardRoleHolds("COLLEGE_ADMIN", "USER_CREATE")).isTrue();
        world.signIn(admin);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CreateUserRequest request() {
        return new CreateUserRequest(UserType.STAFF, "New Teacher", "new.teacher@test.edu", null, null, null,
                ScopeType.INSTITUTION, collegeA, null);
    }

    private void assertNothingSaved(int accountsBefore) {
        assertThat(world.accounts).hasSize(accountsBefore);
        verify(world.userAccountRepository, never()).save(any());
        verify(world.userIdentityLinkRepository, never()).save(any());
        assertThat(world.auditRows).noneMatch(r -> r.getEventType() == AuditEventType.ACCOUNT_CREATED);
    }

    @Test
    @DisplayName("REG-A07-1: Keycloak down gives 503 and no user_account or identity link row")
    void regA07_1_keycloakDown() {
        when(keycloak.provisionUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new KeycloakProvisioningException("Connection refused"));
        int before = world.accounts.size();

        assertThatThrownBy(() -> service.createUser(world.principal(admin), request()))
                .isInstanceOf(KeycloakProvisioningException.class)
                .satisfies(ex -> {
                    ResponseEntity<Map<String, Object>> mapped = new GlobalExceptionHandler()
                            .handleKeycloakProvisioning((KeycloakProvisioningException) ex, new MockHttpServletRequest());
                    assertThat(mapped.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        assertNothingSaved(before);
    }

    @Test
    @DisplayName("REG-A07-2: Keycloak returns no ID gives 503 and nothing saved")
    void regA07_2_keycloakReturnsNoId() {
        when(keycloak.provisionUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(null);
        int before = world.accounts.size();

        assertThatThrownBy(() -> service.createUser(world.principal(admin), request()))
                .isInstanceOf(KeycloakProvisioningException.class);
        assertNothingSaved(before);

        when(keycloak.provisionUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("  ");
        assertThatThrownBy(() -> service.createUser(world.principal(admin), request()))
                .isInstanceOf(KeycloakProvisioningException.class);
        assertNothingSaved(before);
    }
}
