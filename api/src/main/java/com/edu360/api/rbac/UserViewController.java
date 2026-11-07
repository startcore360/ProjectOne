package com.edu360.api.rbac;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Controller for USER_VIEW - listing and inspecting user accounts in scope.
 * Enforces T01 boundary so institution-scoped callers cannot see users outside their institution.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@Tag(name = "Users", description = "USER_VIEW - see user accounts")
public class UserViewController {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserGovernanceGuard userGovernanceGuard;

    @Autowired
    public UserViewController(
            PermissionEvaluatorService permissionEvaluatorService,
            UserAccountRepository userAccountRepository,
            @Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository,
            @Autowired(required = false) UserGovernanceGuard userGovernanceGuard) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.userAccountRepository = userAccountRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.userGovernanceGuard = userGovernanceGuard;
    }

    public UserViewController(
            PermissionEvaluatorService permissionEvaluatorService,
            UserAccountRepository userAccountRepository) {
        this(permissionEvaluatorService, userAccountRepository, null, null);
    }

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

        List<UserSummaryResponse> users;
        if (scopeType != ScopeType.TENANT && institutionId != null && userRoleAssignmentRepository != null) {
            Set<UUID> institutionUserIds = userRoleAssignmentRepository
                    .findByIdTenantIdAndInstitutionIdAndStatus(tenantId, institutionId, AssignmentStatus.ACTIVE)
                    .stream()
                    .map(UserRoleAssignment::getUserAccountId)
                    .collect(Collectors.toSet());

            users = userAccountRepository.findAll().stream()
                    .filter(account -> tenantId.equals(account.getId().getTenantId())
                            && institutionUserIds.contains(account.getId().getUserAccountId()))
                    .map(this::toSummary)
                    .toList();
        } else {
            users = userAccountRepository.findAll().stream()
                    .filter(account -> tenantId.equals(account.getId().getTenantId()))
                    .map(this::toSummary)
                    .toList();
        }

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

        if (scopeType != ScopeType.TENANT && institutionId != null && userGovernanceGuard != null) {
            userGovernanceGuard.checkTargetUserAuthority(tenantId, principal.userAccountId(), userId, institutionId);
        }

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
