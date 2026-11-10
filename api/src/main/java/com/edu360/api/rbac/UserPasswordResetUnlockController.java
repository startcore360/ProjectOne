package com.edu360.api.rbac;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;
import com.edu360.api.rbac.dto.InstitutionScopeRequest;
import com.edu360.api.rbac.dto.PasswordResetResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controller for USER_PASSWORD_RESET_UNLOCK: resets passwords and unlocks accounts.
 * Enforces T01 (institution scope boundary) and T02 (hierarchy boundary) protection.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@Tag(name = "Users", description = "USER_PASSWORD_RESET_UNLOCK - reset a password or unlock an account")
public class UserPasswordResetUnlockController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantRepository tenantRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;
    private final UserGovernanceGuard userGovernanceGuard;

    /** B3 / I-05 (R2-35): restarts the 7-day window of a student's printed password on reset. */
    @Autowired(required = false)
    private com.edu360.api.auth.PrintedPasswordPolicy printedPasswordPolicy;

    @Autowired
    public UserPasswordResetUnlockController(
            PermissionEvaluatorService permissionEvaluatorService,
            TenantRepository tenantRepository,
            UserIdentityLinkRepository userIdentityLinkRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService,
            @Autowired(required = false) UserGovernanceGuard userGovernanceGuard) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.tenantRepository = tenantRepository;
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditLogService = auditLogService;
        this.userGovernanceGuard = userGovernanceGuard;
    }

    public UserPasswordResetUnlockController(
            PermissionEvaluatorService permissionEvaluatorService,
            TenantRepository tenantRepository,
            UserIdentityLinkRepository userIdentityLinkRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService) {
        this(permissionEvaluatorService, tenantRepository, userIdentityLinkRepository, keycloakAdminClient, auditLogService, null);
    }

    @PostMapping("/{userId}/password-reset")
    @Operation(summary = "Reset a user's Keycloak password (USER_PASSWORD_RESET_UNLOCK)")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody InstitutionScopeRequest request,
            Authentication authentication) {

        TenantPrincipal principal = authorize(authentication, tenantId, userId, request);

        String keycloakUserId = activeKeycloakSubjectFor(tenantId, userId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));

        String temporaryPassword = generateTemporaryPassword();
        keycloakAdminClient.resetPassword(tenant.getKeycloakRealm(), keycloakUserId, temporaryPassword, true);
        keycloakAdminClient.logoutUser(tenant.getKeycloakRealm(), keycloakUserId);
        if (printedPasswordPolicy != null) {
            printedPasswordPolicy.markIssued(tenantId, userId);
        }

        auditLogService.record(
                AuditEventType.USER_PASSWORD_RESET,
                null,
                keycloakUserId,
                "0.0.0.0",
                "Password reset for user " + userId + " by admin " + principal.userAccountId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new PasswordResetResponse(
                        "USER_PASSWORD_RESET_UNLOCK", "ALLOWED",
                        "Password reset in Keycloak for user " + userId + ".", temporaryPassword));
    }

    @PostMapping("/{userId}/unlock")
    @Operation(summary = "Unlock a user's account (USER_PASSWORD_RESET_UNLOCK)")
    public ResponseEntity<CreateUserCheckResponse> unlockUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody InstitutionScopeRequest request,
            Authentication authentication) {

        TenantPrincipal principal = authorize(authentication, tenantId, userId, request);

        String keycloakUserId = activeKeycloakSubjectFor(tenantId, userId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));

        keycloakAdminClient.clearBruteForceLockout(tenant.getKeycloakRealm(), keycloakUserId);
        keycloakAdminClient.logoutUser(tenant.getKeycloakRealm(), keycloakUserId);

        auditLogService.record(
                AuditEventType.USER_UNLOCKED,
                null,
                keycloakUserId,
                "0.0.0.0",
                "Brute-force lockout cleared for user " + userId + " by admin " + principal.userAccountId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CreateUserCheckResponse(
                        "USER_PASSWORD_RESET_UNLOCK", "ALLOWED",
                        "User " + userId + " unlocked: Keycloak brute-force lockout cleared."));
    }

    private TenantPrincipal authorize(Authentication authentication, UUID tenantId, UUID userId,
                                       InstitutionScopeRequest request) {
        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        RbacScope scope = RbacScope.institution(request.institutionId());

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_PASSWORD_RESET_UNLOCK", scope, RbacTarget.of(userId));

        if (userGovernanceGuard != null) {
            userGovernanceGuard.checkTargetUserAuthority(tenantId, principal.userAccountId(), userId, request.institutionId());
        }

        return principal;
    }

    private String activeKeycloakSubjectFor(UUID tenantId, UUID userId) {
        UserIdentityLink link = userIdentityLinkRepository
                .findFirstByIdTenantIdAndUserAccountIdAndStatus(tenantId, userId, IdentityLinkStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + userId + " has no active Keycloak identity link"));
        return link.getIdentitySubject();
    }

    private String generateTemporaryPassword() {
        byte[] randomBytes = new byte[18];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
