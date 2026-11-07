package com.edu360.api.platformops.role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.role.dto.CreatePlatformRoleRequest;
import com.edu360.api.platformops.role.dto.PlatformRoleResponse;
import com.edu360.api.platformops.role.dto.RoleActionItem;
import com.edu360.api.platformops.role.dto.UpdatePlatformRoleRequest;

import lombok.RequiredArgsConstructor;

/**
 * Custom platform roles. Choosing a template copies its actions into a draft and grants nothing
 * (D3 CR-07). Saving, activating and assigning are separate audited steps (CR-08). Editing a
 * template never changes a role that already exists (OD-62).
 */
@Service
@RequiredArgsConstructor
public class PlatformCustomRoleService {

    private static final String ACTION = "PLATFORM_USER_MANAGE";
    private static final String ENTITY = "platform_role";

    private final PlatformCustomRoleRepository roleRepository;
    private final PlatformCustomRoleActionRepository actionRepository;
    private final PlatformRoleTemplateService templateService;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformAuditWriter auditWriter;

    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public List<PlatformRoleResponse> listRoles() {
        List<PlatformCustomRole> roles = roleRepository.findAllByOrderByRoleNameAsc();
        Map<UUID, List<PlatformCustomRoleAction>> actions = actionRepository
                .findByPlatformRoleIdIn(roles.stream().map(PlatformCustomRole::getPlatformRoleId).toList()).stream()
                .collect(Collectors.groupingBy(PlatformCustomRoleAction::getPlatformRoleId));
        return roles.stream()
                .map(role -> toResponse(role, actions.getOrDefault(role.getPlatformRoleId(), List.of())))
                .toList();
    }

    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public PlatformRoleResponse getRole(UUID roleId) {
        PlatformCustomRole role = requireRole(roleId);
        return toResponse(role, actionRepository.findByPlatformRoleId(roleId));
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleResponse createRole(CreatePlatformRoleRequest request, PlatformPrincipal actor) {
        String name = request.roleName().trim();
        PlatformActionPolicy.requireCustomisable(request.basePlatformRole());
        validateActions(request.actions(), request.basePlatformRole());
        if (roleRepository.existsByRoleNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A platform role named '" + name + "' already exists.");
        }
        if (request.updateTemplate() && request.sourceTemplateId() == null) {
            throw new BusinessRuleViolationException("Choose a template before saving these permissions to it.");
        }
        if (request.sourceTemplateId() != null) {
            templateService.requireUsable(request.sourceTemplateId());
        }

        UUID roleId = UUID.randomUUID();
        PlatformCustomRole role = roleRepository.save(PlatformCustomRole.builder()
                .platformRoleId(roleId)
                .roleName(name)
                .basePlatformRole(request.basePlatformRole())
                .sourceTemplateId(request.sourceTemplateId())
                .status(PlatformCustomRoleStatus.DRAFT)
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
        List<PlatformCustomRoleAction> saved = saveActions(roleId, request.actions(), actor);
        auditWriter.record(actor, "CREATE", ACTION, ENTITY, roleId);

        if (request.updateTemplate()) {
            templateService.replaceActions(request.sourceTemplateId(),
                    request.actions().stream().map(RoleActionItem::actionCode).toList(), actor);
        }
        return toResponse(role, saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleResponse updateRole(UUID roleId, UpdatePlatformRoleRequest request, PlatformPrincipal actor) {
        PlatformCustomRole role = requireRole(roleId);
        if (role.getStatus() == PlatformCustomRoleStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("An archived role can't be changed.");
        }
        validateActions(request.actions(), role.getBasePlatformRole());
        if (role.getStatus() == PlatformCustomRoleStatus.ACTIVE && request.actions().isEmpty()) {
            throw new BusinessRuleViolationException("An active role needs at least one permission. Deactivate it instead.");
        }
        String name = request.roleName().trim();
        if (roleRepository.existsByRoleNameIgnoreCaseAndPlatformRoleIdNot(name, roleId)) {
            throw new DuplicateResourceException("A platform role named '" + name + "' already exists.");
        }

        role.setRoleName(name);
        role.setUpdatedBy(actor.platformUserId());
        roleRepository.save(role);
        actionRepository.deleteByPlatformRoleId(roleId);
        actionRepository.flush();
        List<PlatformCustomRoleAction> saved = saveActions(roleId, request.actions(), actor);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, roleId);
        return toResponse(role, saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleResponse activate(UUID roleId, PlatformPrincipal actor) {
        PlatformCustomRole role = requireRole(roleId);
        if (role.getStatus() != PlatformCustomRoleStatus.DRAFT && role.getStatus() != PlatformCustomRoleStatus.INACTIVE) {
            throw new BusinessRuleViolationException("Only a draft or inactive role can be activated.");
        }
        List<PlatformCustomRoleAction> actions = actionRepository.findByPlatformRoleId(roleId);
        if (actions.isEmpty()) {
            throw new BusinessRuleViolationException("Add at least one permission before activating this role.");
        }
        return changeStatus(role, PlatformCustomRoleStatus.ACTIVE, actions, actor);
    }

    /** Everyone holding the role loses its rights at once, because rights are read on each request. */
    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleResponse deactivate(UUID roleId, PlatformPrincipal actor) {
        PlatformCustomRole role = requireRole(roleId);
        if (role.getStatus() != PlatformCustomRoleStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Only an active role can be deactivated.");
        }
        return changeStatus(role, PlatformCustomRoleStatus.INACTIVE, actionRepository.findByPlatformRoleId(roleId), actor);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleResponse archive(UUID roleId, PlatformPrincipal actor) {
        PlatformCustomRole role = requireRole(roleId);
        if (role.getStatus() == PlatformCustomRoleStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("This role is already archived.");
        }
        if (holders(roleId) > 0) {
            throw new BusinessRuleViolationException("Move everyone off this role before archiving it.");
        }
        return changeStatus(role, PlatformCustomRoleStatus.ARCHIVED, actionRepository.findByPlatformRoleId(roleId), actor);
    }

    /** Removes one permission from the role, whichever way it was given (TPA-02). */
    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleResponse takeBack(UUID roleId, String actionCode, PlatformPrincipal actor) {
        PlatformCustomRole role = requireRole(roleId);
        if (role.getStatus() == PlatformCustomRoleStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("An archived role can't be changed.");
        }
        List<PlatformCustomRoleAction> actions = new ArrayList<>(actionRepository.findByPlatformRoleId(roleId));
        PlatformCustomRoleAction action = actions.stream()
                .filter(a -> a.getActionCode().equals(actionCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("This role does not hold " + actionCode + "."));
        if (role.getStatus() == PlatformCustomRoleStatus.ACTIVE && actions.size() == 1) {
            throw new BusinessRuleViolationException("This is the role's last permission. Deactivate the role instead.");
        }
        actionRepository.delete(action);
        actions.remove(action);
        role.setUpdatedBy(actor.platformUserId());
        roleRepository.save(role);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, roleId);
        return toResponse(role, actions);
    }

    private PlatformRoleResponse changeStatus(PlatformCustomRole role, PlatformCustomRoleStatus status,
            List<PlatformCustomRoleAction> actions, PlatformPrincipal actor) {
        role.setStatus(status);
        role.setUpdatedBy(actor.platformUserId());
        roleRepository.save(role);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, role.getPlatformRoleId());
        return toResponse(role, actions);
    }

    private void validateActions(List<RoleActionItem> actions, com.edu360.api.platform.PlatformRole base) {
        Set<String> seen = new HashSet<>();
        for (RoleActionItem item : actions) {
            if (!seen.add(item.actionCode())) {
                throw new BusinessRuleViolationException(item.actionCode() + " is listed more than once.");
            }
        }
        PlatformActionPolicy.validate(seen, base);
    }

    private List<PlatformCustomRoleAction> saveActions(UUID roleId, List<RoleActionItem> actions, PlatformPrincipal actor) {
        return actionRepository.saveAll(actions.stream()
                .map(item -> PlatformCustomRoleAction.builder()
                        .platformRoleActionId(UUID.randomUUID())
                        .platformRoleId(roleId)
                        .actionCode(item.actionCode())
                        .accessMode(item.accessMode())
                        .createdBy(actor.platformUserId())
                        .build())
                .toList());
    }

    private PlatformCustomRole requireRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform role " + roleId + " was not found."));
    }

    private long holders(UUID roleId) {
        return platformUserRepository.countByPlatformRoleIdAndStatus(roleId, PlatformUserStatus.ACTIVE);
    }

    private PlatformRoleResponse toResponse(PlatformCustomRole role, List<PlatformCustomRoleAction> actions) {
        return new PlatformRoleResponse(
                role.getPlatformRoleId(),
                role.getRoleName(),
                role.getBasePlatformRole(),
                role.getSourceTemplateId(),
                role.getStatus(),
                actions.stream()
                        .sorted(Comparator.comparing(PlatformCustomRoleAction::getActionCode))
                        .map(a -> new RoleActionItem(a.getActionCode(), a.getAccessMode()))
                        .toList(),
                holders(role.getPlatformRoleId()),
                role.getCreatedAt());
    }
}
