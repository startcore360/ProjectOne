package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
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
    @Mock private FileObjectRepository fileObjectRepository;
    @Mock private LocalFileStorage fileStorage;
    @Mock private AuditLogService auditLogService;

    private ProfileService profileService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();

    private ProfileService service() {
        return new ProfileService(userAccountRepository, platformUserRepository, fileObjectRepository,
                fileStorage, auditLogService);
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

    // --------------------------------------------------------------------------------- photo

    @Test
    void uploadTenantPhoto_validJpeg_storesEndsAnyPreviousAndReturnsPhotoUrl() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(mockTenantAccount()));
        when(fileStorage.store(eq(TENANT_ID), any(), any())).thenReturn("tenant/stored-key.jpg");

        FileObject previousActive = FileObject.builder()
                .id(new FileObjectId(TENANT_ID, UUID.randomUUID()))
                .purpose("PROFILE_PHOTO").status("ACTIVE").scanStatus("CLEAN")
                .storageKey("old-key").originalFilename("old.jpg").contentType("image/jpeg")
                .sizeBytes(10).sha256("x").uploadedBy(USER_ACCOUNT_ID).build();
        when(fileObjectRepository.findByIdTenantIdAndUploadedByAndPurpose(TENANT_ID, USER_ACCOUNT_ID, "PROFILE_PHOTO"))
                .thenReturn(List.of(previousActive));
        when(fileObjectRepository.save(any(FileObject.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileObjectRepository.findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                TENANT_ID, USER_ACCOUNT_ID, "PROFILE_PHOTO", "ACTIVE", "CLEAN"))
                .thenReturn(Optional.of(FileObject.builder().status("ACTIVE").scanStatus("CLEAN").build()));

        MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});

        ProfileResponse response = profileService.uploadTenantPhoto(principal, file);

        assertThat(response.photoUrl()).isEqualTo("/me/profile/photo");
        assertThat(previousActive.getStatus()).isEqualTo("DELETED");
        verify(auditLogService).record(AuditEventType.PROFILE_UPDATED, null, "Original Name", null, null);
    }

    @Test
    void uploadTenantPhoto_disallowedContentType_rejectedBeforeAnyStorageCall() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        MockMultipartFile file = new MockMultipartFile("file", "me.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> profileService.uploadTenantPhoto(principal, file))
                .isInstanceOf(IllegalArgumentException.class);
        verify(fileObjectRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void uploadTenantPhoto_tooLarge_rejected() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", tooBig);

        assertThatThrownBy(() -> profileService.uploadTenantPhoto(principal, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTenantPhoto_noActivePhoto_throwsResourceNotFound() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        when(fileObjectRepository.findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                TENANT_ID, USER_ACCOUNT_ID, "PROFILE_PHOTO", "ACTIVE", "CLEAN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getTenantPhoto(principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTenantPhoto_activePhoto_streamsStoredBytesAndContentType() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Original Name", false);
        FileObject stored = FileObject.builder()
                .id(new FileObjectId(TENANT_ID, UUID.randomUUID()))
                .status("ACTIVE").scanStatus("CLEAN").storageKey("tenant/stored-key.jpg")
                .contentType("image/jpeg").build();
        when(fileObjectRepository.findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                TENANT_ID, USER_ACCOUNT_ID, "PROFILE_PHOTO", "ACTIVE", "CLEAN"))
                .thenReturn(Optional.of(stored));
        when(fileStorage.read("tenant/stored-key.jpg")).thenReturn(new byte[]{9, 9, 9});

        ProfileService.StoredPhoto photo = profileService.getTenantPhoto(principal);

        assertThat(photo.content()).containsExactly(9, 9, 9);
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
    }

    // ------------------------------------------------------------------------ I-08 parent phone change

    @Test
    @org.junit.jupiter.api.DisplayName("REG-I08-3: a parent's mobile change is refused and the audit says exactly that (no code checked, no alert sent)")
    void updateTenantProfile_guardianMobileChange_refusedAndAuditIsHonest() {
        profileService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.GUARDIAN, "Parent", false);
        UserAccount parent = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)).userType(UserType.GUARDIAN)
                .displayName("Parent").mobile("+10000000000").build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID))).thenReturn(Optional.of(parent));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setMobile("+19999999999");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> profileService.updateTenantProfile(principal, request))
                .isInstanceOfSatisfying(com.edu360.api.exception.BusinessRuleViolationException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("PHONE_VERIFICATION_UNAVAILABLE"));

        assertThat(parent.getMobile()).isEqualTo("+10000000000");
        verify(userAccountRepository, org.mockito.Mockito.never()).save(any(UserAccount.class));
        verify(auditLogService).record(AuditEventType.DENIED_ATTEMPT, null, "Parent", null,
                ProfileService.PARENT_MOBILE_CHANGE_REFUSED_AUDIT);
        assertThat(ProfileService.PARENT_MOBILE_CHANGE_REFUSED_AUDIT).contains("no SMS provider").contains("no alert was sent");
    }
}
