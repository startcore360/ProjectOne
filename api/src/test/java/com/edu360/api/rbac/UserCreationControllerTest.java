package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
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
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.rbac.dto.CreateUserCheckRequest;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;
import com.edu360.api.rbac.dto.CreateUserRequest;
import com.edu360.api.rbac.dto.CreateUserResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * Action & Guard tests for USER_CREATE (Sprint 1 Story #9 / 12.3, 13.1, MX-13):
 * - ACT-USER_CREATE-ALLOW
 * - ACT-USER_CREATE-DENY
 * - ACT-USER_CREATE-SCOPE
 * - ACT-USER_CREATE-GUARD (duplicate email, plan limits, Keycloak provisioning)
 */
class UserCreationControllerTest {

    private UserAccountRepository userAccountRepository;
    private UserIdentityLinkRepository userIdentityLinkRepository;
    private TenantRepository tenantRepository;
    private PlanRepository planRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private KeycloakAdminClient keycloakAdminClient;
    private RoleAssignmentService roleAssignmentService;
    private AuditLogService auditLogService;
    private UserAccountService userAccountService;
    private UserCreationController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        userIdentityLinkRepository = mock(UserIdentityLinkRepository.class);
        tenantRepository = mock(TenantRepository.class);
        planRepository = mock(PlanRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        keycloakAdminClient = mock(KeycloakAdminClient.class);
        roleAssignmentService = mock(RoleAssignmentService.class);
        auditLogService = mock(AuditLogService.class);

        userAccountService = new UserAccountService(
                userAccountRepository,
                userIdentityLinkRepository,
                tenantRepository,
                planRepository,
                permissionEvaluatorService,
                keycloakAdminClient,
                roleAssignmentService,
                auditLogService
        );

        controller = new UserCreationController(userAccountService, permissionEvaluatorService);
    }

    private Authentication authenticatedTenantUser(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "College Admin",
                false
        );
        return new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList());
    }

    @Test
    @DisplayName("ACT-USER_CREATE-ALLOW: Authorized Admin creates staff account with Keycloak credentials")
    void actUserCreate_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        CreateUserRequest req = new CreateUserRequest(
                UserType.STAFF,
                "Anita Sharma",
                "anita.sharma@northbridge.edu.in",
                "+919876543210",
                "4589",
                "TEACHER",
                ScopeType.INSTITUTION,
                INSTITUTION_ID,
                null
        );

        when(userAccountRepository.existsByIdTenantIdAndEmail(TENANT_ID, "anita.sharma@northbridge.edu.in")).thenReturn(false);
        when(userAccountRepository.countByIdTenantIdAndStatusNot(TENANT_ID, UserAccountStatus.DEACTIVATED)).thenReturn(10L);

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).planId(PLAN_ID).keycloakRealm("northbridge-realm").build();
        Plan plan = Plan.builder().planId(PLAN_ID).planName("Standard Plan").userLimit(100).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        when(keycloakAdminClient.provisionUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("kc-user-uuid-123");

        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userIdentityLinkRepository.save(any(UserIdentityLink.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<CreateUserResponse> response = controller.createUser(TENANT_ID, req, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().displayName()).isEqualTo("Anita Sharma");
        assertThat(response.getBody().email()).isEqualTo("anita.sharma@northbridge.edu.in");
        assertThat(response.getBody().userType()).isEqualTo(UserType.STAFF);
        assertThat(response.getBody().status()).isEqualTo(UserAccountStatus.PENDING_ACTIVATION);
        assertThat(response.getBody().temporaryPassword()).isNotBlank();
        assertThat(response.getBody().initialRole()).isEqualTo("TEACHER");

        // Verify Keycloak provisioning
        verify(keycloakAdminClient).provisionUser(
                eq("northbridge-realm"),
                eq("Anita Sharma"),
                eq("anita.sharma@northbridge.edu.in"),
                anyString(),
                eq(true)
        );

        // Verify RBAC authorization check
        verify(permissionEvaluatorService).authorize(
                eq(new RbacPrincipal(TENANT_ID, ADMIN_USER_ID)),
                eq("USER_CREATE"),
                eq(RbacScope.institution(INSTITUTION_ID)),
                eq(RbacTarget.NONE)
        );
    }

    @Test
    @DisplayName("ACT-USER_CREATE-DENY: Role lacking USER_CREATE (or Principal under MX-13) is rejected with 403")
    void actUserCreate_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        CreateUserRequest req = new CreateUserRequest(
                UserType.STAFF, "John Doe", "john.doe@test.edu", null, null, null, ScopeType.TENANT, null, null
        );

        doThrow(new UnauthorizedException("The active role does not hold action USER_CREATE (Principal never creates users [MX-13])."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("USER_CREATE"), any(), any());

        assertThatThrownBy(() -> controller.createUser(TENANT_ID, req, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("USER_CREATE");
    }

    @Test
    @DisplayName("ACT-USER_CREATE-SCOPE: Foreign tenant in URL path is rejected with 403")
    void actUserCreate_scope() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        CreateUserRequest req = new CreateUserRequest(
                UserType.STAFF, "John Doe", "john.doe@test.edu", null, null, null, ScopeType.TENANT, null, null
        );

        assertThatThrownBy(() -> controller.createUser(foreignTenantId, req, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-USER_CREATE-GUARD: Duplicate email within tenant throws 409 Conflict")
    void actUserCreate_guard_duplicateEmail() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        CreateUserRequest req = new CreateUserRequest(
                UserType.STAFF, "Anita Sharma", "anita.sharma@northbridge.edu.in", null, null, null, ScopeType.TENANT, null, null
        );

        when(userAccountRepository.existsByIdTenantIdAndEmail(TENANT_ID, "anita.sharma@northbridge.edu.in"))
                .thenReturn(true);

        assertThatThrownBy(() -> controller.createUser(TENANT_ID, req, auth))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("ACT-USER_CREATE-GUARD: User plan limit reached throws PLAN_LIMIT_REACHED (WF-14)")
    void actUserCreate_guard_planLimitReached() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        CreateUserRequest req = new CreateUserRequest(
                UserType.STAFF, "Anita Sharma", "new.user@northbridge.edu.in", null, null, null, ScopeType.TENANT, null, null
        );

        when(userAccountRepository.countByIdTenantIdAndStatusNot(TENANT_ID, UserAccountStatus.DEACTIVATED)).thenReturn(50L);

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).planId(PLAN_ID).build();
        Plan plan = Plan.builder().planId(PLAN_ID).planName("Starter Plan").userLimit(50).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.createUser(TENANT_ID, req, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("PLAN_LIMIT_REACHED");
                    assertThat(bre.getMessage()).contains("WF-14");
                });
    }

    @Test
    @DisplayName("ACT-USER_CREATE-CHECK: Backward compatibility check endpoint returns allowed")
    void actUserCreate_check() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        CreateUserCheckRequest req = new CreateUserCheckRequest(ScopeType.TENANT, null, null);

        ResponseEntity<CreateUserCheckResponse> response = controller.checkCreateUser(TENANT_ID, req, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().actionCode()).isEqualTo("USER_CREATE");
        assertThat(response.getBody().reasonCode()).isEqualTo("ALLOWED");
    }
}
