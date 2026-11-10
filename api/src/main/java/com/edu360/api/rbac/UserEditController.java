package com.edu360.api.rbac;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.dto.UpdateUserRequest;
import com.edu360.api.rbac.dto.UserSummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * WHAT: "Edit a user account" (USER_EDIT) - PATCH /api/tenants/{tenantId}/users/{userId}. Per
 * the spec (sql/05_ref_data.sql rows 444-449) granted "Own scope" to University/College/School
 * Admin, Vice Principal (College/School) and Registrar only - the IT/System-Operator roles that
 * can VIEW a user are deliberately NOT in this list, matching the brief's instruction to drop
 * them for this story.
 *
 * WHAT this edits: only the fields app.user_account itself owns (displayName/email/mobile) - a
 * partial update, so a field the caller omits (null) is left untouched rather than cleared. This
 * deliberately never changes status, credential_expires_at, deactivated_at/by, or anything
 * Keycloak owns; those are separate actions (USER_DEACTIVATE, USER_PASSWORD_RESET_UNLOCK).
 *
 * GUARD: a DEACTIVATED account can never be edited back to "normal" through this endpoint -
 * Addendum rule 17 / UserAccountStatus's own javadoc ("DEACTIVATED is permanent... no
 * reactivate") would otherwise be silently defeated by an admin simply patching fields on a
 * deactivated row. This check runs AFTER the permission check, same ordering PermissionEvaluator
 * Service itself uses for its own guard (deny on the clearest, cheapest rule first is still
 * "deny", but authorization is evaluated before any business rule about the resource's state).
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@Tag(name = "Users", description = "USER_EDIT - edit a user account")
public class UserEditController {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountRepository userAccountRepository;
    private final UserGovernanceGuard userGovernanceGuard;

    public UserEditController(
            PermissionEvaluatorService permissionEvaluatorService,
            UserAccountRepository userAccountRepository) {
        this(permissionEvaluatorService, userAccountRepository, null);
    }

    @Autowired
    public UserEditController(
            PermissionEvaluatorService permissionEvaluatorService,
            UserAccountRepository userAccountRepository,
            @Autowired(required = false) UserGovernanceGuard userGovernanceGuard) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.userAccountRepository = userAccountRepository;
        this.userGovernanceGuard = userGovernanceGuard;
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Edit a user account's own fields (USER_EDIT)")
    public ResponseEntity<UserSummaryResponse> updateUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {

        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        RbacScope scope = UserRequestSupport.scopeOf(
                request.scopeType(), request.institutionId(), request.departmentId());

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_EDIT", scope, RbacTarget.of(userId));

        if (userGovernanceGuard != null) {
            userGovernanceGuard.checkTargetUserAuthority(tenantId, principal.userAccountId(), userId, request.institutionId());
        }

        UserAccount account = userAccountRepository.findById(new UserAccountId(tenantId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " was not found"));

        if (account.getStatus() == UserAccountStatus.DEACTIVATED) {
            throw new UnauthorizedException(
                    "User " + userId + " is deactivated; a deactivated account cannot be edited (no reactivate).");
        }

        if (request.displayName() != null) {
            account.setDisplayName(request.displayName());
        }
        if (request.email() != null) {
            account.setEmail(request.email());
        }
        if (request.mobile() != null) {
            account.setMobile(request.mobile());
        }
        account.setUpdatedBy(principal.userAccountId());

        UserAccount saved = userAccountRepository.save(account);

        return ResponseEntity.ok(new UserSummaryResponse(
                saved.getId().getUserAccountId(),
                saved.getUserType(),
                saved.getDisplayName(),
                saved.getEmail(),
                saved.getMobile(),
                saved.getStatus()));
    }
}
