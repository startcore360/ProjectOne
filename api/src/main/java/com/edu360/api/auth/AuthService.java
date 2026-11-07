package com.edu360.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.identity.TenantUserLookup;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.LoginTenantResolver.PlatformLogin;
import com.edu360.api.tenant.LoginTenantResolver.Resolution;
import com.edu360.api.tenant.LoginTenantResolver.TenantLogin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: the real work behind login/refresh/change-password - now across two identity spaces
 * (tenant realms and the platform realm, see LoginTenantResolver) instead of the single
 * realm/single User table this class used before the V4.0 identity migration.
 *
 * Account CREATION is deliberately not this class's job any more: the old register() endpoint's
 * authorization model (PLATFORM_ADMIN creates TEACHER/STUDENT) does not correspond to anything
 * in the V4.0 model - real account creation (USER_CREATE) is a Users-and-roles-epic story that
 * needs the 71-role/280-action model (Foundation Item 7) to authorize correctly, which does not
 * exist yet. Local dev seeding is DevAdminSeeder's job, same dev-only pattern as before.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final LoginTenantResolver loginTenantResolver;
    private final TenantRepository tenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TenantUserLookup tenantUserLookup;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAuthClient keycloakAuthClient;
    private final KeycloakProperties keycloakProperties;
    private final AuditLogService auditLogService;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final FileObjectRepository fileObjectRepository;

    private static final String PHOTO_PURPOSE = "PROFILE_PHOTO";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String SCAN_CLEAN = "CLEAN";

    private static final Set<String> MFA_REQUIRED_ROLES = Set.of(
            "PLATFORM_ADMIN", "BILLING_MANAGER", "SUPPORT_LEAD", "RELEASE_ENGINEER", "COMPLIANCE_AUDITOR",
            "MANAGEMENT", "GOVERNING_BODY_APPROVER", "UNIVERSITY_ADMIN", "REGISTRAR", "VICE_CHANCELLOR",
            "FINANCE_DIRECTOR", "FINANCE_OFFICER", "CONTROLLER_OF_EXAMINATIONS",
            "COLLEGE_ADMIN", "PRINCIPAL_COLLEGE", "VICE_PRINCIPAL_COLLEGE",
            "SCHOOL_ADMIN", "PRINCIPAL_SCHOOL", "VICE_PRINCIPAL_SCHOOL",
            "ACCOUNTANT", "UNIVERSITY_ACCOUNTANT", "COLLEGE_ACCOUNTANT", "SCHOOL_ACCOUNTANT",
            "HR_MANAGER", "HR_OFFICER", "PAYROLL_OFFICER", "IT_ADMINISTRATOR", "SCHOOL_IT_SUPPORT"
    );

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "12345678", "123456789", "qwerty123", "password123", "admin123",
            "changeme", "changeme123", "changeme123!", "welcome123", "letmein123"
    );

    private final ConcurrentMap<String, FailedAttemptTracker> failedAttempts = new ConcurrentHashMap<>();

    public AuthTokenResponse login(HttpServletRequest httpRequest, String identifier, String password,
                                    String ipAddress) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);
        String lockKey = realmOf(resolution) + ":" + (identifier != null ? identifier.trim().toLowerCase() : "");
        checkLockout(lockKey);

        KeycloakAuthClient.TokenResult tokens;
        try {
            tokens = keycloakAuthClient.login(realmOf(resolution), identifier, password);
            clearLockout(lockKey);
        } catch (InvalidCredentialsException ex) {
            recordFailedAttempt(lockKey);
            auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress, null);
            throw ex;
        }

        AuthTokenResponse response = toResponse(resolution, tokens);
        auditLogService.record(AuditEventType.LOGIN_SUCCESS, null, identifier, ipAddress, null);
        return response;
    }

    private void checkLockout(String key) {
        FailedAttemptTracker tracker = failedAttempts.get(key);
        if (tracker != null && tracker.lockedUntil() != null) {
            if (tracker.lockedUntil().isAfter(Instant.now())) {
                throw new InvalidCredentialsException("Account is temporarily locked due to 5 consecutive failed login attempts. Please try again after 15 minutes or contact your administrator.");
            } else {
                failedAttempts.remove(key);
            }
        }
    }

    private void recordFailedAttempt(String key) {
        Instant now = Instant.now();
        failedAttempts.compute(key, (k, existing) -> {
            if (existing == null) {
                return new FailedAttemptTracker(1, now, null);
            }
            int newCount = existing.count() + 1;
            Instant lockedUntil = newCount >= 5 ? now.plus(Duration.ofMinutes(15)) : null;
            return new FailedAttemptTracker(newCount, now, lockedUntil);
        });
    }

    public void clearLockout(String identifier) {
        if (identifier != null) {
            String idLower = identifier.trim().toLowerCase();
            failedAttempts.remove(idLower);
            failedAttempts.keySet().removeIf(k -> k.endsWith(":" + idLower));
        }
    }

    public void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessRuleViolationException("Rule R2-12: Password must be at least 8 characters long.");
        }
        if (COMMON_PASSWORDS.contains(password.trim().toLowerCase())) {
            throw new BusinessRuleViolationException("Rule R2-12: Common and dictionary passwords are not permitted.");
        }
    }

    public record FailedAttemptTracker(int count, Instant lastAttempt, Instant lockedUntil) {}

    /**
     * Builds the /api/auth/me response for an already-authenticated tenant principal, including
     * their resolved role (see {@link #resolveRole}) - the one place GET /me needs role data
     * that AuthUserResponse.fromTenant alone (no role lookup) does not provide.
     */
    public AuthUserResponse currentTenantUser(TenantPrincipal principal) {
        RoleInfo role = resolveRole(principal.tenantId(), principal.userAccountId());
        String photoUrl = resolvePhotoUrl(principal.tenantId(), principal.userAccountId());
        boolean mfaRequired = role.roleCode() != null && MFA_REQUIRED_ROLES.contains(role.roleCode());
        return AuthUserResponse.fromTenant(principal, principal.displayName(), role.roleCode(), role.roleName(), photoUrl, mfaRequired);
    }

    public AuthTokenResponse refresh(HttpServletRequest httpRequest, String refreshToken) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);
        KeycloakAuthClient.TokenResult tokens = keycloakAuthClient.refresh(realmOf(resolution), refreshToken);
        return toResponse(resolution, tokens);
    }

    public AuthUserResponse changeTenantPassword(TenantPrincipal principal, String keycloakSubject,
                                                   String currentPassword, String newPassword, String ipAddress) {
        validatePasswordPolicy(newPassword);
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new InvalidCredentialsException("Tenant no longer exists"));
        UserAccount account = userAccountRepository
                .findById(new UserAccountId(principal.tenantId(), principal.userAccountId()))
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));

        String identifier = identifierFor(principal.tenantId(), principal.userAccountId(), account);
        keycloakAuthClient.login(tenant.getKeycloakRealm(), identifier, currentPassword);
        keycloakAdminClient.resetPassword(tenant.getKeycloakRealm(), keycloakSubject, newPassword);

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, identifier, ipAddress, null);
        TenantPrincipal updated = new TenantPrincipal(principal.tenantId(), principal.userAccountId(),
                principal.userType(), principal.displayName(), false);
        RoleInfo role = resolveRole(principal.tenantId(), principal.userAccountId());
        String photoUrl = resolvePhotoUrl(principal.tenantId(), principal.userAccountId());
        boolean mfaRequired = role.roleCode() != null && MFA_REQUIRED_ROLES.contains(role.roleCode());
        return AuthUserResponse.fromTenant(updated, account.getDisplayName(), role.roleCode(), role.roleName(), photoUrl, mfaRequired);
    }

    public AuthUserResponse changePlatformPassword(PlatformPrincipal principal, String keycloakSubject,
                                                     String currentPassword, String newPassword, String ipAddress) {
        validatePasswordPolicy(newPassword);
        String realm = keycloakProperties.getPlatformRealm();
        keycloakAuthClient.login(realm, principal.email(), currentPassword);
        keycloakAdminClient.resetPassword(realm, keycloakSubject, newPassword);

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, principal.email(), ipAddress, null);
        PlatformPrincipal updated = new PlatformPrincipal(
                principal.platformUserId(), principal.platformRole(), principal.email(), false);
        return AuthUserResponse.fromPlatform(updated, true);
    }

    /**
     * WHAT: picks the right value to re-verify the current password against Keycloak with -
     * email, student code, or mobile, per AUTH_LOGIN's own per-role login rule (§8.1): "Staff:
     * email... College student: Student ID... Parent: mobile". Re-verifying with the wrong
     * field (e.g. always email, what this originally did) would fail Keycloak's login check for
     * every student/parent account, since their Keycloak username is never their email.
     *
     * HOW: looks up this account's active UserIdentityLink to find which UsernameKind it
     * actually logs in with, then resolves the matching value - email/mobile straight off
     * UserAccount, student code via a StudentProfile lookup (the schema keeps that in a
     * separate table, not on user_account itself).
     */
    private String identifierFor(java.util.UUID tenantId, java.util.UUID userAccountId, UserAccount account) {
        UserIdentityLink link = userIdentityLinkRepository
                .findFirstByIdTenantIdAndUserAccountIdAndStatus(tenantId, userAccountId, IdentityLinkStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("No active identity link for this account"));

        return switch (link.getUsernameKind()) {
            case EMAIL -> account.getEmail();
            case MOBILE -> account.getMobile();
            case STUDENT_CODE -> studentProfileRepository.findByTenantIdAndUserAccountId(tenantId, userAccountId)
                    .map(profile -> profile.getStudentCode())
                    .orElseThrow(() -> new InvalidCredentialsException("No student profile for this account"));
        };
    }

    private String realmOf(Resolution resolution) {
        return switch (resolution) {
            case PlatformLogin platformLogin -> platformLogin.realm();
            case TenantLogin tenantLogin -> tenantLogin.tenant().getKeycloakRealm();
        };
    }

    private AuthTokenResponse toResponse(Resolution resolution, KeycloakAuthClient.TokenResult tokens) {
        AuthUserResponse user = switch (resolution) {
            case TenantLogin tenantLogin -> toTenantUserResponse(tenantLogin.tenant(), tokens);
            case PlatformLogin platformLogin -> toPlatformUserResponse(tokens);
        };
        return AuthTokenResponse.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .expiresInSeconds(tokens.expiresInSeconds())
                .user(user)
                .build();
    }

    private AuthUserResponse toTenantUserResponse(Tenant tenant, KeycloakAuthClient.TokenResult tokens) {
        TenantUserLookup.ResolvedUser resolved = tenantUserLookup.resolveActiveUser(
                tenant, tenant.getKeycloakRealm(), tokens.keycloakId());

        if (resolved.account().getCredentialExpiresAt() != null && resolved.account().getCredentialExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException("Rule R2-35: Printed first-login password has expired after 7 days. Please request an administrator reset.");
        }

        TenantPrincipal principal = new TenantPrincipal(tenant.getTenantId(),
                resolved.account().getId().getUserAccountId(), resolved.account().getUserType(),
                resolved.account().getDisplayName(), tokens.mustChangePassword());
        RoleInfo role = resolveRole(tenant.getTenantId(), principal.userAccountId());
        String photoUrl = resolvePhotoUrl(tenant.getTenantId(), principal.userAccountId());
        boolean mfaRequired = role.roleCode() != null && MFA_REQUIRED_ROLES.contains(role.roleCode());
        return AuthUserResponse.fromTenant(principal, resolved.account().getDisplayName(), role.roleCode(), role.roleName(), photoUrl, mfaRequired);
    }

    /** Shared with ProfileService (see its own resolvePhotoUrl) so /api/auth/me, login and the
     *  profile screen all agree on exactly which stored file counts as "the current photo". */
    private String resolvePhotoUrl(UUID tenantId, UUID userAccountId) {
        boolean hasPhoto = fileObjectRepository
                .findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                        tenantId, userAccountId, PHOTO_PURPOSE, STATUS_ACTIVE, SCAN_CLEAN)
                .isPresent();
        return hasPhoto ? "/me/profile/photo" : null;
    }

    /**
     * Resolves the display-only role name/code for a tenant user: every ACTIVE assignment they
     * hold (scope-agnostic - see UserRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus),
     * filtered to ones currently in their effective window, preferring a TENANT-scoped assignment
     * over an INSTITUTION/DEPARTMENT one if both exist. Returns RoleInfo.none() if the user has
     * no resolvable active role yet - the frontend falls back to its generic dashboard in that case,
     * same as before this existed. This never grants anything by itself - it is read-only display
     * data; PermissionEvaluatorService is still the only thing that decides what an action can do.
     */
    private RoleInfo resolveRole(UUID tenantId, UUID userAccountId) {
        Instant now = Instant.now();
        List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, userAccountId, AssignmentStatus.ACTIVE)
                .stream()
                .filter(a -> (a.getEffectiveFrom() == null || !a.getEffectiveFrom().isAfter(now))
                        && (a.getEffectiveTo() == null || a.getEffectiveTo().isAfter(now)))
                .sorted(Comparator.comparing(a -> a.getScopeType() == ScopeType.TENANT ? 0 : 1))
                .toList();
        if (assignments.isEmpty()) {
            return RoleInfo.none();
        }
        UserRoleAssignment chosen = assignments.get(0);
        RoleDefinition definition = roleDefinitionRepository
                .findById(new RoleDefinitionId(tenantId, chosen.getRoleDefinitionId()))
                .orElse(null);
        return definition == null ? RoleInfo.none() : new RoleInfo(definition.getRoleCode(), definition.getRoleName());
    }

    private record RoleInfo(String roleCode, String roleName) {
        static RoleInfo none() {
            return new RoleInfo(null, null);
        }
    }

    private AuthUserResponse toPlatformUserResponse(KeycloakAuthClient.TokenResult tokens) {
        PlatformUser user = platformUserRepository.findByIdentitySubject(tokens.keycloakId())
                .filter(u -> u.getStatus() == PlatformUserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("No active platform account for this identity"));
        PlatformPrincipal principal = new PlatformPrincipal(user.getPlatformUserId(), user.getPlatformRole(),
                user.getEmail(), tokens.mustChangePassword());
        return AuthUserResponse.fromPlatform(principal, true);
    }
}
