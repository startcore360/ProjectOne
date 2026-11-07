package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.dto.PlatformUserCreateRequest;
import com.edu360.api.platformops.dto.PlatformUserResponse;

/**
 * Action & Guard tests for PLATFORM_USER_MANAGE (create half).
 */
class PlatformUserControllerTest {

    private PlatformUserRepository platformUserRepository;
    private KeycloakAdminClient keycloakAdminClient;
    private KeycloakProperties keycloakProperties;
    private PlatformPermissionService platformPermissionService;
    private PlatformUserManagementService platformUserManagementService;
    private PlatformUserController controller;

    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        platformUserRepository = mock(PlatformUserRepository.class);
        keycloakAdminClient = mock(KeycloakAdminClient.class);
        keycloakProperties = mock(KeycloakProperties.class);
        when(keycloakProperties.getPlatformRealm()).thenReturn("edu360-platform");
        platformPermissionService = new PlatformPermissionService(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());

        platformUserManagementService = new PlatformUserManagementService(
                platformUserRepository, keycloakAdminClient, keycloakProperties,
                org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformCustomRoleRepository.class),
                org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class));

        controller = new PlatformUserController(platformPermissionService, platformUserManagementService);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(PLATFORM_USER_ID, role, "owner@edu360-platform.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private PlatformUserCreateRequest sampleRequest() {
        return PlatformUserCreateRequest.builder()
                .fullName("Priya Nair")
                .email("priya.nair@edu360.com")
                .platformRole(PlatformRole.BILLING_MANAGER)
                .build();
    }

    @Test
    void createPlatformUser_allow_forProductOwner() {
        when(platformUserRepository.findByEmail("priya.nair@edu360.com")).thenReturn(Optional.empty());
        when(keycloakAdminClient.provisionUser(
                eq("edu360-platform"), eq("Priya Nair"), eq("priya.nair@edu360.com"), anyString(), eq(true)))
                .thenReturn("kc-subject-1");
        when(platformUserRepository.save(any(PlatformUser.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<PlatformUserResponse> response = controller.createPlatformUser(
                sampleRequest(), authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PlatformUserResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.fullName()).isEqualTo("Priya Nair");
        assertThat(body.email()).isEqualTo("priya.nair@edu360.com");
        assertThat(body.platformRole()).isEqualTo(PlatformRole.BILLING_MANAGER);
        assertThat(body.status()).isEqualTo(PlatformUserStatus.ACTIVE);
        assertThat(body.tempPassword()).isNotBlank();
    }

    @Test
    void createPlatformUser_deny_forNonProductOwnerPlatformRoles() {
        PlatformUserCreateRequest request = sampleRequest();

        assertThatThrownBy(() -> controller.createPlatformUser(request, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not hold action PLATFORM_USER_MANAGE");
    }

    @Test
    void createPlatformUser_rejectsNonPlatformPrincipal() {
        PlatformUserCreateRequest request = sampleRequest();
        Authentication nonPlatformAuth = new UsernamePasswordAuthenticationToken("tenant-user-string", null, List.of());

        assertThatThrownBy(() -> controller.createPlatformUser(request, nonPlatformAuth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Platform credentials required");
    }

    @Test
    void createPlatformUser_guard_rejectsDuplicateEmail() {
        when(platformUserRepository.findByEmail("priya.nair@edu360.com"))
                .thenReturn(Optional.of(PlatformUser.builder().email("priya.nair@edu360.com").build()));

        PlatformUserCreateRequest request = sampleRequest();

        assertThatThrownBy(() -> controller.createPlatformUser(request, authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void listPlatformUsers_returnsAllUsers() {
        PlatformUser user = PlatformUser.builder()
                .platformUserId(UUID.randomUUID())
                .email("aarav.mehta@edu360.com")
                .fullName("Aarav Mehta")
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .status(PlatformUserStatus.ACTIVE)
                .mfaRequired(true)
                .identitySubject("kc-subject-2")
                .build();
        when(platformUserRepository.findAll()).thenReturn(List.of(user));

        ResponseEntity<List<PlatformUserResponse>> response =
                controller.listPlatformUsers(authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).fullName()).isEqualTo("Aarav Mehta");
        assertThat(response.getBody().get(0).tempPassword()).isNull();
    }
}
