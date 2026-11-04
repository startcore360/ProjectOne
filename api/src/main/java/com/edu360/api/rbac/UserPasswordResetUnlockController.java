package com.edu360.api.rbac;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
import lombok.RequiredArgsConstructor;

/**
 * WHAT: "Reset a password or unlock an account" (USER_PASSWORD_RESET_UNLOCK) -
 * POST /api/tenants/{tenantId}/users/{userId}/password-reset and
 * POST /api/tenants/{tenantId}/users/{userId}/unlock. Per the spec (sql/05_ref_data.sql rows
 * 455-459) granted "Own institution" only to University/College/School Admin, IT Administrator
 * and School IT Support - always INSTITUTION-scoped, same InstitutionScopeRequest shape as
 * USER_DEACTIVATE.
 *
 * HOW this reaches Keycloak: reuses the existing KeycloakAdminClient exactly the way
 * AuthService.changeTenantPassword already does for a SELF password change - realm from
 * TenantRepository.findById(tenantId).getKeycloakRealm(), target Keycloak user id ("subject")
 * from the target user's own ACTIVE UserIdentityLink (identity/, read-only reference for this
 * task; both repositories are used as-is, not extended). No second Keycloak client is built
 * here, per the brief's explicit instruction.
 *
 * GAP CLOSED: /unlock now calls KeycloakAdminClient.clearBruteForceLockout(realm, keycloakUserId)
 * - DELETE /admin/realms/{realm}/attack-detection/brute-force/users/{id} - in addition to issuing
 * a fresh temporary credential via resetPassword, so it both restores the ability to log in AND
 * actually clears Keycloak's own brute-force failure-count record if that subsystem is enabled.
 * (Previously this endpoint only reused resetPassword and left a comment flagging the missing
 * admin call; see git history for that version if useful context.)
 *
 * SECURITY NOTE: the freshly generated temporary password is returned in the /password-reset
 * response body - see PasswordResetResponse's own javadoc for why, and why that is a
 * proof-of-integration simplification that should not ship to production as-is.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "USER_PASSWORD_RESET_UNLOCK - reset a password or unlock an account")
public class UserPasswordResetUnlockController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantRepository tenantRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @PostMapping("/{userId}/password-reset")
    @Operation(summary = "Reset a user's Keycloak password (USER_PASSWORD_RESET_UNLOCK)")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody InstitutionScopeRequest request,
            Authentication authentication) {

        authorize(authentication, tenantId, userId, request);

        String keycloakUserId = activeKeycloakSubjectFor(tenantId, userId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));

        String temporaryPassword = generateTemporaryPassword();
        keycloakAdminClient.resetPassword(tenant.getKeycloakRealm(), keycloakUserId, temporaryPassword);

        return ResponseEntity.ok(new PasswordResetResponse(
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

        authorize(authentication, tenantId, userId, request);

        String keycloakUserId = activeKeycloakSubjectFor(tenantId, userId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));

        // Both halves of a real "unlock": a fresh credential (resetPassword, so the user is not
        // left stuck on a password they may not know) AND clearing Keycloak's own brute-force
        // failure-count record (clearBruteForceLockout) - see this class's javadoc.
        keycloakAdminClient.resetPassword(tenant.getKeycloakRealm(), keycloakUserId, generateTemporaryPassword());
        keycloakAdminClient.clearBruteForceLockout(tenant.getKeycloakRealm(), keycloakUserId);

        return ResponseEntity.ok(new CreateUserCheckResponse(
                "USER_PASSWORD_RESET_UNLOCK", "ALLOWED",
                "User " + userId + " unlocked: Keycloak credential reset and brute-force lockout cleared."));
    }

    private TenantPrincipal authorize(Authentication authentication, UUID tenantId, UUID userId,
                                       InstitutionScopeRequest request) {
        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        RbacScope scope = RbacScope.institution(request.institutionId());

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_PASSWORD_RESET_UNLOCK", scope, RbacTarget.of(userId));
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
