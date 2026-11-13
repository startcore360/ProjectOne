package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RoleAssignmentService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.rbac.dto.CreateUserRequest;
import com.edu360.api.rbac.dto.CreateUserResponse;

/**
 * Coordinates staff login provisioning, profile linkage and optional initial role assignment.
 * Personal email aliases remain disabled for login until an ownership-verification workflow
 * marks them verified.
 */
@Service
public class StaffOnboardingService {
    private final UserAccountService userAccounts;
    private final StaffDirectoryService staffDirectory;
    private final RoleAssignmentService roleAssignments;
    private final PermissionEvaluatorService permissions;
    private final JdbcTemplate jdbc;

    public StaffOnboardingService(UserAccountService userAccounts,
            StaffDirectoryService staffDirectory, RoleAssignmentService roleAssignments,
            PermissionEvaluatorService permissions, JdbcTemplate jdbc) {
        this.userAccounts = userAccounts;
        this.staffDirectory = staffDirectory;
        this.roleAssignments = roleAssignments;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @Transactional
    public StaffOnboardingResponse onboard(RbacPrincipal actor, StaffProfileRequest request) {
        String officialEmail = normalize(request.officialEmail());
        String personalEmail = normalize(request.personalEmail());
        if (officialEmail == null) {
            throw new IllegalArgumentException("Official email is required for staff login.");
        }
        if (officialEmail.equals(personalEmail)) {
            throw new IllegalArgumentException("Official and personal email addresses must be different.");
        }

        var active = permissions.activeAssignment(actor)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException(
                        "An active staff role is required."));
        ScopeType scopeType = active.getScopeType();
        UUID institutionId = active.getInstitutionId();
        UUID departmentId = scopeType == ScopeType.DEPARTMENT ? active.getDepartmentId() : null;

        CreateUserRequest accountRequest = new CreateUserRequest(
                UserType.STAFF,
                request.fullName().trim(),
                officialEmail,
                null,
                request.nationalIdLast4(),
                null,
                scopeType,
                institutionId,
                departmentId);
        CreateUserResponse login = userAccounts.createUser(actor, accountRequest);

        StaffProfileRequest profileRequest = new StaffProfileRequest(
                request.staffCode(), request.fullName(), request.dateOfBirth(),
                personalEmail, request.personalMobile(), request.nationalIdLast4(),
                request.photoFileId(), request.employmentStatus(), request.joinedOn(),
                request.leftOn(), officialEmail, request.initialRoleCode());
        StaffProfileResponse profile = staffDirectory.create(actor, profileRequest, login.userAccountId());

        if (personalEmail != null) {
            jdbc.update("""
                    INSERT INTO app.user_login_alias
                        (tenant_id, user_login_alias_id, user_account_id, email, is_verified,
                         created_by, updated_by)
                    VALUES (?, ?, ?, ?, false, ?, ?)
                    """,
                    actor.tenantId(), UUID.randomUUID(), login.userAccountId(), personalEmail,
                    actor.userAccountId(), actor.userAccountId());
        }

        if (request.initialRoleCode() != null && !request.initialRoleCode().isBlank()) {
            roleAssignments.assignRole(actor, login.userAccountId(),
                    new AssignRoleRequest(null, request.initialRoleCode().trim().toUpperCase(),
                            scopeType, institutionId, departmentId, null, null));
        }

        return new StaffOnboardingResponse(profile, login, personalEmail != null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }
}
