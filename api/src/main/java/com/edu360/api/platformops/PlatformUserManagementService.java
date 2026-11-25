package com.edu360.api.platformops;

import com.edu360.api.platformops.role.PlatformCustomRoleStatus;
import com.edu360.api.platformops.role.PlatformCustomRoleRepository;
import com.edu360.api.platformops.role.PlatformCustomRole;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.dto.PlatformUserUpdateRequest;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.dto.PlatformUserCreateRequest;
import com.edu360.api.platformops.dto.PlatformUserResponse;

import lombok.RequiredArgsConstructor;

/**
 * Implements PLATFORM_USER_MANAGE (create half): provisions a Keycloak account in the platform
 * realm (edu360-platform) and the matching platform.platform_user row.
 *
 * WHY this can finally use PlatformUserRepository.save(...) instead of DevAdminSeeder's raw JDBC
 * insert: PlatformJpaConfig now gives platform.* repositories a real Spring-managed
 * EntityManagerFactory backed by edu_platform (see that class's javadoc) - this service is the
 * first production write path to use it.
 */
@Service
@RequiredArgsConstructor
public class PlatformUserManagementService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserManagementService.class);

    private final PlatformUserRepository platformUserRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;
    private final PlatformCustomRoleRepository customRoleRepository;
    private final PlatformAuditWriter auditWriter;

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformUserResponse createPlatformUser(PlatformUserCreateRequest request, PlatformPrincipal principal) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (platformUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateResourceException(
                    "A platform user with email '" + normalizedEmail + "' already exists.");
        }

        AssignedRole assigned = resolveRole(request.getPlatformRole(), request.getPlatformRoleId());

        String realm = keycloakProperties.getPlatformRealm();
        String tempPassword = generateTemporaryPassword();

        // A failure here must stop the request. An account with a made-up identity could never sign in.
        String keycloakSubject = keycloakAdminClient.provisionUser(
                realm, request.getFullName().trim(), normalizedEmail, tempPassword, true);

        UUID platformUserId = UUID.randomUUID();
        Instant now = Instant.now();
        PlatformUser user = PlatformUser.builder()
                .platformUserId(platformUserId)
                .email(normalizedEmail)
                .fullName(request.getFullName().trim())
                .platformRole(assigned.base())
                .platformRoleId(assigned.customRoleId())
                .status(PlatformUserStatus.ACTIVE)
                .mfaRequired(true)
                .identitySubject(keycloakSubject)
                .createdBy(principal.platformUserId())
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        PlatformUser saved = platformUserRepository.save(user);
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        changes.put("fullName", change(null, saved.getFullName()));
        changes.put("email", change(null, saved.getEmail()));
        changes.put("platformRole", change(null, roleSnapshot(assigned.base(), assigned.customRoleId())));
        changes.put("status", change(null, saved.getStatus().name()));
        auditWriter.record(principal, null, "PLATFORM_USER_CREATED", "CREATE", "PLATFORM_USER_MANAGE",
                "platform_user", platformUserId, PlatformAuditWriter.SUCCESS, changes);

        log.info("Created platform user {} ({}) with role {} by {}",
                platformUserId, normalizedEmail, assigned.base(), principal.email());

        return PlatformUserResponse.from(saved, assigned.customRoleName(), tempPassword);
    }

    /**
     * D5 updatePlatformUser: rename, change role, deactivate or reactivate. Nobody changes their own
     * role or status (Addendum rule 11), which also means the last Product Owner can't be removed.
     */
    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformUserResponse updatePlatformUser(UUID platformUserId, PlatformUserUpdateRequest request,
            PlatformPrincipal principal) {
        // Lock all active Product Owner rows in stable order before reading the target. This is a
        // database-backed, cross-instance serialization point for the last-owner invariant.
        platformUserRepository.lockActiveProductOwners(PlatformRole.PRODUCT_OWNER, PlatformUserStatus.ACTIVE);
        PlatformUser user = platformUserRepository.findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform user " + platformUserId + " was not found."));

        boolean changesRole = request.platformRole() != null || request.platformRoleId() != null;
        boolean changesStatus = request.status() != null && request.status() != user.getStatus();
        if ((changesRole || changesStatus) && platformUserId.equals(principal.platformUserId())) {
            throw new UnauthorizedException("You can't change your own role or status (Addendum rule 11).");
        }

        // L-09: the platform must never be left without an active Product Owner.
        boolean losesOwnerRole = user.getPlatformRole() == PlatformRole.PRODUCT_OWNER
                && user.getStatus() == PlatformUserStatus.ACTIVE
                && (changesStatus && request.status() != PlatformUserStatus.ACTIVE
                    || changesRole && (request.platformRole() != PlatformRole.PRODUCT_OWNER));
        if (losesOwnerRole
                && platformUserRepository.countByPlatformRoleAndStatus(PlatformRole.PRODUCT_OWNER, PlatformUserStatus.ACTIVE) <= 1) {
            throw new BusinessRuleViolationException("LAST_PRODUCT_OWNER",
                    "The last active Product Owner cannot be demoted or deactivated.");
        }

        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        if (request.fullName() != null) {
            String name = request.fullName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Full name can't be blank.");
            }
            if (!Objects.equals(user.getFullName(), name)) {
                changes.put("fullName", change(user.getFullName(), name));
                user.setFullName(name);
            }
        }
        if (changesRole) {
            AssignedRole assigned = resolveRole(request.platformRole(), request.platformRoleId());
            String oldRole = roleSnapshot(user.getPlatformRole(), user.getPlatformRoleId());
            String newRole = roleSnapshot(assigned.base(), assigned.customRoleId());
            if (!Objects.equals(oldRole, newRole)) {
                changes.put("platformRole", change(oldRole, newRole));
            }
            user.setPlatformRole(assigned.base());
            user.setPlatformRoleId(assigned.customRoleId());
        }
        user.setUpdatedBy(principal.platformUserId());

        String realm = keycloakProperties.getPlatformRealm();
        if (changesStatus) {
            boolean enable = request.status() == PlatformUserStatus.ACTIVE;
            // Do not persist ACTIVE/INACTIVE unless Keycloak confirms the enabled-state update.
            keycloakAdminClient.setUserEnabledStrict(realm, user.getIdentitySubject(), enable);
            if (!enable) {
                keycloakAdminClient.logoutUser(realm, user.getIdentitySubject());
            }
            changes.put("status", change(user.getStatus().name(), request.status().name()));
            user.setStatus(request.status());
        }

        PlatformUser saved = platformUserRepository.save(user);
        auditWriter.record(principal, null, "PLATFORM_USER_UPDATED", "UPDATE", "PLATFORM_USER_MANAGE",
                "platform_user", platformUserId, PlatformAuditWriter.SUCCESS, changes);
        return PlatformUserResponse.from(saved, customRoleName(saved.getPlatformRoleId()), null);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void unlockPlatformUser(UUID platformUserId, PlatformPrincipal principal) {
        if (platformUserId.equals(principal.platformUserId())) {
            throw new UnauthorizedException("You can't unlock your own platform account.");
        }

        PlatformUser user = platformUserRepository.findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user " + platformUserId + " was not found."));
        if (user.getStatus() != PlatformUserStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "PLATFORM_USER_INACTIVE", "Reactivate this platform account before unlocking sign-in.");
        }

        keycloakAdminClient.clearBruteForceLockout(
                keycloakProperties.getPlatformRealm(), user.getIdentitySubject());
        auditWriter.record(principal, null, "PLATFORM_USER_UNLOCKED", "UPDATE", "PLATFORM_USER_MANAGE",
                "PLATFORM_USER", platformUserId, PlatformAuditWriter.SUCCESS);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformUserResponse resetPlatformUserPassword(UUID platformUserId, PlatformPrincipal principal) {
        if (platformUserId.equals(principal.platformUserId())) {
            throw new UnauthorizedException("You can't reset your own platform password.");
        }

        PlatformUser user = platformUserRepository.findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user " + platformUserId + " was not found."));
        if (user.getStatus() != PlatformUserStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "PLATFORM_USER_INACTIVE",
                    "Reactivate this platform account before resetting its password.");
        }
        if (user.getIdentitySubject() == null || user.getIdentitySubject().isBlank()) {
            throw new BusinessRuleViolationException(
                    "PLATFORM_USER_NO_IDENTITY",
                    "This platform account has no active Keycloak identity.");
        }

        String realm = keycloakProperties.getPlatformRealm();
        String temporaryPassword = generateTemporaryPassword();
        keycloakAdminClient.resetPassword(realm, user.getIdentitySubject(), temporaryPassword, true);
        keycloakAdminClient.logoutUser(realm, user.getIdentitySubject());

        auditWriter.record(principal, null, "PLATFORM_USER_PASSWORD_RESET", "UPDATE",
                "PLATFORM_USER_MANAGE", "PLATFORM_USER", platformUserId, PlatformAuditWriter.SUCCESS);

        return PlatformUserResponse.from(
                user,
                customRoleName(user.getPlatformRoleId()),
                temporaryPassword);
    }

    private static Map<String, Object> change(Object oldValue, Object newValue) {
        return Map.of("old", oldValue == null ? "" : oldValue, "new", newValue == null ? "" : newValue);
    }

    private static String roleSnapshot(PlatformRole role, UUID customRoleId) {
        return role.name() + (customRoleId == null ? "" : ":" + customRoleId);
    }

    private AssignedRole resolveRole(PlatformRole standardRole, UUID customRoleId) {
        if ((standardRole == null) == (customRoleId == null)) {
            throw new IllegalArgumentException("Choose either a standard role or a custom role.");
        }
        if (standardRole != null) {
            return new AssignedRole(standardRole, null, null);
        }
        PlatformCustomRole role = customRoleRepository.findById(customRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform role " + customRoleId + " was not found."));
        if (role.getStatus() != PlatformCustomRoleStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Activate the role '" + role.getRoleName() + "' before assigning it.");
        }
        return new AssignedRole(role.getBasePlatformRole(), role.getPlatformRoleId(), role.getRoleName());
    }

    private String customRoleName(UUID customRoleId) {
        return customRoleId == null ? null
                : customRoleRepository.findById(customRoleId).map(PlatformCustomRole::getRoleName).orElse(null);
    }

    private record AssignedRole(PlatformRole base, UUID customRoleId, String customRoleName) {
    }

    @Transactional(readOnly = true)
    public List<PlatformUserResponse> listPlatformUsers() {
        Map<UUID, String> roleNames = customRoleRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformCustomRole::getPlatformRoleId, PlatformCustomRole::getRoleName));
        return platformUserRepository.findAll().stream()
                .map(user -> PlatformUserResponse.from(user, roleNames.get(user.getPlatformRoleId()), null))
                .toList();
    }

    private String generateTemporaryPassword() {
        return com.edu360.api.auth.TemporaryPasswordGenerator.generate();
    }
}
