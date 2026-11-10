package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.GlobalExceptionHandler;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.selfservice.NoticeRepository;
import com.edu360.api.selfservice.ProfileController;
import com.edu360.api.selfservice.ProfileService;
import com.edu360.api.selfservice.SelfServiceController;
import com.edu360.api.selfservice.SelfServiceService;
import com.edu360.api.selfservice.UserLeaveBalanceRepository;
import com.edu360.api.selfservice.UserLeaveRequestRepository;
import com.edu360.api.selfservice.UserNotificationRepository;
import com.edu360.api.selfservice.dto.NotificationDto;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;
import com.edu360.api.tenant.TenantContext;

/**
 * A-08: self-service is checked at the user's own scope. A Teacher's role is institution-scoped,
 * so a tenant-wide check (the old behaviour) refused them their own pages. Real
 * PermissionEvaluatorService over real role/action rows (SUBJECT_TEACHER holds SELF_SERVICE_VIEW
 * and PROFILE_EDIT_OWN in ref.standard_role_action).
 */
class SelfServiceScopeA08Test {

    private RbacTestWorld world;
    private ProfileController profileController;
    private SelfServiceController selfServiceController;
    private final UserNotificationRepository notifications = mock(UserNotificationRepository.class);
    private final UUID schoolA = UUID.randomUUID();
    private UUID teacher;
    private UUID otherUser;

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
        ProfileService profileService = new ProfileService(world.userAccountRepository, mock(PlatformUserRepository.class),
                mock(FileObjectRepository.class), mock(LocalFileStorage.class), world.auditLogService);
        profileService.setPermissionEvaluatorService(world.evaluator);
        profileController = new ProfileController(profileService);
        selfServiceController = new SelfServiceController(new SelfServiceService(world.evaluator, notifications,
                mock(UserLeaveBalanceRepository.class), mock(UserLeaveRequestRepository.class), mock(NoticeRepository.class)));

        teacher = world.user("Teacher A");
        world.assign(teacher, "SUBJECT_TEACHER", ScopeType.INSTITUTION, schoolA, null);
        otherUser = world.user("Other Teacher");
        world.assign(otherUser, "SUBJECT_TEACHER", ScopeType.INSTITUTION, schoolA, null);
        assertThat(RbacTestWorld.standardRoleHolds("SUBJECT_TEACHER", "SELF_SERVICE_VIEW")).isTrue();
        assertThat(RbacTestWorld.standardRoleHolds("SUBJECT_TEACHER", "PROFILE_EDIT_OWN")).isTrue();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new TenantPrincipal(world.tenantId, userId, UserType.STAFF, "Teacher A", false), null, List.of());
    }

    @Test
    @DisplayName("REG-A08-1: a Teacher (institution scope) reads and updates their own profile: 200")
    void regA08_1_teacherReadsAndUpdatesOwnProfile() {
        world.signIn(teacher);

        ResponseEntity<ProfileResponse> read = profileController.getProfile(auth(teacher));
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().userAccountId()).isEqualTo(teacher);

        ProfileUpdateRequest update = new ProfileUpdateRequest();
        update.setDisplayName("Teacher A Renamed");
        ResponseEntity<ProfileResponse> updated = profileController.updateProfile(update, auth(teacher));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(world.accounts.get(teacher).getDisplayName()).isEqualTo("Teacher A Renamed");
        assertThat(world.accounts.get(otherUser).getDisplayName()).isEqualTo("Other Teacher");
        assertThat(world.auditRows.get(world.auditRows.size() - 1).getEventType()).isEqualTo(AuditEventType.PROFILE_UPDATED);
        assertThat(world.auditRows.get(world.auditRows.size() - 1).getActorRoleCode()).isEqualTo("SUBJECT_TEACHER");

        // The self-service reads work at the Teacher's own (institution) scope too.
        when(notifications.findByIdTenantIdAndRecipientUserIdOrderByCreatedAtDesc(world.tenantId, teacher)).thenReturn(List.of());
        ResponseEntity<List<NotificationDto>> own = selfServiceController.getNotifications(world.tenantId, auth(teacher));
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("REG-A08-2: a Teacher reads another user's profile through self-service: 404")
    void regA08_2_teacherCannotReadAnotherProfileThroughSelfService() throws Exception {
        world.signIn(teacher);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(profileController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        // Self-service has no "someone else's profile" address: asking for one is not found.
        for (String path : List.of("/api/me/profile/" + otherUser,
                "/api/tenants/" + world.tenantId + "/me/profile/" + otherUser)) {
            mvc.perform(get(path).principal(auth(teacher))).andExpect(status().isNotFound());
        }
        // And the self-service profile only ever returns the caller's own record.
        mvc.perform(get("/api/me/profile").param("userId", otherUser.toString()).principal(auth(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userAccountId").value(teacher.toString()));
        verify(world.userAccountRepository, never()).findById(eq(new UserAccountId(world.tenantId, otherUser)));
        verify(world.userAccountRepository, never()).save(any());
    }
}
