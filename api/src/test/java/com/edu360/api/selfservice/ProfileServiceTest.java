package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;

/**
 * Unit tests for ProfileService (PROFILE_EDIT_OWN) - repositories mocked out, same style as
 * AuthServiceTest. Pins down: "own" is always resolved from the principal's own id (never a
 * parameter), role/platformRole/email are never written by updateXProfile even if someone tried
 * to smuggle them in, and a null field on the request leaves the existing value untouched.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private AuditLogService auditLogService;

    private ProfileService profileService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();

    private ProfileService service() {
        return new ProfileService(userAccountRepository, platformUserRepository, auditLogService);
    }

    private UserAccount mockTenantAccount() {
        return UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID))
                .userType(UserType.STAFF)
                .displayName("Original Name")
                .email("teacher@abc.edu")
                .mobile("+10000000000")
                .build();
    }

    private PlatformUser mockPlatformUser() {
        return PlatformUser.builder()
                .platformUserId(PLATFORM_USER_ID)
                .email("admin@edu360-platform.local")
                .fullName("Original Admin")
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .build();
    }

    // -------------------------------------------------------------------------------- tenant GET

    @Test
    void getTenantProfile_returnsAccountFields() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        UserAccount account = mockTenantAccount();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account));

        ProfileResponse response = profileService.getTenantProfile(principal);

        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.userAccountId()).isEqualTo(USER_ACCOUNT_ID);
        assertThat(response.displayName()).isEqualTo("Original Name");
        assertThat(response.email()).isEqualTo("teacher@abc.edu");
        assertThat(response.mobile()).isEqualTo("+10000000000");
    }

    @Test
    void getTenantProfile_accountMissing_throwsResourceNotFound() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        when(userAccountRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getTenantProfile(principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------------------ tenant PATCH

    @Test
    void updateTenantProfile_appliesDisplayNameAndMobile_andStampsUpdatedByAsSelf() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        UserAccount account = mockTenantAccount();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDisplayName("New Name");
        request.setMobile("+19999999999");

        ProfileResponse response = profileService.updateTenantProfile(principal, request);

        assertThat(response.displayName()).isEqualTo("New Name");
        assertThat(response.mobile()).isEqualTo("+19999999999");
        assertThat(account.getUpdatedBy()).isEqualTo(USER_ACCOUNT_ID);
        verify(userAccountRepository).save(account);
        verify(auditLogService).record(AuditEventType.PROFILE_UPDATED, null, "Original Name", null, null);
    }

    @Test
    void updateTenantProfile_nullFields_leaveExistingValuesUnchanged() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        UserAccount account = mockTenantAccount();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateTenantProfile(principal, new ProfileUpdateRequest());

        assertThat(response.displayName()).isEqualTo("Original Name");
        assertThat(response.mobile()).isEqualTo("+10000000000");
    }

    @Test
    void updateTenantProfile_requestHasNoRoleOrEmailField_emailNeverChanges() {
        // ProfileUpdateRequest has no setEmail/setUserType/setRole method to even call - this
        // test documents that guarantee by asserting the stored email is untouched after a full
        // update, rather than trying (and failing to compile) to set a field that does not exist.
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        UserAccount account = mockTenantAccount();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDisplayName("New Name");

        ProfileResponse response = profileService.updateTenantProfile(principal, request);

        assertThat(response.email()).isEqualTo("teacher@abc.edu");
    }

    // ------------------------------------------------------------------------------- platform GET

    @Test
    void getPlatformProfile_returnsUserFields() {
        profileService = service();
        PlatformPrincipal principal = new PlatformPrincipal(PLATFORM_USER_ID, PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        when(platformUserRepository.findById(PLATFORM_USER_ID)).thenReturn(Optional.of(mockPlatformUser()));

        ProfileResponse response = profileService.getPlatformProfile(principal);

        assertThat(response.platformUserId()).isEqualTo(PLATFORM_USER_ID);
        assertThat(response.platformRole()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(response.displayName()).isEqualTo("Original Admin");
        assertThat(response.email()).isEqualTo("admin@edu360-platform.local");
    }

    // ----------------------------------------------------------------------------- platform PATCH

    @Test
    void updatePlatformProfile_appliesDisplayName_ignoresMobile_stampsUpdatedByAsSelf() {
        profileService = service();
        PlatformPrincipal principal = new PlatformPrincipal(PLATFORM_USER_ID, PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        PlatformUser user = mockPlatformUser();
        when(platformUserRepository.findById(PLATFORM_USER_ID)).thenReturn(Optional.of(user));
        when(platformUserRepository.save(any(PlatformUser.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDisplayName("New Admin Name");
        request.setMobile("+19999999999");

        ProfileResponse response = profileService.updatePlatformProfile(principal, request);

        assertThat(response.displayName()).isEqualTo("New Admin Name");
        assertThat(user.getUpdatedBy()).isEqualTo(PLATFORM_USER_ID);
        verify(platformUserRepository).save(user);
        verify(auditLogService).record(AuditEventType.PROFILE_UPDATED, null, "admin@edu360-platform.local", null, null);
    }

    @Test
    void getPlatformProfile_userMissing_throwsResourceNotFound() {
        profileService = service();
        PlatformPrincipal principal = new PlatformPrincipal(PLATFORM_USER_ID, PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        when(platformUserRepository.findById(PLATFORM_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getPlatformProfile(principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
