package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;

/**
 * Plain unit tests for ProfileController - same no-Spring-context style as AuthControllerTest:
 * pins down switching on principal type and refusing (401) with no identity, not the real
 * security pipeline (already covered elsewhere).
 */
@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock private ProfileService profileService;
    @Mock private Jwt jwt;

    private ProfileController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();

    private ProfileController controller() {
        return new ProfileController(profileService);
    }

    // ------------------------------------------------------------------------------------- GET

    @Test
    void getProfile_noAuthentication_returns401() {
        controller = controller();
        ResponseEntity<ProfileResponse> response = controller.getProfile(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getProfile_tenantPrincipal_callsGetTenantProfile() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        ProfileResponse expected = ProfileResponse.builder().tenantId(TENANT_ID).build();
        when(profileService.getTenantProfile(principal)).thenReturn(expected);

        ResponseEntity<ProfileResponse> response = controller.getProfile(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(profileService, never()).getPlatformProfile(any());
    }

    @Test
    void getProfile_platformPrincipal_callsGetPlatformProfile() {
        controller = controller();
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.SUPPORT_MANAGER,
                "support@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        ProfileResponse expected = ProfileResponse.builder().platformUserId(principal.platformUserId()).build();
        when(profileService.getPlatformProfile(principal)).thenReturn(expected);

        ResponseEntity<ProfileResponse> response = controller.getProfile(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(profileService, never()).getTenantProfile(any());
    }

    // ------------------------------------------------------------------------------------- PATCH

    @Test
    void updateProfile_noAuthentication_returns401WithoutCallingService() {
        controller = controller();
        ResponseEntity<ProfileResponse> response = controller.updateProfile(new ProfileUpdateRequest(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(profileService, never()).updateTenantProfile(any(), any());
        verify(profileService, never()).updatePlatformProfile(any(), any());
    }

    @Test
    void updateProfile_tenantPrincipal_callsUpdateTenantProfile() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDisplayName("New Name");
        ProfileResponse expected = ProfileResponse.builder().displayName("New Name").build();
        when(profileService.updateTenantProfile(eq(principal), eq(request))).thenReturn(expected);

        ResponseEntity<ProfileResponse> response = controller.updateProfile(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(profileService, never()).updatePlatformProfile(any(), any());
    }

    @Test
    void updateProfile_platformPrincipal_callsUpdatePlatformProfile() {
        controller = controller();
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDisplayName("New Admin Name");
        ProfileResponse expected = ProfileResponse.builder().displayName("New Admin Name").build();
        when(profileService.updatePlatformProfile(eq(principal), eq(request))).thenReturn(expected);

        ResponseEntity<ProfileResponse> response = controller.updateProfile(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(profileService, never()).updateTenantProfile(any(), any());
    }

    // ------------------------------------------------------------------------------------ photo

    @Test
    void uploadPhoto_tenantPrincipal_callsUploadTenantPhoto() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        MultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1});
        ProfileResponse expected = ProfileResponse.builder().photoUrl("/me/profile/photo").build();
        when(profileService.uploadTenantPhoto(principal, file)).thenReturn(expected);

        ResponseEntity<ProfileResponse> response = controller.uploadPhoto(file, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void uploadPhoto_platformPrincipal_rejectedWithoutCallingService() {
        controller = controller();
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        MultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> controller.uploadPhoto(file, authentication))
                .isInstanceOf(UnauthorizedException.class);
        verify(profileService, never()).uploadTenantPhoto(any(), any());
    }

    @Test
    void getPhoto_noAuthentication_returns401() {
        controller = controller();
        ResponseEntity<byte[]> response = controller.getPhoto(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getPhoto_tenantPrincipal_streamsStoredPhoto() {
        controller = controller();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
        when(profileService.getTenantPhoto(principal))
                .thenReturn(new ProfileService.StoredPhoto(new byte[]{1, 2}, "image/jpeg"));

        ResponseEntity<byte[]> response = controller.getPhoto(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1, 2);
    }

    @Test
    void getPhoto_platformPrincipal_rejectedWithoutCallingService() {
        controller = controller();
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, jwt, List.of());

        assertThatThrownBy(() -> controller.getPhoto(authentication))
                .isInstanceOf(UnauthorizedException.class);
        verify(profileService, never()).getTenantPhoto(any());
    }
}
