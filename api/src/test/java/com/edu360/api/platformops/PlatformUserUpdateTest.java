package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
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
    private PlatformAuditWriter auditWriter;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        users = mock(PlatformUserRepository.class);
        keycloak = mock(KeycloakAdminClient.class);
        roles = mock(PlatformCustomRoleRepository.class);
        KeycloakProperties properties = mock(KeycloakProperties.class);
        when(properties.getPlatformRealm()).thenReturn("edu360-platform");
        auditWriter = mock(PlatformAuditWriter.class);
        service = new PlatformUserManagementService(users, keycloak, properties, roles, auditWriter);
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
        verify(keycloak).setUserEnabledStrict("edu360-platform", "kc-staff", false);
        verify(keycloak).logoutUser("edu360-platform", "kc-staff");
    }

    @Test
    void ifKeycloakFailsTheAccountStaysAsItWas() {
        doThrow(new KeycloakProvisioningException("down")).when(keycloak).setUserEnabledStrict(anyString(), anyString(), anyBoolean());

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
    void reactivatingEnablesKeycloakAndAuditsTheStatusChange() {
        when(users.findById(staffId)).thenReturn(Optional.of(PlatformUser.builder()
                .platformUserId(staffId).fullName("Staff").email("staff@edu360.test").identitySubject("kc-staff")
                .platformRole(PlatformRole.BILLING_MANAGER).status(PlatformUserStatus.INACTIVE).build()));

        PlatformUserResponse res = service.updatePlatformUser(staffId,
                new PlatformUserUpdateRequest(null, null, null, PlatformUserStatus.ACTIVE), OWNER);

        assertThat(res.status()).isEqualTo(PlatformUserStatus.ACTIVE);
        verify(keycloak).setUserEnabledStrict("edu360-platform", "kc-staff", true);
        verify(auditWriter).record(eq(OWNER), isNull(), eq("PLATFORM_USER_UPDATED"), eq("UPDATE"),
                eq("PLATFORM_USER_MANAGE"), eq("platform_user"), eq(staffId), eq(PlatformAuditWriter.SUCCESS),
                argThat(changes -> changes.get("status").equals(Map.of("old", "INACTIVE", "new", "ACTIVE"))));
    }

    @Test
    void unlockingClearsOnlyTheKeycloakBruteForceLockoutAndAuditsIt() {
        service.unlockPlatformUser(staffId, OWNER);

        verify(keycloak).clearBruteForceLockout("edu360-platform", "kc-staff");
        verify(keycloak, never()).resetPassword(anyString(), anyString(), anyString(), anyBoolean());
        verify(keycloak, never()).logoutUser(anyString(), anyString());
        verify(users, never()).save(any());
        verify(auditWriter).record(OWNER, null, "PLATFORM_USER_UNLOCKED", "UPDATE",
                "PLATFORM_USER_MANAGE", "PLATFORM_USER", staffId, PlatformAuditWriter.SUCCESS);
    }

    @Test
    void cannotUnlockOwnPlatformAccount() {
        assertThatThrownBy(() -> service.unlockPlatformUser(OWNER.platformUserId(), OWNER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("own platform account");

        verify(keycloak, never()).clearBruteForceLockout(anyString(), anyString());
    }

    @Test
    void cannotUnlockAnInactivePlatformAccount() {
        when(users.findById(staffId)).thenReturn(Optional.of(PlatformUser.builder()
                .platformUserId(staffId).identitySubject("kc-staff")
                .platformRole(PlatformRole.BILLING_MANAGER).status(PlatformUserStatus.INACTIVE).build()));

        assertThatThrownBy(() -> service.unlockPlatformUser(staffId, OWNER))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Reactivate this platform account");

        verify(keycloak, never()).clearBruteForceLockout(anyString(), anyString());
    }

    @Test
    void parallelDemotionsCannotRemoveTheLastActiveProductOwner() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PlatformUser first = PlatformUser.builder().platformUserId(firstId).fullName("Owner One")
                .email("one@edu360.test").identitySubject("kc-one").platformRole(PlatformRole.PRODUCT_OWNER)
                .status(PlatformUserStatus.ACTIVE).build();
        PlatformUser second = PlatformUser.builder().platformUserId(secondId).fullName("Owner Two")
                .email("two@edu360.test").identitySubject("kc-two").platformRole(PlatformRole.PRODUCT_OWNER)
                .status(PlatformUserStatus.ACTIVE).build();
        when(users.findById(firstId)).thenReturn(Optional.of(first));
        when(users.findById(secondId)).thenReturn(Optional.of(second));

        // Model the repository's database row-lock contract: the first update holds the lock
        // through its save; the concurrent update then observes the committed owner count.
        ReentrantLock ownerRowsLock = new ReentrantLock(true);
        AtomicInteger activeOwners = new AtomicInteger(2);
        when(users.lockActiveProductOwners(PlatformRole.PRODUCT_OWNER, PlatformUserStatus.ACTIVE))
                .thenAnswer(invocation -> {
                    ownerRowsLock.lock();
                    return List.of(first, second);
                });
        when(users.countByPlatformRoleAndStatus(PlatformRole.PRODUCT_OWNER, PlatformUserStatus.ACTIVE))
                .thenAnswer(invocation -> {
                    int count = activeOwners.get();
                    if (count <= 1) ownerRowsLock.unlock();
                    return (long) count;
                });
        when(users.save(any())).thenAnswer(invocation -> {
            PlatformUser saved = invocation.getArgument(0);
            activeOwners.decrementAndGet();
            ownerRowsLock.unlock();
            return saved;
        });

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> {
                start.await();
                return demoteOwner(firstId);
            });
            var two = pool.submit(() -> {
                start.await();
                return demoteOwner(secondId);
            });
            start.countDown();
            int successes = (one.get(5, TimeUnit.SECONDS) ? 1 : 0) + (two.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(activeOwners.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean demoteOwner(UUID userId) {
        try {
            service.updatePlatformUser(userId,
                    new PlatformUserUpdateRequest(null, PlatformRole.PLATFORM_ADMIN, null, null), OWNER);
            return true;
        } catch (BusinessRuleViolationException ex) {
            assertThat(ex).hasMessageContaining("last active Product Owner");
            return false;
        }
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
