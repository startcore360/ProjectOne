package com.edu360.api.rbac;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.MfaRecoveryService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.rbac.dto.MfaResetRequest;
import com.edu360.api.selfservice.InAppNotifier;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * WHAT: USER_MFA_RESET (decision MFA-01) - an admin resets a tenant user's two-step login when
 * their authenticator no longer works (lost or new phone, deleted app).
 *
 * HOW: same checks as password reset (UserPasswordResetUnlockController): the action at the
 * acting institution scope, then the governance guard (no reaching above your own authority).
 * Nobody may reset their own two-step login - that would let one stolen password undo the second
 * factor. The reset removes the authenticator and the backup codes (never the password), ends every
 * session, records the reason, and tells the user in-app so a reset they did not ask for is noticed.
 * The next sign-in shows a new QR code through the existing in-app set-up.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@Tag(name = "Users", description = "USER_MFA_RESET - reset a user's two-step login")
public class UserMfaResetController {

    static final String ACTION = "USER_MFA_RESET";

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantRepository tenantRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;
    private final MfaRecoveryService mfaRecoveryService;
    private final UserGovernanceGuard userGovernanceGuard;
    private final InAppNotifier inAppNotifier;

    @Autowired
    public UserMfaResetController(PermissionEvaluatorService permissionEvaluatorService,
                                  TenantRepository tenantRepository,
                                  UserIdentityLinkRepository userIdentityLinkRepository,
                                  KeycloakAdminClient keycloakAdminClient,
                                  AuditLogService auditLogService,
                                  MfaRecoveryService mfaRecoveryService,
                                  @Autowired(required = false) UserGovernanceGuard userGovernanceGuard,
                                  @Autowired(required = false) InAppNotifier inAppNotifier) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.tenantRepository = tenantRepository;
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditLogService = auditLogService;
        this.mfaRecoveryService = mfaRecoveryService;
        this.userGovernanceGuard = userGovernanceGuard;
        this.inAppNotifier = inAppNotifier;
    }

    @PostMapping("/{userId}/mfa-reset")
    @Operation(summary = "Reset a user's two-step login (USER_MFA_RESET); the next sign-in sets up a new authenticator")
    public ResponseEntity<Map<String, String>> resetMfa(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody MfaResetRequest request,
            Authentication authentication) {

        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                ACTION, RbacScope.institution(request.institutionId()), RbacTarget.of(userId));
        if (userId.equals(principal.userAccountId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_RESET_OWN_MFA",
                    "You cannot reset your own two-step login. Use a backup code, or ask another administrator.");
        }
        if (userGovernanceGuard != null) {
            userGovernanceGuard.checkTargetUserAuthority(tenantId, principal.userAccountId(), userId, request.institutionId());
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));
        String realm = tenant.getKeycloakRealm();
        String keycloakUserId = activeKeycloakSubjectFor(tenantId, userId);

        int removed = keycloakAdminClient.removeOtpCredentials(realm, keycloakUserId);
        mfaRecoveryService.clear(realm, keycloakUserId);
        keycloakAdminClient.logoutUser(realm, keycloakUserId);

        auditLogService.record(AuditEventType.USER_MFA_RESET, null, keycloakUserId, "0.0.0.0",
                "Two-step login reset for user " + userId + " by admin " + principal.userAccountId()
                        + " (" + removed + " authenticator(s) removed). Reason: " + request.reason().trim());
        if (inAppNotifier != null) {
            inAppNotifier.notify(tenantId, userId, request.institutionId(), "MFA_RESET",
                    "Your two-step login was reset",
                    "An administrator reset your two-step login. Next time you sign in you will set up your "
                            + "authenticator app again. If you did not ask for this, contact your administrator now.",
                    "USER_ACCOUNT", userId, principal.userAccountId(), "SECURITY", "MFA_RESET:" + userId + ":" + System.currentTimeMillis());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("actionCode", ACTION, "status", "RESET",
                        "message", "Two-step login reset. The user sets up a new authenticator at their next sign-in."));
    }

    private String activeKeycloakSubjectFor(UUID tenantId, UUID userId) {
        UserIdentityLink link = userIdentityLinkRepository
                .findFirstByIdTenantIdAndUserAccountIdAndStatus(tenantId, userId, IdentityLinkStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + userId + " has no active Keycloak identity link"));
        return link.getIdentitySubject();
    }
}
