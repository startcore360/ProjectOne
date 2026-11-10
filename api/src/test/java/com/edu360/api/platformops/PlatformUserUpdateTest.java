package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.config.keycloak.KeycloakProvisioningException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.dto.PlatformUserCreateRequest;
import com.edu360.api.platformops.dto.PlatformUserResponse;
import com.edu360.api.platformops.dto.PlatformUserUpdateRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.role.PlatformCustomRole;
import com.edu360.api.platformops.role.PlatformCustomRoleRepository;
import com.edu360.api.platformops.role.PlatformCustomRoleStatus;

class PlatformUserUpdateTest {

    private static final PlatformPrincipal OWNER =
            new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);

    private PlatformUserRepository users;
    private KeycloakAdminClient keycloak;
    private PlatformCustomRoleRepository roles;
    private PlatformUserManagementService service;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        users = mock(PlatformUserRepository.class);
        keycloak = mock(KeycloakAdminClient.class);
        roles = mock(PlatformCustomRoleRepository.class);
        KeycloakProperties properties = mock(KeycloakProperties.class);
        when(properties.getPlatformRealm()).thenReturn("edu360-platform");
        service = new PlatformUserManagementService(users, keycloak, properties, roles, mock(PlatformAuditWriter.class));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        staffId = UUID.randomUUID();
        when(users.findById(staffId)).thenReturn(Optional.of(PlatformUser.builder()
                .platformUserId(staffId).fullName("Staff").email("staff@edu360.test").identitySubject("kc-staff")
                .platformRole(PlatformRole.BILLING_MANAGER).status(PlatformUserStatus.ACTIVE).build()));
    }

    private UUID customRole(PlatformCustomRoleStatus status) {
        UUID id = UUID.randomUUID();
        when(roles.findById(id)).thenReturn(Optional.of(PlatformCustomRole.builder()
                .platformRoleId(id).roleName("Billing helper").basePlatformRole(PlatformRole.BILLING_MANAGER).status(status).build()));
        return id;
    }

    @Test
    void deactivatingDisablesTheAccountAndEndsItsSessions() {
        PlatformUserResponse res = service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, null, null, PlatformUserStatus.INACTIVE), OWNER);

        assertThat(res.status()).isEqualTo(PlatformUserStatus.INACTIVE);
        verify(keycloak).setUserEnabled("edu360-platform", "kc-staff", false);
        verify(keycloak).logoutUser("edu360-platform", "kc-staff");
    }

    @Test
    void ifKeycloakFailsTheAccountStaysAsItWas() {
        doThrow(new KeycloakProvisioningException("down")).when(keycloak).setUserEnabled(anyString(), anyString(), anyBoolean());

        assertThatThrownBy(() -> service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, null, null, PlatformUserStatus.INACTIVE), OWNER))
                .isInstanceOf(KeycloakProvisioningException.class);
        verify(users, never()).save(any());
    }

    @Test
    void nobodyCanChangeTheirOwnRoleOrStatus() {
        when(users.findById(OWNER.platformUserId())).thenReturn(Optional.of(PlatformUser.builder()
                .platformUserId(OWNER.platformUserId()).platformRole(PlatformRole.PRODUCT_OWNER)
                .status(PlatformUserStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.updatePlatformUser(OWNER.platformUserId(),
                new PlatformUserUpdateRequest(null, PlatformRole.PLATFORM_ADMIN, null, null), OWNER))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.updatePlatformUser(OWNER.platformUserId(),
                new PlatformUserUpdateRequest(null, null, null, PlatformUserStatus.INACTIVE), OWNER))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void assigningACustomRoleUsesItsBaseRole() {
        UUID roleId = customRole(PlatformCustomRoleStatus.ACTIVE);

        PlatformUserResponse res = service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, null, roleId, null), OWNER);

        assertThat(res.platformRoleId()).isEqualTo(roleId);
        assertThat(res.customRoleName()).isEqualTo("Billing helper");
        assertThat(res.platformRole()).isEqualTo(PlatformRole.BILLING_MANAGER);
    }

    @Test
    void movingBackToAStandardRoleClearsTheCustomRole() {
        PlatformUserResponse res = service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, PlatformRole.RELEASE_MANAGER, null, null), OWNER);

        assertThat(res.platformRoleId()).isNull();
        assertThat(res.platformRole()).isEqualTo(PlatformRole.RELEASE_MANAGER);
    }

    @Test
    void draftRoleCanNotBeAssigned() {
        UUID roleId = customRole(PlatformCustomRoleStatus.DRAFT);

        assertThatThrownBy(() -> service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, null, roleId, null), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void sendingBothKindsOfRoleIsRefused() {
        assertThatThrownBy(() -> service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, PlatformRole.RELEASE_MANAGER, UUID.randomUUID(), null), OWNER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creatingAccountStopsIfKeycloakFails() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(keycloak.provisionUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new KeycloakProvisioningException("down"));

        assertThatThrownBy(() -> service.createPlatformUser(PlatformUserCreateRequest.builder()
                .fullName("New Staff").email("new@edu360.test").platformRole(PlatformRole.RELEASE_MANAGER).build(), OWNER))
                .isInstanceOf(KeycloakProvisioningException.class);
        verify(users, never()).save(any());
    }
}
