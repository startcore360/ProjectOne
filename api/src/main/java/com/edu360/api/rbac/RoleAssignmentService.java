package com.edu360.api.rbac;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.StaffDirectoryService;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.rbac.dto.RoleDefinitionResponse;
import com.edu360.api.rbac.dto.UserRoleAssignmentResponse;

import lombok.RequiredArgsConstructor;

/**
 * Service managing administrative role assignments and role definitions for tenants:
 * - ROLE_ASSIGN (Sprint 1 Story #14 / WF-27, MX-04)
 *
 * Implements:
 * 1. Single active role per exact scope context (Addendum rule 10).
 * 2. Self-elevation and self-role changes strictly forbidden (Addendum rule 11 / separation of duty).
 * 3. Immediate user logout upon assignment change so rights take effect at next login (WF-27, PQ-20).
 * 4. Soft-ending of assignments to preserve full audit history (Addendum rules 17 & 18).
 */
@Service
@RequiredArgsConstructor
public class RoleAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RoleAssignmentService.class);

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final RoleActionRepository roleActionRepository;
    private final StandardRoleRepository standardRoleRepository;
    private final StandardRoleActionRepository standardRoleActionRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;

    @Autowired(required = false)
    private StaffDirectoryService staffDirectoryService;

    @Autowired(required = false)
    private com.edu360.api.live.LiveEventPublisher liveEventPublisher;

    /**
     * Assigns a role to a user within an authorized scope (ROLE_ASSIGN).
     */
    @Transactional
    public UserRoleAssignmentResponse assignRole(
            RbacPrincipal principal,
            UUID targetUserId,
            AssignRoleRequest request) {

        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(targetUserId, "targetUserId is required");
        Objects.requireNonNull(request, "request is required");

        UUID tenantId = principal.tenantId();

        // 1. Separation of Duty / Addendum Rule 11: Nobody modifies their own roles
        if (principal.userAccountId().equals(targetUserId)) {
            log.warn("Security check: user {} attempted to assign a role to themselves in tenant {} (Addendum rule 11)",
                    principal.userAccountId(), tenantId);
            throw new UnauthorizedException(
                    "A principal may not assign or alter their own roles (Addendum rule 11 / separation of duty)."
            );
        }

        // 2. Authorize caller via PermissionEvaluatorService (ROLE_ASSIGN or ROLE_ASSIGN_NON_SENSITIVE)
        RbacScope actingScope = resolveActingScope(request);
        boolean hasRoleAssign = permissionEvaluatorService
                .can(principal, "ROLE_ASSIGN", actingScope, RbacTarget.of(targetUserId))
                .allowed();

        boolean actingAsNonSensitive = false;
        if (!hasRoleAssign) {
            permissionEvaluatorService.authorize(
                    principal,
                    "ROLE_ASSIGN_NON_SENSITIVE",
                    actingScope,
                    RbacTarget.of(targetUserId)
            );
            actingAsNonSensitive = true;
        }

        // 3. Verify target user exists in this tenant
        var targetAccount = userAccountRepository.findById(new UserAccountId(tenantId, targetUserId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target user " + targetUserId + " does not exist in tenant " + tenantId
                ));

        // 4. Resolve RoleDefinition (from existing tenant role or standard role template)
        RoleDefinition roleDefinition = resolveRoleDefinition(tenantId, principal.userAccountId(), request);

        // Q-02 (DB-A11): a DRAFT, INACTIVE, RETIRED or ARCHIVED role cannot be given to anyone.
        if (roleDefinition.getStatus() != null && roleDefinition.getStatus() != RoleDefinitionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("ROLE_NOT_ACTIVE",
                    "Role " + roleDefinition.getRoleCode() + " is " + roleDefinition.getStatus() + "; only an ACTIVE role can be assigned.");
        }

        // 4b. Rule MX-04 enforcement for non-sensitive role assigners (IT Admin / School IT Support)
        if (actingAsNonSensitive) {
            if (request.scopeType() == ScopeType.TENANT) {
                throw new BusinessRuleViolationException(
                        "MX_04_INSTITUTION_SCOPE_REQUIRED",
                        "Rule MX-04: Non-sensitive role assigners can only assign roles within an institution scope."
                );
            }
            if (roleDefinition.isSensitiveRole()) {
                log.warn("Rule MX-04 violation: user {} attempted to assign sensitive role {} under ROLE_ASSIGN_NON_SENSITIVE in tenant {}",
                        principal.userAccountId(), roleDefinition.getRoleCode(), tenantId);
                throw new BusinessRuleViolationException(
                        "MX_04_SENSITIVE_ROLE_PROHIBITED",
                        "Rule MX-04: Non-sensitive role assigners are prohibited from assigning administrative, leadership, finance, HR, or examination roles."
                );
            }
        }

        // 5. Addendum Rule 10: Single active role per exact scope
        // If an active assignment exists for this exact scope, end it so exactly one is active
        List<UserRoleAssignment> existingActive = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                        tenantId,
                        targetUserId,
                        request.scopeType(),
                        request.institutionId(),
                        request.departmentId(),
                        AssignmentStatus.ACTIVE
                );

        Instant now = Instant.now();
        for (UserRoleAssignment existing : existingActive) {
            existing.setStatus(AssignmentStatus.ENDED);
            existing.setEffectiveTo(now);
            existing.setUpdatedBy(principal.userAccountId());
            userRoleAssignmentRepository.save(existing);
            log.info("Addendum rule 10: ended prior active assignment {} for user {} in scope {}",
                    existing.getId().getUserRoleAssignmentId(), targetUserId, request.scopeType());
        }

        // 6. Create new active assignment
        UUID assignmentId = UUID.randomUUID();
        UserRoleAssignment newAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(tenantId, assignmentId))
                .userAccountId(targetUserId)
                .roleDefinitionId(roleDefinition.getId().getRoleDefinitionId())
                .scopeType(request.scopeType())
                .institutionId(request.institutionId())
                .departmentId(request.departmentId())
                .effectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : now)
                .effectiveTo(request.effectiveTo())
                .status(AssignmentStatus.ACTIVE)
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        UserRoleAssignment saved = userRoleAssignmentRepository.save(newAssignment);
        if (targetAccount.getUserType() == UserType.STAFF
                && request.scopeType() != ScopeType.TENANT
                && staffDirectoryService != null) {
            java.time.LocalDate staffEffectiveFrom = request.effectiveFrom() == null
                    ? java.time.LocalDate.now()
                    : request.effectiveFrom().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            java.time.LocalDate staffEffectiveTo = request.effectiveTo() == null ? null
                    : request.effectiveTo().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            staffDirectoryService.ensureStaffAssignment(tenantId, targetUserId,
                    request.institutionId(), request.departmentId(), principal.userAccountId(),
                    roleDefinition.getRoleName(), staffEffectiveFrom, staffEffectiveTo);
        }
        log.info("Assigned role '{}' ({}) to user {} in tenant {} scope {}",
                roleDefinition.getRoleCode(), roleDefinition.getId().getRoleDefinitionId(),
                targetUserId, tenantId, request.scopeType());

        // 7. Revoke active sessions in Keycloak so new rights take effect at next login (WF-27, PQ-20)
        revokeUserSessions(tenantId, targetUserId);

        // 8. Audit log
        auditLogService.record(
                AuditEventType.ROLE_ASSIGNED,
                null,
                principal.userAccountId().toString(),
                null,
                "Assigned role " + roleDefinition.getRoleCode() + " to user " + targetUserId + " in tenant " + tenantId
        );

        return UserRoleAssignmentResponse.from(saved, roleDefinition);
    }

    /**
     * Ends an existing role assignment (WF-27, PQ-20).
     * Soft-deactivation (status = ENDED) to preserve complete audit history (Addendum rule 17).
     */
    @Transactional
    public UserRoleAssignmentResponse endRoleAssignment(RbacPrincipal principal, UUID assignmentId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(assignmentId, "assignmentId is required");

        UUID tenantId = principal.tenantId();
        UserRoleAssignment assignment = userRoleAssignmentRepository
                .findByIdTenantIdAndIdUserRoleAssignmentId(tenantId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Role assignment " + assignmentId + " not found."));

        // Addendum rule 11: cannot end own assignment
        if (principal.userAccountId().equals(assignment.getUserAccountId())) {
            throw new UnauthorizedException(
                    "A principal may not remove their own role assignment (Addendum rule 11 / separation of duty)."
            );
        }

        // Authorize action (ROLE_ASSIGN or ROLE_ASSIGN_NON_SENSITIVE)
        RbacScope scope = resolveScopeFromAssignment(assignment);
        boolean hasRoleAssign = permissionEvaluatorService
                .can(principal, "ROLE_ASSIGN", scope, RbacTarget.of(assignment.getUserAccountId()))
                .allowed();

        if (!hasRoleAssign) {
            permissionEvaluatorService.authorize(
                    principal,
                    "ROLE_ASSIGN_NON_SENSITIVE",
                    scope,
                    RbacTarget.of(assignment.getUserAccountId())
            );

            RoleDefinition targetRole = roleDefinitionRepository
                    .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()))
                    .orElse(null);
            if (targetRole != null && targetRole.isSensitiveRole()) {
                log.warn("Rule MX-04 violation: user {} attempted to end sensitive role assignment {} ({}) under ROLE_ASSIGN_NON_SENSITIVE in tenant {}",
                        principal.userAccountId(), assignmentId, targetRole.getRoleCode(), tenantId);
                throw new BusinessRuleViolationException(
                        "MX_04_SENSITIVE_ROLE_PROHIBITED",
                        "Rule MX-04: Non-sensitive role assigners are prohibited from altering or ending administrative, leadership, finance, HR, or examination roles."
                );
            }
        }

        // End assignment
        Instant now = Instant.now();
        assignment.setStatus(AssignmentStatus.ENDED);
        assignment.setEffectiveTo(now);
        assignment.setUpdatedBy(principal.userAccountId());
        UserRoleAssignment saved = userRoleAssignmentRepository.save(assignment);

        // Revoke active sessions (WF-27)
        revokeUserSessions(tenantId, assignment.getUserAccountId());

        // Audit log
        auditLogService.record(
                AuditEventType.ROLE_ASSIGNMENT_ENDED,
                null,
                principal.userAccountId().toString(),
                null,
                "Ended role assignment " + assignmentId + " for user " + assignment.getUserAccountId()
        );

        RoleDefinition role = roleDefinitionRepository
                .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()))
                .orElse(null);

        return UserRoleAssignmentResponse.from(saved, role);
    }

    /** D5 listRoleAssignments x-action-codes (any one, at the caller's own active scope). */
    private static final List<String> LIST_ASSIGNMENTS_ACTIONS =
            List.of("USER_VIEW", "ROLE_ASSIGN", "ROLE_ASSIGN_NON_SENSITIVE", "TENANT_OVERSIGHT_VIEW");

    /** D5 listRoles x-action-codes (any one, at the caller's own active scope). */
    private static final List<String> LIST_ROLES_ACTIONS =
            List.of("ROLE_CUSTOM_MANAGE", "ROLE_ASSIGN", "ROLE_ASSIGN_NON_SENSITIVE", "RBAC_MAPPING_CHECK", "TENANT_OVERSIGHT_VIEW");

    /**
     * Lists role assignments for a specific user (A-04, BS-18). The caller may always read their
     * own. For anyone else the caller needs one of D5 listRoleAssignments' actions at their own
     * active scope (403 otherwise), and the target must be a user of the tenant who holds an
     * active assignment inside that scope; a user outside it looks missing (404).
     */
    @Transactional(readOnly = true)
    public List<UserRoleAssignmentResponse> listUserRoleAssignments(RbacPrincipal principal, UUID targetUserId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(targetUserId, "targetUserId is required");

        UUID tenantId = principal.tenantId();
        List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountId(tenantId, targetUserId);

        if (!targetUserId.equals(principal.userAccountId())) {
            RbacScope ownScope = permissionEvaluatorService.ownScope(principal);
            authorizeAny(principal, LIST_ASSIGNMENTS_ACTIONS, ownScope, RbacTarget.of(targetUserId));

            userAccountRepository.findById(new UserAccountId(tenantId, targetUserId))
                    .orElseThrow(() -> new ResourceNotFoundException("User " + targetUserId + " was not found."));
            boolean inScope = ownScope.scopeType() == ScopeType.TENANT
                    || assignments.stream().anyMatch(a -> a.getStatus() == AssignmentStatus.ACTIVE && within(a, ownScope));
            if (!inScope) {
                throw new ResourceNotFoundException("User " + targetUserId + " was not found.");
            }
        }

        List<UserRoleAssignmentResponse> responses = new ArrayList<>();
        for (UserRoleAssignment assignment : assignments) {
            RoleDefinition role = roleDefinitionRepository
                    .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()))
                    .orElse(null);
            responses.add(UserRoleAssignmentResponse.from(assignment, role));
        }
        return responses;
    }

    /**
     * Lists the roles of this tenant (A-04, D5 listRoles): needs one of D5's listRoles actions at
     * the caller's own active scope, and writes nothing. Roles already in app.role_definition are
     * listed as they are; standard roles not yet copied into the tenant are listed from
     * ref.standard_role with no roleDefinitionId (they are copied when first assigned).
     */
    @Transactional(readOnly = true)
    public List<RoleDefinitionResponse> listRoles(RbacPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        UUID tenantId = principal.tenantId();

        authorizeAny(principal, LIST_ROLES_ACTIONS, permissionEvaluatorService.ownScope(principal), RbacTarget.NONE);

        List<RoleDefinition> existing = roleDefinitionRepository.findByIdTenantId(tenantId);
        List<RoleDefinitionResponse> result = new ArrayList<>(existing.stream().map(RoleDefinitionResponse::from).toList());
        java.util.Set<String> copied = new java.util.HashSet<>();
        existing.forEach(r -> copied.add(r.getRoleCode()));
        for (StandardRole sr : standardRoleRepository.findAll()) {
            if (sr.getRoleLevel() != RoleLevel.PLATFORM && !copied.contains(sr.getRoleCode())) {
                result.add(new RoleDefinitionResponse(null, tenantId, sr.getRoleCode(), sr.getRoleName(),
                        sr.getRoleLevel(), RoleOrigin.STANDARD, false, RoleDefinitionStatus.ACTIVE));
            }
        }
        return result;
    }

    /** Passes when any one of the actions is allowed; a missing role choice is a 409, otherwise 403. */
    private void authorizeAny(RbacPrincipal principal, List<String> actions, RbacScope scope, RbacTarget target) {
        PermissionDecision last = null;
        for (String action : actions) {
            PermissionDecision decision = permissionEvaluatorService.can(principal, action, scope, target);
            if (decision != null && decision.allowed()) {
                return;
            }
            if (decision != null) {
                last = decision;
            }
        }
        // Re-run the last action through authorize(...) so the denial takes its standard form
        // (409 ACTIVE_ROLE_REQUIRED or 403) without duplicating that mapping here.
        permissionEvaluatorService.authorize(principal, actions.get(actions.size() - 1), scope, target);
        throw new UnauthorizedException(last != null ? last.message() : "Not allowed.");
    }

    private static boolean within(UserRoleAssignment a, RbacScope scope) {
        if (scope.scopeType() == ScopeType.INSTITUTION) {
            return Objects.equals(a.getInstitutionId(), scope.institutionId());
        }
        if (scope.scopeType() == ScopeType.DEPARTMENT) {
            return Objects.equals(a.getDepartmentId(), scope.departmentId());
        }
        return true;
    }

    private RoleDefinition resolveRoleDefinition(UUID tenantId, UUID actorUserId, AssignRoleRequest request) {
        if (request.roleDefinitionId() != null) {
            return roleDefinitionRepository
                    .findById(new RoleDefinitionId(tenantId, request.roleDefinitionId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Role definition " + request.roleDefinitionId() + " was not found."
                    ));
        }

        if (request.roleCode() != null && !request.roleCode().isBlank()) {
            String roleCode = request.roleCode().trim().toUpperCase();
            List<RoleDefinition> existing = roleDefinitionRepository.findByIdTenantId(tenantId);
            for (RoleDefinition role : existing) {
                if (roleCode.equals(role.getRoleCode()) && role.getStatus() == RoleDefinitionStatus.ACTIVE) {
                    return role;
                }
            }
            // Bootstrap from standard role if not copied yet
            return createRoleDefinitionFromStandard(tenantId, actorUserId, roleCode);
        }

        throw new IllegalArgumentException("Either roleDefinitionId or roleCode must be provided.");
    }

    private RoleDefinition createRoleDefinitionFromStandard(UUID tenantId, UUID actorUserId, String roleCode) {
        StandardRole standard = standardRoleRepository.findById(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Standard role code '" + roleCode + "' does not exist."));

        RoleDefinition roleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                .roleCode(roleCode)
                .roleName(standard.getRoleName())
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(standard.getRoleLevel())
                .baseStandardRole(roleCode)
                .customScope(null)
                .sensitiveRole(false)
                .status(RoleDefinitionStatus.ACTIVE)
                .createdBy(actorUserId)
                .updatedBy(actorUserId)
                .build();

        RoleDefinition saved = roleDefinitionRepository.save(roleDefinition);

        // Copy standard role actions
        List<StandardRoleAction> actions = standardRoleActionRepository.findByRoleCode(roleCode);
        for (StandardRoleAction sa : actions) {
            RoleAction ra = RoleAction.builder()
                    .id(new RoleActionId(tenantId, UUID.randomUUID()))
                    .roleDefinitionId(saved.getId().getRoleDefinitionId())
                    .actionCode(sa.getActionCode())
                    .createdBy(actorUserId)
                    .updatedBy(actorUserId)
                    .build();
            roleActionRepository.save(ra);
        }

        return saved;
    }

    private RbacScope resolveActingScope(AssignRoleRequest request) {
        if (request.scopeType() == ScopeType.TENANT) {
            return RbacScope.tenant();
        }
        if (request.scopeType() == ScopeType.INSTITUTION) {
            if (request.institutionId() == null) {
                throw new IllegalArgumentException("institutionId is required when scopeType is INSTITUTION.");
            }
            return RbacScope.institution(request.institutionId());
        }
        if (request.scopeType() == ScopeType.DEPARTMENT) {
            if (request.institutionId() == null || request.departmentId() == null) {
                throw new IllegalArgumentException("institutionId and departmentId are required when scopeType is DEPARTMENT.");
            }
            return RbacScope.department(request.institutionId(), request.departmentId());
        }
        return RbacScope.tenant();
    }

    private RbacScope resolveScopeFromAssignment(UserRoleAssignment assignment) {
        if (assignment.getScopeType() == ScopeType.INSTITUTION && assignment.getInstitutionId() != null) {
            return RbacScope.institution(assignment.getInstitutionId());
        }
        if (assignment.getScopeType() == ScopeType.DEPARTMENT && assignment.getDepartmentId() != null) {
            return RbacScope.department(assignment.getInstitutionId(), assignment.getDepartmentId());
        }
        return RbacScope.tenant();
    }

    /**
     * B3 / I-01 (REG-I01-6): keeps Keycloak's mfa_required attribute in line with the user's
     * roles after every assign/end. Field-injected and optional so this class's constructor (and
     * its tests) stay unchanged; if Keycloak is unreachable the sign-in check in AuthService
     * corrects the attribute before any token is issued.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.edu360.api.auth.MfaRequirementService mfaRequirementService;

    private void syncTwoStepRequirement(UUID tenantId, UUID userId) {
        if (mfaRequirementService == null) {
            return;
        }
        try {
            mfaRequirementService.syncTenantUser(tenantId, userId);
        } catch (Exception ex) {
            log.warn("Two-step login flag not updated for user {} in tenant {} (corrected at next sign-in): {}",
                    userId, tenantId, ex.getMessage());
        }
    }

    private void revokeUserSessions(UUID tenantId, UUID userId) {
        syncTwoStepRequirement(tenantId, userId);
        try {
            userIdentityLinkRepository
                    .findFirstByIdTenantIdAndUserAccountIdAndStatus(tenantId, userId, IdentityLinkStatus.ACTIVE)
                    .ifPresent(link -> keycloakAdminClient.logoutUser(link.getIdentityRealm(), link.getIdentitySubject()));
        } catch (Exception ex) {
            log.warn("Session logout skipped or non-fatal for user {} in tenant {}: {}", userId, tenantId, ex.getMessage());
        }
        if (liveEventPublisher != null) {
            liveEventPublisher.publishToUser(tenantId, userId, "PERMISSIONS_CHANGED",
                    java.util.Map.of("reason", "ROLE_ASSIGNMENT_CHANGED"));
            liveEventPublisher.publishToUser(tenantId, userId, "SESSION_REVOKED",
                    java.util.Map.of("reason", "ROLE_ASSIGNMENT_CHANGED"));
        }
    }

    /**
     * Determines whether a given role is sensitive per Rule MX-04.
     * Non-sensitive roles are strictly limited to operational/support roles (e.g., Office Staff,
     * Library Assistant, Driver, Security, Hostel Staff). Roles with administrative, leadership,
     * financial, human resources, or examination/grading powers are sensitive and forbidden to
     * non-sensitive assigners.
     */
    public static boolean isSensitiveRole(String roleCode) {
        if (roleCode == null) return true;
        String code = roleCode.toUpperCase().trim();
        return code.contains("ADMIN")
                || code.contains("PRINCIPAL")
                || code.contains("HOD")
                || code.contains("HEAD_OF_DEPARTMENT")
                || code.contains("ACCOUNTANT")
                || code.contains("FINANCE")
                || code.contains("HR")
                || code.contains("PAYROLL")
                || code.contains("EXAM")
                || code.contains("REGISTRAR")
                || code.contains("CHANCELLOR")
                || code.contains("MANAGEMENT")
                || code.contains("GOVERN")
                || code.contains("PROTECTION")
                || code.contains("TEACHER")
                || code.contains("PROFESSOR")
                || code.contains("LECTURER")
                || code.contains("DEAN")
                || code.contains("IT_SUPPORT");
    }
}
