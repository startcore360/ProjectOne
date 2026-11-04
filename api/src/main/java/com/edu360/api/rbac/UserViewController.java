package com.edu360.api.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.rbac.dto.UserListResponse;
import com.edu360.api.rbac.dto.UserSummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: "See user accounts" (USER_VIEW) - GET /api/tenants/{tenantId}/users (list) and
 * GET /api/tenants/{tenantId}/users/{userId} (detail). Per the spec (sql/05_ref_data.sql
 * ref.standard_role_action rows 416-434) this is granted "Own scope" to University/College/
 * School Admin, Vice Principal (College/School), Registrar, Management, Vice Chancellor,
 * Finance Director, Controller of Examinations, University Accountant, University HR Manager,
 * Principal (College/School), College/School Accountant, System Operator, IT Administrator and
 * School IT Support - i.e. "any scope shape", unlike USER_DEACTIVATE/USER_PASSWORD_RESET_UNLOCK
 * which are "Own institution" only. The caller states which scope it is asking in (same shape as
 * UserCreationController's CreateUserCheckRequest), since app.user_role_assignment has no
 * column recording "the one scope a user operates in" - a principal can hold several differently
 * scoped assignments (Addendum rules 8/9), so the evaluator always needs to be told exactly
 * which one this request means.
 *
 * WHY list filters UserAccountRepository.findAll() by tenantId in Java rather than a new derived
 * query method: UserAccountRepository (identity/, read-only reference for this task) exposes no
 * tenant-scoped finder today. Postgres RLS (app.tenant_id session GUC, already wired per recent
 * commit history) is the primary isolation layer; this filter is the same "defence in depth, not
 * instead of" posture UserAccount's own class javadoc describes for tenant_id being a real
 * column at all. A dedicated, paginated findByIdTenantId(...) method is a natural follow-up once
 * identity/ is back in scope for this task.
 *
 * WHY this does NOT filter the returned rows by "the target user's institution" even though the
 * requested scope might be INSTITUTION: app.user_account has no institution_id column in the
 * current schema (it is purely a login identity - see UserAccount's own javadoc), so there is no
 * column here to filter the TARGET population by. Only the CALLER's own grant is scope-checked
 * via the evaluator; which rows "belong" to an institution is a gap flagged here rather than
 * silently invented.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "USER_VIEW - see user accounts")
public class UserViewController {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountRepository userAccountRepository;

    @GetMapping
    @Operation(summary = "List user accounts in a given scope (USER_VIEW)")
    public ResponseEntity<UserListResponse> listUsers(
            @PathVariable UUID tenantId,
            @RequestParam ScopeType scopeType,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID departmentId,
            Authentication authentication) {

        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        RbacScope scope = UserRequestSupport.scopeOf(scopeType, institutionId, departmentId);

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_VIEW", scope, RbacTarget.NONE);

        List<UserSummaryResponse> users = userAccountRepository.findAll().stream()
                .filter(account -> tenantId.equals(account.getId().getTenantId()))
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(new UserListResponse(users));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get one user account's detail in a given scope (USER_VIEW)")
    public ResponseEntity<UserSummaryResponse> getUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @RequestParam ScopeType scopeType,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID departmentId,
            Authentication authentication) {

        TenantPrincipal principal = UserRequestSupport.requireTenantPrincipal(authentication, tenantId);
        RbacScope scope = UserRequestSupport.scopeOf(scopeType, institutionId, departmentId);

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_VIEW", scope, RbacTarget.of(userId));

        UserAccount account = userAccountRepository.findById(new UserAccountId(tenantId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " was not found"));

        return ResponseEntity.ok(toSummary(account));
    }

    private UserSummaryResponse toSummary(UserAccount account) {
        return new UserSummaryResponse(
                account.getId().getUserAccountId(),
                account.getUserType(),
                account.getDisplayName(),
                account.getEmail(),
                account.getMobile(),
                account.getStatus());
    }
}
