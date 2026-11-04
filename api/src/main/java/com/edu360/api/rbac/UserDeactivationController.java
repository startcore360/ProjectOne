package com.edu360.api.rbac;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;
import com.edu360.api.rbac.dto.InstitutionScopeRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: "Deactivate a user" (USER_DEACTIVATE) - POST /api/tenants/{tenantId}/users/{userId}/
 * deactivation. Per the spec (sql/05_ref_data.sql rows 450-454) granted "Own institution" only
 * to University/College/School Admin, IT Administrator and School IT Support - always an
 * INSTITUTION-scoped check, never TENANT or DEPARTMENT, which is why the request body
 * (InstitutionScopeRequest) only ever carries an institutionId, unlike USER_VIEW/USER_EDIT.
 *
 * GUARD (Addendum rule 17, "no hard delete" / UserAccountStatus's own javadoc "DEACTIVATED is
 * permanent... no reactivate"): this ALWAYS does a status flip via save(), never a delete - note
 * UserAccountRepository (identity/, read-only reference) extends plain JpaRepository and so
 * technically still exposes a delete method, but this controller never calls it, matching the
 * same discipline UserRoleAssignmentRepository/RoleActionRepository enforce by not exposing
 * delete at all. Deactivating an already-deactivated user is a no-op (idempotent, not an error),
 * since "deactivate" has no further effect to apply once reached.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "USER_DEACTIVATE - deactivate a user (soft delete only)")
public class UserDeactivationController {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountRepository userAccountRepository;

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

        UserAccount account = userAccountRepository.findById(new UserAccountId(tenantId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " was not found"));

        if (account.getStatus() == UserAccountStatus.DEACTIVATED) {
            return ResponseEntity.ok(new CreateUserCheckResponse(
                    "USER_DEACTIVATE", "ALREADY_DEACTIVATED", "User is already deactivated; no changes made."));
        }

        // Status flip via save() only - deliberately no repository.delete(...) call anywhere in
        // this method (Addendum rule 17: no hard delete, ever).
        account.setStatus(UserAccountStatus.DEACTIVATED);
        account.setDeactivatedAt(Instant.now());
        account.setDeactivatedBy(principal.userAccountId());
        account.setUpdatedBy(principal.userAccountId());
        userAccountRepository.save(account);

        return ResponseEntity.ok(new CreateUserCheckResponse(
                "USER_DEACTIVATE", "ALLOWED", "User deactivated (soft delete; the row was never removed)."));
    }
}
