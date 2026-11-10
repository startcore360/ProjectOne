package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.RoleDefinitionResponse;
import com.edu360.api.rbac.dto.UserRoleAssignmentResponse;
import com.edu360.api.tenant.TenantContext;

/**
 * A-04 (BS-18): role and assignment reads are authorised. Real PermissionEvaluatorService over
 * real role/action rows; D5 listRoleAssignments / listRoles action lists.
 */
class RoleReadA04Test {

    private RbacTestWorld world;
    private RoleAssignmentController controller;
    private final UUID collegeA = UUID.randomUUID();
    private final UUID collegeB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
        RoleAssignmentService service = new RoleAssignmentService(world.userRoleAssignmentRepository,
                world.roleDefinitionRepository, world.roleActionRepository, world.standardRoleRepository,
                mock(StandardRoleActionRepository.class), world.userAccountRepository,
                mock(UserIdentityLinkRepository.class), world.evaluator, mock(KeycloakAdminClient.class),
                world.auditLogService);
        controller = new RoleAssignmentController(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new TenantPrincipal(world.tenantId, userId, UserType.STAFF, "user", false), null, List.of());
    }

    @Test
    @DisplayName("REG-A04-1: self reads own assignments: 200")
    void regA04_1_selfReadsOwnAssignments() {
        UUID teacher = world.user("Teacher");
        UserRoleAssignment own = world.assign(teacher, "SUBJECT_TEACHER", ScopeType.INSTITUTION, collegeA, null);
        world.signIn(teacher);

        ResponseEntity<List<UserRoleAssignmentResponse>> response =
                controller.listUserRoleAssignments(world.tenantId, teacher, auth(teacher));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(UserRoleAssignmentResponse::userRoleAssignmentId)
                .containsExactly(own.getId().getUserRoleAssignmentId());
    }

    @Test
    @DisplayName("REG-A04-2: a Teacher reads another user's assignments: 403")
    void regA04_2_teacherCannotReadOthers() {
        UUID teacher = world.user("Teacher");
        world.assign(teacher, "SUBJECT_TEACHER", ScopeType.INSTITUTION, collegeA, null);
        UUID colleague = world.user("Colleague");
        world.assign(colleague, "SUBJECT_TEACHER", ScopeType.INSTITUTION, collegeA, null);
        for (String code : List.of("USER_VIEW", "ROLE_ASSIGN", "ROLE_ASSIGN_NON_SENSITIVE", "TENANT_OVERSIGHT_VIEW")) {
            assertThat(RbacTestWorld.standardRoleHolds("SUBJECT_TEACHER", code)).as(code).isFalse();
        }
        world.signIn(teacher);

        assertThatThrownBy(() -> controller.listUserRoleAssignments(world.tenantId, colleague, auth(teacher)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("REG-A04-3: College Admin A reads a user in college B: 404")
    void regA04_3_collegeAdminCannotReadOtherCollege() {
        UUID adminA = world.user("College Admin A");
        world.assign(adminA, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        UUID staffB = world.user("Staff B");
        world.assign(staffB, "OFFICE_STAFF", ScopeType.INSTITUTION, collegeB, null);
        assertThat(RbacTestWorld.standardRoleHolds("COLLEGE_ADMIN", "USER_VIEW")).isTrue();
        world.signIn(adminA);

        assertThatThrownBy(() -> controller.listUserRoleAssignments(world.tenantId, staffB, auth(adminA)))
                .isInstanceOf(ResourceNotFoundException.class);

        // ...while a user of college A is readable.
        UUID staffA = world.user("Staff A");
        world.assign(staffA, "OFFICE_STAFF", ScopeType.INSTITUTION, collegeA, null);
        assertThat(controller.listUserRoleAssignments(world.tenantId, staffA, auth(adminA)).getBody()).hasSize(1);
    }

    @Test
    @DisplayName("REG-A04-4: University Admin reads any user in the tenant: 200")
    void regA04_4_universityAdminReadsAnyUser() {
        UUID uniAdmin = world.user("University Admin");
        world.assign(uniAdmin, "UNIVERSITY_ADMIN", ScopeType.TENANT, null, null);
        UUID staffB = world.user("Staff B");
        world.assign(staffB, "OFFICE_STAFF", ScopeType.INSTITUTION, collegeB, null);
        world.signIn(uniAdmin);

        ResponseEntity<List<UserRoleAssignmentResponse>> response =
                controller.listUserRoleAssignments(world.tenantId, staffB, auth(uniAdmin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).institutionId()).isEqualTo(collegeB);
    }

    @Test
    @DisplayName("REG-A04-5: GET listRoles writes nothing (repository save never called)")
    void regA04_5_listRolesWritesNothing() {
        UUID adminA = world.user("College Admin A");
        world.assign(adminA, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        world.signIn(adminA);
        int rolesBefore = world.roles.size();
        int actionsBefore = world.roleActions.size();

        ResponseEntity<List<RoleDefinitionResponse>> response = controller.listRoles(world.tenantId, auth(adminA));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(RoleDefinitionResponse::roleCode).contains("COLLEGE_ADMIN", "PROFESSOR");
        verify(world.roleDefinitionRepository, never()).save(any());
        verify(world.roleActionRepository, never()).save(any());
        assertThat(world.roles).hasSize(rolesBefore);
        assertThat(world.roleActions).hasSize(actionsBefore);

        // ...and it is still an authorised read: a Teacher is refused.
        UUID teacher = world.user("Teacher");
        world.assign(teacher, "SUBJECT_TEACHER", ScopeType.INSTITUTION, collegeA, null);
        world.signIn(teacher);
        assertThatThrownBy(() -> controller.listRoles(world.tenantId, auth(teacher)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
