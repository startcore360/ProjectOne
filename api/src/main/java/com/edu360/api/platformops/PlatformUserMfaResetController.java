package com.edu360.api.platformops;

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

import com.edu360.api.auth.MfaRecoveryService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platformops.dto.PlatformMfaResetRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * WHAT: plan story E8-S4 "Reset authenticator" for platform staff, under decision MFA-01.
 *
 * WHO: PLATFORM_USER_MANAGE - Product Owner only, and a reserved action that can never be handed
 * over (ck_platform_role_action_reserved), which is the right home for resetting another staff
 * member's second factor. Nobody resets their own: a Product Owner who lost their phone uses a
 * backup code, or the emergency procedure (docs/MFA_EMERGENCY_RESET_RUNBOOK.md).
 *
 * HOW: removes the authenticator and backup codes in the platform realm (never the password), ends
 * every session, writes a platform audit row with the reason, and notifies the person.
 */
@RestController
@RequestMapping({"/v1/platform/platform-users", "/api/platform/platform-users"})
@Tag(name = "Platform Users", description = "PLATFORM_USER_MANAGE - reset a staff member's two-step login")
public class PlatformUserMfaResetController {

    static final String ACTION = "PLATFORM_USER_MANAGE";

    private final PlatformPermissionService platformPermissionService;
    private final PlatformUserRepository platformUserRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;
    private final MfaRecoveryService mfaRecoveryService;
    private final PlatformAuditWriter platformAuditWriter;
    private final PlatformNotificationService platformNotificationService;

    @Autowired
    public PlatformUserMfaResetController(PlatformPermissionService platformPermissionService,
                                          PlatformUserRepository platformUserRepository,
                                          KeycloakAdminClient keycloakAdminClient,
                                          KeycloakProperties keycloakProperties,
                                          MfaRecoveryService mfaRecoveryService,
                                          PlatformAuditWriter platformAuditWriter,
                                          @Autowired(required = false) PlatformNotificationService platformNotificationService) {
        this.platformPermissionService = platformPermissionService;
        this.platformUserRepository = platformUserRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakProperties = keycloakProperties;
        this.mfaRecoveryService = mfaRecoveryService;
        this.platformAuditWriter = platformAuditWriter;
        this.platformNotificationService = platformNotificationService;
    }

    @PostMapping("/{platformUserId}/mfa-reset")
    @Operation(summary = "Reset a platform staff member's two-step login (PLATFORM_USER_MANAGE, MFA-01)")
    public ResponseEntity<Map<String, String>> resetMfa(@PathVariable UUID platformUserId,
                                                        @Valid @RequestBody PlatformMfaResetRequest request,
                                                        Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("A platform sign-in is required.");
        }
        platformPermissionService.authorize(principal, ACTION);
        if (platformUserId.equals(principal.platformUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_RESET_OWN_MFA",
                    "You cannot reset your own two-step login. Use a backup code, or follow the emergency procedure.");
        }
        PlatformUser target = platformUserRepository.findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform user " + platformUserId + " was not found"));

        String realm = keycloakProperties.getPlatformRealm();
        String keycloakUserId = target.getIdentitySubject();
        int removed = keycloakAdminClient.removeOtpCredentials(realm, keycloakUserId);
        mfaRecoveryService.clear(realm, keycloakUserId);
        keycloakAdminClient.logoutUser(realm, keycloakUserId);

        platformAuditWriter.record(principal, null, "PLATFORM_USER_MFA_RESET", "RESET_MFA", ACTION,
                "PLATFORM_USER", platformUserId, PlatformAuditWriter.SUCCESS,
                Map.of("reason", Map.of("new", request.reason().trim()),
                        "authenticatorsRemoved", Map.of("new", removed)));
        if (platformNotificationService != null) {
            platformNotificationService.notify(platformUserId, "MFA_RESET", "Your two-step login was reset",
                    "The Product Owner reset your two-step login. Next time you sign in you will set up your "
                            + "authenticator app again. If you did not ask for this, tell the Product Owner now.",
                    "PLATFORM_USER", platformUserId, "SECURITY", principal.platformUserId());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("actionCode", ACTION, "status", "RESET",
                        "message", "Two-step login reset. They set up a new authenticator at their next sign-in."));
    }
}
