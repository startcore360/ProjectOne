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
import com.edu360.api.rbac.dto.CreateCustomRoleRequest;
import com.edu360.api.rbac.dto.RoleDetailResponse;
import com.edu360.api.rbac.dto.UpdateCustomRoleRequest;
import com.edu360.api.rbac.dto.UpdateRoleActionSetRequest;

import lombok.RequiredArgsConstructor;

/**
 * Service managing custom role creation, inspection, and retirement (Story #16: ROLE_CUSTOM_MANAGE).
 * Implements Addendum rules 3, 4, and 17 (no hard delete, retired terminal state).
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

        permissionEvaluatorService.authorize(principal, "ROLE_CUSTOM_MANAGE", RbacScope.tenant(), RbacTarget.NONE);

        String roleCode = request.roleCode().trim().toUpperCase();
        if (roleDefinitionRepository.existsByIdTenantIdAndRoleCode(tenantId, roleCode)) {
            throw new DuplicateResourceException("Role code '" + roleCode + "' already exists in this tenant.");
        }

        List<String> actions = request.actionCodes() != null ? request.actionCodes() : List.of();
        boolean hasSensitiveAction = false;
        for (String actionCode : actions) {
            ActionCatalog action = actionCatalogRepository.findById(actionCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Action code '" + actionCode + "' is not recognized in the action catalog."));
            if (action.getSensitiveClass() != null || action.isApprovalAction()) {
                hasSensitiveAction = true;
            }
        }

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

        permissionEvaluatorService.authorize(principal, "ROLE_CUSTOM_MANAGE", RbacScope.tenant(), RbacTarget.NONE);

        RoleDefinition role = roleDefinitionRepository.findById(new RoleDefinitionId(tenantId, roleDefinitionId))
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleDefinitionId + " was not found."));

        if (role.getRoleOrigin() == RoleOrigin.STANDARD) {
            throw new BusinessRuleViolationException("STANDARD_ROLE_IMMUTABLE",
                    "Standard roles cannot be directly modified. Create a custom role based on a template.");
        }

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

        permissionEvaluatorService.authorize(principal, "ROLE_CUSTOM_MANAGE", RbacScope.tenant(), RbacTarget.NONE);

        RoleDefinition role = roleDefinitionRepository.findById(new RoleDefinitionId(tenantId, roleDefinitionId))
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleDefinitionId + " was not found."));

        if (role.getRoleOrigin() == RoleOrigin.STANDARD) {
            throw new BusinessRuleViolationException("STANDARD_ROLE_IMMUTABLE", "Standard roles cannot be directly modified.");
        }

        List<String> actionCodes = request.actionCodes() != null ? request.actionCodes() : List.of();
        boolean hasSensitiveAction = false;
        for (String actionCode : actionCodes) {
            ActionCatalog action = actionCatalogRepository.findById(actionCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Action code '" + actionCode + "' is not recognized in the action catalog."));
            if (action.getSensitiveClass() != null || action.isApprovalAction()) {
                hasSensitiveAction = true;
            }
        }

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

        permissionEvaluatorService.authorize(principal, "ROLE_CUSTOM_MANAGE", RbacScope.tenant(), RbacTarget.NONE);

        RoleDefinition role = roleDefinitionRepository.findById(new RoleDefinitionId(tenantId, roleDefinitionId))
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleDefinitionId + " was not found."));

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
}
