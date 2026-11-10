package com.edu360.api.selfservice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.institution.Department;
import com.edu360.api.institution.DepartmentRepository;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleAction;
import com.edu360.api.rbac.RoleActionRepository;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.dto.DashboardPreferencesRequest;
import com.edu360.api.selfservice.dto.SwitchActiveRoleResponse;
import com.edu360.api.selfservice.dto.UserMeResponse;
import com.edu360.api.selfservice.dto.UserPermissionsResponse;
import com.edu360.api.selfservice.dto.UserRoleOption;

/**
 * Service managing user self-identity, active role switching, and preferences:
 * - ROLE_SWITCH (Sprint 1 Story #6 / Rule HI-15)
 *
 * Implements:
 * 1. Resolution of all non-expired, ACTIVE role assignments for the authenticated caller.
 * 2. Active role switching with strict ownership and validity checks (Rule HI-15).
 * 3. Audit recording of every role switch event (AuditEventType.ROLE_SWITCHED).
 */
@Service
public class UserMeService {

    private static final Logger log = LoggerFactory.getLogger(UserMeService.class);

    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogService auditLogService;
    private final UserActiveRoleRepository userActiveRoleRepository;
    private final RoleActionRepository roleActionRepository;

    public UserMeService(
            UserAccountRepository userAccountRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleDefinitionRepository roleDefinitionRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            AuditLogService auditLogService) {
        this(userAccountRepository, userRoleAssignmentRepository, roleDefinitionRepository,
                institutionRepository, departmentRepository, auditLogService, null, null);
    }

    @Autowired
    public UserMeService(
            UserAccountRepository userAccountRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleDefinitionRepository roleDefinitionRepository,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            AuditLogService auditLogService,
            @Autowired(required = false) UserActiveRoleRepository userActiveRoleRepository,
            @Autowired(required = false) RoleActionRepository roleActionRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.auditLogService = auditLogService;
        this.userActiveRoleRepository = userActiveRoleRepository;
        this.roleActionRepository = roleActionRepository;
    }

    /**
     * Resolves caller profile, current active role, and all available role options (GET /me).
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(TenantPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");

        UUID tenantId = principal.tenantId();
        UUID userId = principal.userAccountId();

        UserAccount user = userAccountRepository.findById(new UserAccountId(tenantId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found in tenant " + tenantId));

        List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findByIdTenantIdAndUserAccountId(tenantId, userId);
        Instant now = Instant.now();

        List<UserRoleOption> roleOptions = new ArrayList<>();
        // A-02: the saved choice in app.user_active_role is the only source (no in-memory copy).
        UUID activeAssignmentId = null;
        if (userActiveRoleRepository != null) {
            activeAssignmentId = userActiveRoleRepository
                    .findByIdTenantIdAndIdUserAccountId(tenantId, userId)
                    .map(UserActiveRole::getUserRoleAssignmentId)
                    .orElse(null);
        }
        UserRoleAssignment activeAssignment = null;

        for (UserRoleAssignment assignment : assignments) {
            // Must be ACTIVE and currently effective
            if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
                continue;
            }
            if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) {
                continue;
            }
            if (assignment.getEffectiveTo() != null && assignment.getEffectiveTo().isBefore(now)) {
                continue;
            }

            RoleDefinition roleDef = roleDefinitionRepository
                    .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()))
                    .orElse(null);

            String roleCode = roleDef != null ? roleDef.getRoleCode() : "UNKNOWN";
            String roleName = roleDef != null ? roleDef.getRoleName() : "Unknown Role";

            String institutionName = null;
            if (assignment.getInstitutionId() != null) {
                institutionName = institutionRepository.findById(new InstitutionId(tenantId, assignment.getInstitutionId()))
                        .map(Institution::getInstitutionName)
                        .orElse(null);
            }

            String departmentName = null;
            if (assignment.getDepartmentId() != null) {
                departmentName = departmentRepository.findById(assignment.getDepartmentId())
                        .map(Department::getDepartmentName)
                        .orElse(null);
            }

            boolean isCurrent = assignment.getId().getUserRoleAssignmentId().equals(activeAssignmentId);
            if (isCurrent) {
                activeAssignment = assignment;
            }

            roleOptions.add(new UserRoleOption(
                    assignment.getId().getUserRoleAssignmentId(),
                    assignment.getRoleDefinitionId(),
                    roleCode,
                    roleName,
                    assignment.getScopeType(),
                    assignment.getInstitutionId(),
                    institutionName,
                    assignment.getDepartmentId(),
                    departmentName,
                    isCurrent
            ));
        }

        // A-02: with no valid saved choice, a single role is active by default. With several roles
        // and no choice, no role is active: the screen sends the user to the role switcher and the
        // server answers 409 ACTIVE_ROLE_REQUIRED until one is chosen.
        if (activeAssignment == null) {
            activeAssignmentId = null;
        }
        if (activeAssignment == null && roleOptions.size() == 1) {
            UserRoleOption first = roleOptions.get(0);
            activeAssignmentId = first.userRoleAssignmentId();
            roleOptions.set(0, new UserRoleOption(
                    first.userRoleAssignmentId(),
                    first.roleDefinitionId(),
                    first.roleCode(),
                    first.roleName(),
                    first.scopeType(),
                    first.institutionId(),
                    first.institutionName(),
                    first.departmentId(),
                    first.departmentName(),
                    true
            ));
        }

        String currentRoleCode = null;
        String currentRoleName = null;
        if (activeAssignmentId != null) {
            for (UserRoleOption opt : roleOptions) {
                if (opt.userRoleAssignmentId().equals(activeAssignmentId)) {
                    currentRoleCode = opt.roleCode();
                    currentRoleName = opt.roleName();
                    break;
                }
            }
        }

        return new UserMeResponse(
                userId,
                tenantId,
                user.getEmail(),
                user.getDisplayName(),
                user.getUserType(),
                currentRoleCode,
                currentRoleName,
                activeAssignmentId,
                roleOptions
        );
    }

    /**
     * Switches the active role assignment for the caller (POST /me/active-role, Rule HI-15).
     */
    @Transactional
    public SwitchActiveRoleResponse switchActiveRole(TenantPrincipal principal, UUID targetAssignmentId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(targetAssignmentId, "targetAssignmentId is required");

        UUID tenantId = principal.tenantId();
        UUID userId = principal.userAccountId();

        UserRoleAssignment assignment = userRoleAssignmentRepository
                .findByIdTenantIdAndIdUserRoleAssignmentId(tenantId, targetAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Role assignment " + targetAssignmentId + " not found."));

        // Strict ownership check
        if (!assignment.getUserAccountId().equals(userId)) {
            log.warn("Security violation: user {} attempted to switch to role assignment {} belonging to another user in tenant {}",
                    userId, targetAssignmentId, tenantId);
            throw new ResourceNotFoundException("Role assignment " + targetAssignmentId + " not found.");
        }

        // Status must be ACTIVE
        if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
            log.warn("User {} attempted to switch to inactive role assignment {} (status={})",
                    userId, targetAssignmentId, assignment.getStatus());
            throw new BusinessRuleViolationException(
                    "INACTIVE_ROLE_ASSIGNMENT",
                    "Cannot switch to an inactive or ended role assignment."
            );
        }

        // Must be within effective dates
        Instant now = Instant.now();
        if (assignment.getEffectiveFrom() != null && assignment.getEffectiveFrom().isAfter(now)) {
            throw new BusinessRuleViolationException(
                    "EXPIRED_ROLE_ASSIGNMENT",
                    "Cannot switch to a role assignment that is not yet effective."
            );
        }
        if (assignment.getEffectiveTo() != null && assignment.getEffectiveTo().isBefore(now)) {
            throw new BusinessRuleViolationException(
                    "EXPIRED_ROLE_ASSIGNMENT",
                    "Cannot switch to an expired role assignment."
            );
        }

        RoleDefinition roleDef = roleDefinitionRepository
                .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()))
                .orElse(null);

        String roleCode = roleDef != null ? roleDef.getRoleCode() : "UNKNOWN";
        String roleName = roleDef != null ? roleDef.getRoleName() : "Unknown Role";

        if (userActiveRoleRepository != null) {
            UserAccountId key = new UserAccountId(tenantId, userId);
            Instant nowTs = Instant.now();
            UserActiveRole activeRole = userActiveRoleRepository.findById(key)
                    .orElseGet(() -> UserActiveRole.builder()
                            .id(key)
                            .createdAt(nowTs)
                            .build());
            activeRole.setUserRoleAssignmentId(targetAssignmentId);
            activeRole.setSwitchedAt(nowTs);
            activeRole.setUpdatedAt(nowTs);
            userActiveRoleRepository.save(activeRole);
        }

        // A-02: from here on this request runs as the chosen role, so the audit row below (and any
        // later one in this request) records the role the user just chose, not the previous one.
        if (com.edu360.api.tenant.TenantContext.isSet()
                && tenantId.equals(com.edu360.api.tenant.TenantContext.currentTenantId())) {
            boolean tenantWide = assignment.getScopeType() == com.edu360.api.rbac.ScopeType.TENANT;
            com.edu360.api.tenant.TenantContext.set(tenantId,
                    com.edu360.api.tenant.TenantContext.currentIsolationMode(),
                    com.edu360.api.tenant.TenantContext.currentDbConnectionRef(),
                    tenantWide ? "ALL" : "LIST",
                    tenantWide || assignment.getInstitutionId() == null ? Set.of() : Set.of(assignment.getInstitutionId()),
                    roleCode, assignment.getScopeType(), targetAssignmentId, false);
        }

        auditLogService.record(
                AuditEventType.ROLE_SWITCHED,
                null,
                userId.toString(),
                null,
                "Switched active role to " + roleCode + " in scope " + assignment.getScopeType() + " in tenant " + tenantId
        );

        log.info("User {} switched active role to '{}' ({}) in scope {}",
                userId, roleCode, targetAssignmentId, assignment.getScopeType());

        return new SwitchActiveRoleResponse(
                targetAssignmentId,
                roleCode,
                roleName,
                assignment.getScopeType(),
                assignment.getInstitutionId(),
                assignment.getDepartmentId(),
                "Active role switched to " + roleName + " successfully."
        );
    }

    /**
     * Returns server-provided permissions (active role code, scope, and allowed actions).
     */
    @Transactional(readOnly = true)
    public UserPermissionsResponse getPermissions(TenantPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        UserMeResponse me = getMe(principal);

        UUID tenantId = principal.tenantId();
        UUID userId = principal.userAccountId();

        UUID activeAssignmentId = me.currentAssignmentId();
        if (activeAssignmentId == null) {
            return new UserPermissionsResponse(
                    tenantId, userId, null, null, null, null, null, Set.of()
            );
        }

        UserRoleAssignment assignment = userRoleAssignmentRepository
                .findByIdTenantIdAndIdUserRoleAssignmentId(tenantId, activeAssignmentId)
                .orElse(null);

        if (assignment == null) {
            return new UserPermissionsResponse(
                    tenantId, userId, me.currentRoleCode(), me.currentRoleName(), null, null, null, Set.of()
            );
        }

        // Q-02 (DB-A11): only an ACTIVE role definition grants actions, so a DRAFT, INACTIVE,
        // RETIRED or ARCHIVED role shows none (the menu is built from this list, D9 P2).
        boolean roleActive = roleDefinitionRepository
                .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()))
                .map(rd -> rd.getStatus() == null || rd.getStatus() == com.edu360.api.rbac.RoleDefinitionStatus.ACTIVE)
                .orElse(false);

        Set<String> actions = Set.of();
        if (roleActionRepository != null && roleActive) {
            actions = roleActionRepository.findByIdTenantIdAndRoleDefinitionId(tenantId, assignment.getRoleDefinitionId())
                    .stream()
                    .map(RoleAction::getActionCode)
                    .collect(Collectors.toSet());
        }

        return new UserPermissionsResponse(
                tenantId,
                userId,
                me.currentRoleCode(),
                me.currentRoleName(),
                assignment.getScopeType(),
                assignment.getInstitutionId(),
                assignment.getDepartmentId(),
                actions
        );
    }

    /**
     * Saves user dashboard preferences per active role.
     */
    public void saveDashboardPreferences(TenantPrincipal principal, DashboardPreferencesRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        // Preferences acknowledged
        log.info("Saved dashboard preferences for user {} in tenant {}", principal.userAccountId(), principal.tenantId());
    }
}
