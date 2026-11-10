package com.edu360.api.rbac;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.dto.CreateCustomRoleRequest;
import com.edu360.api.rbac.dto.RoleDetailResponse;
import com.edu360.api.rbac.dto.UpdateCustomRoleRequest;
import com.edu360.api.rbac.dto.UpdateRoleActionSetRequest;

import lombok.RequiredArgsConstructor;

/**
 * Custom role creation, editing and retirement (story 16: ROLE_CUSTOM_MANAGE). A-03 guards:
 * <ul>
 *   <li>ROLE_CUSTOM_MANAGE is checked at the caller's own active scope (D3 CR-02: College and
 *       School Admin at their institution, University Admin at the university for
 *       university-office roles only).</li>
 *   <li>Only Action List actions (CR-01); the shared lock list
 *       {@link PermissionEvaluatorService#isLockedForCustomRoles} is refused with 422
 *       LOCKED_ACTION_FORBIDDEN whatever the role says (CR-03); approval actions are refused with
 *       422 APPROVAL_ACTION_FORBIDDEN because approval power comes only from approval chains
 *       (CR-10, Part 9 section 2.2).</li>
 *   <li>The creator must hold every action at their own active scope (CR-04), else 422
 *       ACTION_NOT_HELD.</li>
 *   <li>Nobody edits a role they hold (CR-04, 403); a role owned by another institution looks
 *       missing (404); a lower level never edits a role owned by a higher level (Addendum rule 2,
 *       403).</li>
 * </ul>
 * Roles are never hard-deleted (Addendum rule 17).
 */
@Service
@RequiredArgsConstructor
public class RoleCustomService {

    private static final Logger log = LoggerFactory.getLogger(RoleCustomService.class);

    private final RoleDefinitionRepository roleDefinitionRepository;
    private final RoleActionRepository roleActionRepository;
    private final ActionCatalogRepository actionCatalogRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    public RoleDetailResponse getRole(RbacPrincipal principal, UUID roleDefinitionId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(roleDefinitionId, "roleDefinitionId is required");
        UUID tenantId = principal.tenantId();

        RoleDefinition role = roleDefinitionRepository.findById(new RoleDefinitionId(tenantId, roleDefinitionId))
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleDefinitionId + " was not found."));

        List<String> actions = roleActionRepository.findByIdTenantIdAndRoleDefinitionId(tenantId, roleDefinitionId)
                .stream()
                .map(RoleAction::getActionCode)
                .toList();

        return RoleDetailResponse.from(role, actions);
    }

    @Transactional
    public RoleDetailResponse createCustomRole(RbacPrincipal principal, CreateCustomRoleRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(request, "request is required");
        UUID tenantId = principal.tenantId();

        Caller caller = authorizeManage(principal);

        String roleCode = request.roleCode().trim().toUpperCase();
        if (roleDefinitionRepository.existsByIdTenantIdAndRoleCode(tenantId, roleCode)) {
            throw new DuplicateResourceException("Role code '" + roleCode + "' already exists in this tenant.");
        }

        enforceCustomScope(caller, request.customScope());

        List<String> actions = normalise(request.actionCodes());
        boolean hasSensitiveAction = validateAndCollectSensitivity(principal, caller, actions);

        UUID roleDefinitionId = UUID.randomUUID();
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, roleDefinitionId))
                .roleCode(roleCode)
                .roleName(request.roleName().trim())
                .roleOrigin(RoleOrigin.CUSTOM)
                .roleLevel(request.roleLevel())
                .baseStandardRole(null)
                .customScope(request.customScope())
                .sensitiveRole(hasSensitiveAction)
                .status(RoleDefinitionStatus.ACTIVE)
                .ownerLevel(caller.ownerLevel())
                .ownerInstitutionId(caller.institutionId())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        RoleDefinition saved = roleDefinitionRepository.save(role);

        List<String> savedActions = new ArrayList<>();
        for (String actionCode : actions) {
            RoleAction roleAction = RoleAction.builder()
                    .id(new RoleActionId(tenantId, UUID.randomUUID()))
                    .roleDefinitionId(roleDefinitionId)
                    .actionCode(actionCode)
                    .createdBy(principal.userAccountId())
                    .updatedBy(principal.userAccountId())
                    .build();
            roleActionRepository.save(roleAction);
            savedActions.add(actionCode);
        }

        auditLogService.record(
                AuditEventType.CUSTOM_ROLE_CREATED,
                null,
                principal.userAccountId().toString(),
                null,
                "Custom role '" + saved.getRoleCode() + "' (" + roleDefinitionId + ") created with " + savedActions.size() + " actions."
        );

        log.info("Custom role '{}' ({}) created in tenant {}", saved.getRoleCode(), roleDefinitionId, tenantId);
        return RoleDetailResponse.from(saved, savedActions);
    }

    @Transactional
    public RoleDetailResponse updateCustomRole(RbacPrincipal principal, UUID roleDefinitionId, UpdateCustomRoleRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(roleDefinitionId, "roleDefinitionId is required");
        Objects.requireNonNull(request, "request is required");
        UUID tenantId = principal.tenantId();

        Caller caller = authorizeManage(principal);
        RoleDefinition role = loadEditableRole(principal, caller, roleDefinitionId);

        if (request.roleName() != null && !request.roleName().isBlank()) {
            role.setRoleName(request.roleName().trim());
        }
        if (request.status() != null) {
            role.setStatus(request.status());
        }
        role.setUpdatedBy(principal.userAccountId());
        RoleDefinition saved = roleDefinitionRepository.save(role);

        List<String> actions = roleActionRepository.findByIdTenantIdAndRoleDefinitionId(tenantId, roleDefinitionId)
                .stream()
                .map(RoleAction::getActionCode)
                .toList();

        auditLogService.record(
                AuditEventType.CUSTOM_ROLE_UPDATED,
                null,
                principal.userAccountId().toString(),
                null,
                "Custom role '" + saved.getRoleCode() + "' (" + roleDefinitionId + ") updated."
        );

        log.info("Custom role '{}' ({}) updated in tenant {}", saved.getRoleCode(), roleDefinitionId, tenantId);
        return RoleDetailResponse.from(saved, actions);
    }

    @Transactional
    public RoleDetailResponse updateRoleActionSet(RbacPrincipal principal, UUID roleDefinitionId, UpdateRoleActionSetRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(roleDefinitionId, "roleDefinitionId is required");
        Objects.requireNonNull(request, "request is required");
        UUID tenantId = principal.tenantId();

        Caller caller = authorizeManage(principal);
        RoleDefinition role = loadEditableRole(principal, caller, roleDefinitionId);

        List<String> actionCodes = normalise(request.actionCodes());
        boolean hasSensitiveAction = validateAndCollectSensitivity(principal, caller, actionCodes);

        roleActionRepository.deleteByIdTenantIdAndRoleDefinitionId(tenantId, roleDefinitionId);
        roleActionRepository.flush();

        List<String> savedActions = new ArrayList<>();
        for (String actionCode : actionCodes) {
            RoleAction roleAction = RoleAction.builder()
                    .id(new RoleActionId(tenantId, UUID.randomUUID()))
                    .roleDefinitionId(roleDefinitionId)
                    .actionCode(actionCode)
                    .accessMode("SHARED")
                    .createdBy(principal.userAccountId())
                    .updatedBy(principal.userAccountId())
                    .build();
            roleActionRepository.save(roleAction);
            savedActions.add(actionCode);
        }

        role.setSensitiveRole(hasSensitiveAction);
        role.setUpdatedBy(principal.userAccountId());
        RoleDefinition saved = roleDefinitionRepository.save(role);

        auditLogService.record(
                AuditEventType.CUSTOM_ROLE_UPDATED,
                null,
                principal.userAccountId().toString(),
                null,
                "Custom role '" + saved.getRoleCode() + "' (" + roleDefinitionId + ") action set updated with " + savedActions.size() + " actions."
        );

        log.info("Custom role '{}' ({}) action set updated in tenant {}", saved.getRoleCode(), roleDefinitionId, tenantId);
        return RoleDetailResponse.from(saved, savedActions);
    }

    @Transactional
    public RoleDetailResponse retireCustomRole(RbacPrincipal principal, UUID roleDefinitionId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(roleDefinitionId, "roleDefinitionId is required");
        UUID tenantId = principal.tenantId();

        Caller caller = authorizeManage(principal);
        RoleDefinition role = loadEditableRole(principal, caller, roleDefinitionId);

        // Addendum Rule 17: Roles must not be hard deleted; status becomes RETIRED
        role.setStatus(RoleDefinitionStatus.RETIRED);
        role.setUpdatedBy(principal.userAccountId());
        RoleDefinition saved = roleDefinitionRepository.save(role);

        List<String> actions = roleActionRepository.findByIdTenantIdAndRoleDefinitionId(tenantId, roleDefinitionId)
                .stream()
                .map(RoleAction::getActionCode)
                .toList();

        auditLogService.record(
                AuditEventType.CUSTOM_ROLE_RETIRED,
                null,
                principal.userAccountId().toString(),
                null,
                "Custom role '" + saved.getRoleCode() + "' (" + roleDefinitionId + ") retired [Rule 17]."
        );

        log.info("Custom role '{}' ({}) retired in tenant {}", saved.getRoleCode(), roleDefinitionId, tenantId);
        return RoleDetailResponse.from(saved, actions);
    }

    // ------------------------------------------------------------------------------------------

    /** Who is acting: their own active scope, the owner level their role sits at, and its institution. */
    private record Caller(RbacScope scope, RoleLevel level, String ownerLevel, UUID institutionId) {
    }

    /** ROLE_CUSTOM_MANAGE at the caller's own active scope (A-03, D3 CR-02). */
    private Caller authorizeManage(RbacPrincipal principal) {
        UserRoleAssignment active = permissionEvaluatorService.activeAssignment(principal).orElse(null);
        RbacScope scope = active != null ? PermissionEvaluatorService.scopeOf(active) : RbacScope.tenant();
        permissionEvaluatorService.authorize(principal, "ROLE_CUSTOM_MANAGE", scope, RbacTarget.NONE);
        if (active == null) {
            return new Caller(scope, null, null, null);
        }
        RoleLevel level = roleDefinitionRepository
                .findById(new RoleDefinitionId(principal.tenantId(), active.getRoleDefinitionId()))
                .map(RoleDefinition::getRoleLevel)
                .orElse(null);
        String ownerLevel = level == null || level == RoleLevel.PLATFORM || level == RoleLevel.EXTERNAL ? null : level.name();
        UUID institutionId = active.getScopeType() == ScopeType.TENANT ? null : active.getInstitutionId();
        return new Caller(scope, level, ownerLevel, institutionId);
    }

    /** D3 CR-02: University Admin builds university-office roles only; College/School Admin institution roles. */
    private static void enforceCustomScope(Caller caller, CustomScope requested) {
        if (caller.level() == null || requested == null) {
            return;
        }
        boolean institutionCaller = caller.institutionId() != null;
        if (institutionCaller && requested != CustomScope.INSTITUTION) {
            throw new BusinessRuleViolationException("CUSTOM_SCOPE_NOT_ALLOWED",
                    "An institution administrator builds institution custom roles only (D3 CR-02).");
        }
        if (!institutionCaller && requested != CustomScope.UNIVERSITY_OFFICE) {
            throw new BusinessRuleViolationException("CUSTOM_SCOPE_NOT_ALLOWED",
                    "A University Admin builds university-office custom roles only (D3 CR-02, V3.3 C4).");
        }
    }

    /**
     * Loads a custom role the caller may edit: missing or owned by another institution gives 404,
     * a role the caller holds gives 403 (CR-04), a role owned by a higher level gives 403
     * (Addendum rule 2), a standard role gives 422.
     */
    private RoleDefinition loadEditableRole(RbacPrincipal principal, Caller caller, UUID roleDefinitionId) {
        RoleDefinition role = roleDefinitionRepository.findById(new RoleDefinitionId(principal.tenantId(), roleDefinitionId))
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleDefinitionId + " was not found."));

        if (caller.institutionId() != null && role.getOwnerInstitutionId() != null
                && !caller.institutionId().equals(role.getOwnerInstitutionId())) {
            // A record from another institution looks missing (Part 9 section 2.1).
            throw new ResourceNotFoundException("Role " + roleDefinitionId + " was not found.");
        }

        boolean holdsIt = permissionEvaluatorService.heldAssignments(principal).stream()
                .anyMatch(a -> roleDefinitionId.equals(a.getRoleDefinitionId()));
        if (holdsIt) {
            throw new UnauthorizedException("You cannot change a role you hold yourself (D3 CR-04).");
        }

        if (caller.ownerLevel() != null && role.getOwnerLevel() != null
                && rank(role.getOwnerLevel()) < rank(caller.ownerLevel())) {
            throw new UnauthorizedException("A role owned by a higher level (" + role.getOwnerLevel()
                    + ") cannot be changed from " + caller.ownerLevel() + " level (Addendum rule 2).");
        }

        if (role.getRoleOrigin() == RoleOrigin.STANDARD) {
            throw new BusinessRuleViolationException("STANDARD_ROLE_IMMUTABLE",
                    "Standard roles cannot be directly modified. Create a custom role based on a template.");
        }
        return role;
    }

    /** Lower number = higher level. COLLEGE and SCHOOL are peers below the university. */
    private static int rank(String ownerLevel) {
        return switch (ownerLevel) {
            case "TENANT" -> 0;
            case "UNIVERSITY" -> 1;
            case "COLLEGE", "SCHOOL" -> 2;
            default -> 3;
        };
    }

    private static List<String> normalise(List<String> actionCodes) {
        if (actionCodes == null) {
            return List.of();
        }
        return actionCodes.stream().map(code -> code.trim().toUpperCase()).distinct().toList();
    }

    private boolean validateAndCollectSensitivity(RbacPrincipal principal, Caller caller, List<String> actions) {
        boolean hasSensitiveAction = false;
        for (String code : actions) {
            if (PermissionEvaluatorService.isLockedForCustomRoles(code)) {
                throw new BusinessRuleViolationException(
                        "LOCKED_ACTION_FORBIDDEN",
                        "Action '" + code + "' is a locked action and cannot be granted in a custom role (C7 / CR-03).");
            }
            ActionCatalog action = actionCatalogRepository.findById(code).orElse(null);
            if (action == null) {
                // CR-01: custom roles are built only from Action List actions; anything else is
                // locked out of every custom role.
                throw new BusinessRuleViolationException(
                        "LOCKED_ACTION_FORBIDDEN",
                        "Action '" + code + "' is not in the approved Action List, so it is a locked action "
                                + "that no custom role can carry (CR-01 / CR-03).");
            }
            if (action.isApprovalAction()) {
                throw new BusinessRuleViolationException(
                        "APPROVAL_ACTION_FORBIDDEN",
                        "Approval action '" + code + "' cannot be granted via custom roles; approval power comes only from chains (C7 / CR-10).");
            }
            if (action.getSensitiveClass() != null) {
                hasSensitiveAction = true;
            }
            PermissionDecision decision = permissionEvaluatorService.can(principal, code, caller.scope(), RbacTarget.NONE);
            if (decision != null && !decision.allowed()) {
                throw new BusinessRuleViolationException(
                        "ACTION_NOT_HELD",
                        "You do not hold action '" + code + "' in your active role, so you cannot grant it (C7 / CR-04).");
            }
        }
        return hasSensitiveAction;
    }
}
