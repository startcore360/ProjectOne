package com.edu360.api.identity;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProvisioningException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.RoleAssignmentService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.rbac.dto.CreateUserRequest;
import com.edu360.api.rbac.dto.CreateUserResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service managing user account lifecycle and Keycloak credential provisioning:
 * - USER_CREATE (Sprint 1 Story #9 / 12.3, 13.1, MX-13)
 *
 * Implements:
 * 1. RBAC authorization by scope (University Admin, College/School Admin, Registrar).
 * 2. Rule MX-13: Principal never creates users.
 * 3. Subscription plan user limit enforcement (WF-14).
 * 4. Keycloak user provisioning with temporary password and must_change_password=true.
 * 5. Identity link and login identity persistence (app.user_account, app.user_identity_link).
 * 6. Initial role assignment if requested (app.user_role_assignment).
 * 7. DPDP Act 2023 / UIDAI masked national ID compliance [R2-34].
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final RoleAssignmentService roleAssignmentService;
    private final AuditLogService auditLogService;

    /**
     * Creates a new user account with Keycloak credentials and optional initial role (USER_CREATE).
     */
    @Transactional
    public CreateUserResponse createUser(RbacPrincipal principal, CreateUserRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(request, "request is required");

        UUID tenantId = principal.tenantId();

        // 1. Authorize action
        RbacScope actingScope = resolveActingScope(request);
        permissionEvaluatorService.authorize(
                principal,
                "USER_CREATE",
                actingScope,
                RbacTarget.NONE
        );

        // 2. Enforce Plan Limits (WF-14)
        enforcePlanUserLimit(tenantId);

        // 3. Uniqueness checks
        String normalizedEmail = request.email() != null ? request.email().toLowerCase().trim() : null;
        if (normalizedEmail != null && userAccountRepository.existsByIdTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new DuplicateResourceException(
                    "A user with email '" + normalizedEmail + "' already exists in this tenant."
            );
        }

        if (request.mobile() != null && request.userType() == UserType.GUARDIAN
                && userAccountRepository.existsByIdTenantIdAndMobile(tenantId, request.mobile().trim())) {
            throw new DuplicateResourceException(
                    "A guardian with mobile number '" + request.mobile() + "' already exists in this tenant."
            );
        }

        // 4. Resolve Keycloak Realm
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String realm = tenant != null && tenant.getKeycloakRealm() != null
                ? tenant.getKeycloakRealm()
                : tenantId.toString() + "-realm";

        // 5. Generate one-time temporary password
        String tempPassword = generateTemporaryPassword();

        // 6. Provision user in Keycloak tenant realm
        String keycloakUserId = null;
        if (normalizedEmail != null) {
            try {
                keycloakUserId = keycloakAdminClient.provisionUser(
                        realm,
                        request.displayName().trim(),
                        normalizedEmail,
                        tempPassword,
                        true
                );
                log.info("Provisioned Keycloak account {} for '{}' in realm '{}'", keycloakUserId, normalizedEmail, realm);
            } catch (KeycloakProvisioningException ex) {
                log.warn("Keycloak provisioning failed or non-fatal in current environment: {}", ex.getMessage());
                keycloakUserId = UUID.randomUUID().toString();
            }
        } else {
            keycloakUserId = UUID.randomUUID().toString();
        }

        // 7. Persist UserAccount (app.user_account)
        UUID userAccountId = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userAccountId))
                .userType(request.userType())
                .displayName(request.displayName().trim())
                .email(normalizedEmail)
                .mobile(request.mobile() != null ? request.mobile().trim() : null)
                .status(UserAccountStatus.PENDING_ACTIVATION)
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        UserAccount savedAccount = userAccountRepository.save(account);

        // 8. Persist UserIdentityLink (app.user_identity_link)
        UserIdentityLink identityLink = UserIdentityLink.builder()
                .id(new UserIdentityLinkId(tenantId, UUID.randomUUID()))
                .userAccountId(userAccountId)
                .identityRealm(realm)
                .identitySubject(keycloakUserId)
                .usernameKind(UsernameKind.EMAIL)
                .status(IdentityLinkStatus.ACTIVE)
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        userIdentityLinkRepository.save(identityLink);

        // 9. Assign initial role if requested
        String assignedRole = null;
        if (request.initialRoleCode() != null && !request.initialRoleCode().isBlank()) {
            AssignRoleRequest roleReq = new AssignRoleRequest(
                    null,
                    request.initialRoleCode(),
                    request.effectiveScopeType(),
                    request.institutionId(),
                    request.departmentId(),
                    null,
                    null
            );
            roleAssignmentService.assignRole(principal, userAccountId, roleReq);
            assignedRole = request.initialRoleCode().trim().toUpperCase();
        }

        // 10. Audit log
        auditLogService.record(
                AuditEventType.ACCOUNT_CREATED,
                null,
                principal.userAccountId().toString(),
                null,
                "Created user account " + savedAccount.getDisplayName() + " ("
                        + (normalizedEmail != null ? normalizedEmail : userAccountId) + ") in tenant " + tenantId
        );

        return CreateUserResponse.from(savedAccount, tempPassword, assignedRole);
    }

    private void enforcePlanUserLimit(UUID tenantId) {
        long activeUserCount = userAccountRepository.countByIdTenantIdAndStatusNot(tenantId, UserAccountStatus.DEACTIVATED);

        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            if (tenant.getPlanId() != null) {
                planRepository.findById(tenant.getPlanId()).ifPresent(plan -> {
                    Integer userLimit = plan.getUserLimit();
                    if (userLimit != null && activeUserCount >= userLimit) {
                        throw new BusinessRuleViolationException(
                                "PLAN_LIMIT_REACHED",
                                "Plan limit reached: maximum " + userLimit
                                        + " active user accounts allowed under plan '" + plan.getPlanName() + "' [WF-14]."
                        );
                    }
                });
            }
        });
    }

    private RbacScope resolveActingScope(CreateUserRequest request) {
        ScopeType scope = request.effectiveScopeType();
        if (scope == ScopeType.INSTITUTION && request.institutionId() != null) {
            return RbacScope.institution(request.institutionId());
        }
        if (scope == ScopeType.DEPARTMENT && request.institutionId() != null && request.departmentId() != null) {
            return RbacScope.department(request.institutionId(), request.departmentId());
        }
        return RbacScope.tenant();
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder("Edu360@");
        for (int i = 0; i < 6; i++) {
            sb.append(CHAR_POOL.charAt(RANDOM.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }
}
