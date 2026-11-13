package com.edu360.api.identity;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        // 4. Resolve the tenant's own Keycloak realm. No made-up realm name (R7): a tenant without a
        // realm cannot sign anyone in, so the account is not created.
        String realm = tenantRepository.findById(tenantId)
                .map(Tenant::getKeycloakRealm)
                .filter(r -> !r.isBlank())
                .orElseThrow(() -> new KeycloakProvisioningException(
                        "Tenant " + tenantId + " has no sign-in realm, so no account can be provisioned."));

        // Sign-in identities are e-mail based (UsernameKind.EMAIL); an account with no e-mail could
        // never sign in, and making up an identity subject for it is forbidden (A-07, R7).
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("email is required: every account needs a sign-in identity.");
        }

        // 5. Generate one-time temporary password
        String tempPassword = generateTemporaryPassword();

        // 6. Provision the user in the tenant's Keycloak realm BEFORE anything is saved. If Keycloak is
        // down or returns no id, the request fails with 503 (KeycloakProvisioningException) and no
        // user_account or user_identity_link row is written (A-07, BS-21). The exception propagates.
        String keycloakUserId = keycloakAdminClient.provisionUser(
                realm,
                request.displayName().trim(),
                normalizedEmail,
                tempPassword,
                true
        );
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new KeycloakProvisioningException("Keycloak created no account id for this user; nothing was saved.");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        try {
                            keycloakAdminClient.deleteUser(realm, keycloakUserId);
                        } catch (RuntimeException cleanupFailure) {
                            log.error("Could not remove orphan staff/user identity {} from realm {} after transaction rollback",
                                    keycloakUserId, realm, cleanupFailure);
                        }
                    }
                }
            });
        }
        log.info("Provisioned Keycloak account {} in realm '{}'", keycloakUserId, realm);

        // 7. Persist UserAccount (app.user_account)
        UUID userAccountId = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userAccountId))
                .userType(request.userType())
                .displayName(request.displayName().trim())
                .email(normalizedEmail)
                .mobile(request.mobile() != null ? request.mobile().trim() : null)
                .status(UserAccountStatus.PENDING_ACTIVATION)
                // B3 / I-05 (R2-35, PD-1): a student's printed first-login password stops working 7 days after issue.
                .credentialExpiresAt(com.edu360.api.auth.PrintedPasswordPolicy.expiryFor(request.userType(), java.time.Instant.now()))
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

    /**
     * Creates a student login using the institution-generated student code as the Keycloak
     * username. Students do not need an email address; the identity link records STUDENT_CODE.
     */
    @Transactional
    public CreateUserResponse createStudentAccount(RbacPrincipal principal, String displayName,
            String studentCode, UUID institutionId) {
        Objects.requireNonNull(principal, "principal is required");
        if (displayName == null || displayName.isBlank() || studentCode == null || studentCode.isBlank()
                || institutionId == null) {
            throw new IllegalArgumentException("Student name, student code and institution are required.");
        }
        UUID tenantId = principal.tenantId();
        permissionEvaluatorService.authorize(principal, "USER_CREATE",
                RbacScope.institution(institutionId), RbacTarget.NONE);
        enforcePlanUserLimit(tenantId);

        String realm = tenantRepository.findById(tenantId)
                .map(Tenant::getKeycloakRealm)
                .filter(r -> !r.isBlank())
                .orElseThrow(() -> new KeycloakProvisioningException(
                        "Tenant " + tenantId + " has no sign-in realm, so no account can be provisioned."));

        String tempPassword = generateTemporaryPassword();
        String keycloakUserId = keycloakAdminClient.provisionStudentUser(
                realm, displayName.trim(), studentCode.trim(), tempPassword, true);
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new KeycloakProvisioningException("Keycloak created no student account id; nothing was saved.");
        }
        // Keycloak is outside the database transaction. If the enclosing student/profile/enrolment
        // transaction rolls back, remove the external identity so no orphan login remains.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        try {
                            keycloakAdminClient.deleteUser(realm, keycloakUserId);
                        } catch (RuntimeException cleanupFailure) {
                            log.error("Could not remove orphan student identity {} from realm {} after transaction rollback",
                                    keycloakUserId, realm, cleanupFailure);
                        }
                    }
                }
            });
        }

        UUID userAccountId = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userAccountId))
                .userType(UserType.STUDENT)
                .displayName(displayName.trim())
                .email(null)
                .mobile(null)
                .status(UserAccountStatus.PENDING_ACTIVATION)
                .credentialExpiresAt(com.edu360.api.auth.PrintedPasswordPolicy.expiryFor(
                        UserType.STUDENT, java.time.Instant.now()))
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();
        UserAccount saved = userAccountRepository.save(account);

        userIdentityLinkRepository.save(UserIdentityLink.builder()
                .id(new UserIdentityLinkId(tenantId, UUID.randomUUID()))
                .userAccountId(userAccountId)
                .identityRealm(realm)
                .identitySubject(keycloakUserId)
                .usernameKind(UsernameKind.STUDENT_CODE)
                .status(IdentityLinkStatus.ACTIVE)
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build());

        roleAssignmentService.assignRole(principal, userAccountId,
                new AssignRoleRequest(null, "STUDENT", ScopeType.INSTITUTION,
                        institutionId, null, null, null));

        auditLogService.record(AuditEventType.ACCOUNT_CREATED, null,
                principal.userAccountId().toString(), null,
                "Created student login for student code " + studentCode + " in tenant " + tenantId);
        return CreateUserResponse.from(saved, tempPassword, "STUDENT");
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
