package com.edu360.api.identity;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Set;
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
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.dto.TopUserCreateRequest;
import com.edu360.api.identity.dto.TopUserResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.RoleAssignmentService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service managing top governance account creation (Story #23 / Action #268: TENANT_TOP_USERS_CREATE):
 * - Enforces Rule F-12 & C6: Two-step login requirement on all top governance accounts.
 * - Enforces Decision C9: MANAGEMENT and GOVERNING_BODY_APPROVER mapped to UserType.GOVERNANCE,
 *   VICE_CHANCELLOR and DATA_PROTECTION_CONTACT mapped to UserType.STAFF.
 * - Enforces Addendum Rule 11: Prohibition of self-assignment / self-elevation.
 */
@Service
@RequiredArgsConstructor
public class TopUserService {

    private static final Logger log = LoggerFactory.getLogger(TopUserService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final Set<String> ALLOWED_TOP_ROLES = Set.of(
            "MANAGEMENT",
            "DATA_PROTECTION_CONTACT",
            "VICE_CHANCELLOR",
            "GOVERNING_BODY_APPROVER"
    );

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final TenantRepository tenantRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final RoleAssignmentService roleAssignmentService;
    private final AuditLogService auditLogService;

    @Transactional
    public TopUserResponse createTopUser(RbacPrincipal principal, UUID tenantId, TopUserCreateRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(request, "request is required");

        if (!principal.tenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found or does not match authenticated context");
        }

        // 1. Authorize action TENANT_TOP_USERS_CREATE in TENANT scope
        permissionEvaluatorService.authorize(
                principal,
                "TENANT_TOP_USERS_CREATE",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        // 2. Validate role code
        String roleCode = request.roleCode() != null ? request.roleCode().trim().toUpperCase() : "";
        if (!ALLOWED_TOP_ROLES.contains(roleCode)) {
            throw new BusinessRuleViolationException(
                    "INVALID_TOP_ROLE",
                    "Role code '" + roleCode + "' is not an allowed top governance role. Allowed: " + ALLOWED_TOP_ROLES
            );
        }

        // 3. Addendum Rule 11 / Separation of Duty: Cannot assign to self
        String normalizedEmail = request.email().toLowerCase().trim();
        UserAccount callerAccount = userAccountRepository.findById(new UserAccountId(tenantId, principal.userAccountId()))
                .orElse(null);

        if (callerAccount != null && callerAccount.getEmail() != null
                && callerAccount.getEmail().equalsIgnoreCase(normalizedEmail)) {
            log.warn("Security check: user {} attempted to assign top governance role {} to themselves [Addendum rule 11]",
                    principal.userAccountId(), roleCode);
            throw new UnauthorizedException(
                    "A principal may not assign or create a top governance role for their own account (Addendum rule 11 / separation of duty)."
            );
        }

        // 4. Check email uniqueness in tenant
        if (userAccountRepository.existsByIdTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new DuplicateResourceException(
                    "A user with email '" + normalizedEmail + "' already exists in this tenant."
            );
        }

        // 5. Decision C9: UserType classification
        UserType userType = ("MANAGEMENT".equals(roleCode) || "GOVERNING_BODY_APPROVER".equals(roleCode))
                ? UserType.GOVERNANCE
                : UserType.STAFF;

        // 6. Resolve Keycloak Realm
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String realm = tenant != null && tenant.getKeycloakRealm() != null
                ? tenant.getKeycloakRealm()
                : tenantId.toString() + "-realm";

        // 7. Generate temporary password
        String tempPassword = generateTemporaryPassword();

        // 8. Provision user in Keycloak tenant realm (mustChangePassword = true, two-step login required)
        String keycloakUserId = keycloakAdminClient.provisionUser(
                realm,
                request.displayName().trim(),
                normalizedEmail,
                tempPassword,
                true
        );
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new KeycloakProvisioningException("Keycloak did not return a valid user id for top user");
        }
        log.info("Provisioned Keycloak account {} for top user '{}' ({}) in realm '{}'",
                keycloakUserId, request.displayName(), normalizedEmail, realm);

        // 9. Persist UserAccount (app.user_account)
        UUID userAccountId = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userAccountId))
                .userType(userType)
                .displayName(request.displayName().trim())
                .email(normalizedEmail)
                .mobile(request.mobile() != null && !request.mobile().isBlank() ? request.mobile().trim() : null)
                .status(UserAccountStatus.PENDING_ACTIVATION)
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        UserAccount savedAccount = userAccountRepository.save(account);

        // 10. Persist UserIdentityLink (app.user_identity_link)
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

        // 11. Assign top governance role in TENANT scope
        AssignRoleRequest assignReq = new AssignRoleRequest(
                null,
                roleCode,
                ScopeType.TENANT,
                null,
                null,
                null,
                null
        );
        roleAssignmentService.assignRole(principal, userAccountId, assignReq);

        // 12. Audit log [Rule F-12]
        auditLogService.record(
                AuditEventType.TOP_USER_CREATED,
                null,
                principal.userAccountId().toString(),
                null,
                String.format("Created top governance user '%s' (%s) with role %s [Rule F-12, C6, C9]",
                        request.displayName(), normalizedEmail, roleCode)
        );

        log.info("Successfully created top governance user {} ({}) with role {} in tenant {}",
                userAccountId, normalizedEmail, roleCode, tenantId);

        return new TopUserResponse(
                userAccountId,
                savedAccount.getDisplayName(),
                savedAccount.getEmail(),
                savedAccount.getMobile(),
                roleCode,
                userType,
                savedAccount.getStatus(),
                tempPassword,
                true,
                "Top governance user created with two-step login requirement [Rule F-12, C6, C9]."
        );
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CHAR_POOL.charAt(RANDOM.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }
}
