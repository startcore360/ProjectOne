package com.edu360.api.rbac;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;
import com.edu360.api.rbac.dto.InstitutionScopeRequest;
import com.edu360.api.tenant.TenantRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controller for USER_DEACTIVATE - soft-deactivates accounts, ends role assignments,
 * disables Keycloak login and revokes sessions (WF-27).
 * Enforces T01 (institution scope boundary) and T02 (hierarchy boundary) protection.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@Tag(name = "Users", description = "USER_DEACTIVATE - deactivate a user (soft delete only)")
public class UserDeactivationController {

    private static final Logger log = LoggerFactory.getLogger(UserDeactivationController.class);

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final TenantRepository tenantRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;
    private final UserGovernanceGuard userGovernanceGuard;

    @Autowired
    public UserDeactivationController(
            PermissionEvaluatorService permissionEvaluatorService,
            UserAccountRepository userAccountRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            UserIdentityLinkRepository userIdentityLinkRepository,
            TenantRepository tenantRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService,
            @Autowired(required = false) UserGovernanceGuard userGovernanceGuard) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.userAccountRepository = userAccountRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.tenantRepository = tenantRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditLogService = auditLogService;
        this.userGovernanceGuard = userGovernanceGuard;
    }

    public UserDeactivationController(
            PermissionEvaluatorService permissionEvaluatorService,
            UserAccountRepository userAccountRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            UserIdentityLinkRepository userIdentityLinkRepository,
            TenantRepository tenantRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService) {
        this(permissionEvaluatorService, userAccountRepository, userRoleAssignmentRepository,
                userIdentityLinkRepository, tenantRepository, keycloakAdminClient, auditLogService, null);
    }

    @PostMapping("/{userId}/deactivation")
    @Operation(summary = "Deactivate a user account - soft delete only, never a real DELETE (USER_DEACTIVATE)")
    public ResponseEntity<CreateUserCheckResponse> deactivateUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody InstitutionScopeRequest request,
            Authentication authentication) {

        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        RbacScope scope = RbacScope.institution(request.institutionId());

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_DEACTIVATE", scope, RbacTarget.of(userId));

        // Addendum Rule 11: Self-deactivation prohibited
        if (principal.userAccountId().equals(userId)) {
            log.warn("Security check: user {} attempted to deactivate their own account [Addendum rule 11]", userId);
            throw new BusinessRuleViolationException(
                    "CANNOT_DEACTIVATE_SELF", "An administrator cannot deactivate their own user account.");
        }

        if (userGovernanceGuard != null) {
            userGovernanceGuard.checkTargetUserAuthority(tenantId, principal.userAccountId(), userId, request.institutionId());
        }

        UserAccount account = userAccountRepository.findById(new UserAccountId(tenantId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " was not found"));

        if (account.getStatus() == UserAccountStatus.DEACTIVATED) {
            return ResponseEntity.ok(new CreateUserCheckResponse(
                    "USER_DEACTIVATE", "ALREADY_DEACTIVATED", "User is already deactivated; no changes made."));
        }

        Instant now = Instant.now();
        account.setStatus(UserAccountStatus.DEACTIVATED);
        account.setDeactivatedAt(now);
        account.setDeactivatedBy(principal.userAccountId());
        account.setUpdatedBy(principal.userAccountId());
        userAccountRepository.save(account);

        List<UserRoleAssignment> activeAssignments = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, userId, AssignmentStatus.ACTIVE);
        for (UserRoleAssignment assignment : activeAssignments) {
            assignment.setStatus(AssignmentStatus.ENDED);
            assignment.setEffectiveTo(now);
            assignment.setUpdatedBy(principal.userAccountId());
            userRoleAssignmentRepository.save(assignment);
        }

        userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(tenantId, userId, IdentityLinkStatus.ACTIVE)
                .ifPresent(link -> {
                    tenantRepository.findById(tenantId).ifPresent(tenant -> {
                        keycloakAdminClient.setUserEnabled(tenant.getKeycloakRealm(), link.getIdentitySubject(), false);
                        keycloakAdminClient.logoutUser(tenant.getKeycloakRealm(), link.getIdentitySubject());
                    });
                });

        auditLogService.record(
                AuditEventType.USER_DEACTIVATED,
                null,
                account.getEmail(),
                "0.0.0.0",
                "User account " + userId + " deactivated and roles ended by admin " + principal.userAccountId());

        return ResponseEntity.ok(new CreateUserCheckResponse(
                "USER_DEACTIVATE", "ALLOWED", "User deactivated (soft delete; the row was never removed)."));
    }
}
