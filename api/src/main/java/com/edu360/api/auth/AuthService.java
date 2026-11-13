package com.edu360.api.auth;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAdminClient.CredentialInfo;
import com.edu360.api.config.keycloak.KeycloakAdminClient.KeycloakUser;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient.AccountSetupRequiredException;
import com.edu360.api.config.keycloak.KeycloakAuthClient.PasswordCheck;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService;
import com.edu360.api.exception.AuthChallengeException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.StudentLoginLookup;
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
import com.edu360.api.platformops.security.SecurityPolicyService;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.SessionService;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.LoginTenantResolver.PlatformLogin;
import com.edu360.api.tenant.LoginTenantResolver.Resolution;
import com.edu360.api.tenant.LoginTenantResolver.TenantLogin;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: the real work behind login/refresh/change-password - across two identity spaces (tenant
 * realms and the platform realm, see LoginTenantResolver).
 *
 * SIGN-IN (Sprint 1 batch B3). Keycloak checks every credential; this class decides what to ask
 * for next and never holds a failure count of its own:
 *
 * 1. Which account (I-02): staff and platform users type their email (the Keycloak username).
 *    A college student types their Student ID: when a tenant sign-in identifier is not an
 *    email, it is looked up as a student code (StudentLoginLookup) and the owning account's
 *    Keycloak username is used.
 * 2. Without a code: the password is first proven through the realm's password-check client
 *    (KeycloakAuthClient.verifyPassword - no session is created). Only then does the API look
 *    at the account and answer, to that caller only:
 *    - MFA_ENROLMENT_REQUIRED (401) when a two-step role is held but no authenticator is set up
 *      - with enrolmentUrl, Keycloak's own account page, where CONFIGURE_TOTP shows the QR code;
 *    - MFA_REQUIRED (401) when an authenticator is set up - the screen then asks for the code;
 *    - otherwise the normal password grant and tokens.
 * 3. With a code: one password grant carrying "totp"; Keycloak's edu360-direct-grant flow
 *    checks it. A wrong code is Keycloak's invalid_grant -> 401, counted by Keycloak.
 * 4. Lockout (I-03): Keycloak brute-force protection only. After any refused attempt the API
 *    asks Keycloak whether the account is now locked and answers 423 LOCKED if so. Nothing is
 *    kept in this process, so every node agrees and a restart changes nothing; admin unlock is
 *    Keycloak's own (UserPasswordResetUnlockController).
 * 5. Two-step role list (MfaRequirementService): any held role in the list switches it on; the
 *    mfa_required attribute is corrected here if it was missing, and a token that was issued
 *    without the second step for such an account is ended at once and refused.
 * 6. Printed student passwords (R2-35, I-05): refused 7 days after issue
 *    (app.user_account.credential_expires_at); the check runs before any token is issued.
 * 7. Rotation (R2-12, story 3): a password older than the policy's rotation period (120 days)
 *    sets must_change_password, so MustChangePasswordFilter sends the user to the change screen.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
    private final SessionService sessionService;
    private final PasswordPolicyService passwordPolicyService;
    private final MfaRequirementService mfaRequirementService;
    private final KeycloakRealmSecurityService realmSecurityService;
    private final SecurityPolicyService securityPolicyService;
    private final StudentLoginLookup studentLoginLookup;

    private static final String PHOTO_PURPOSE = "PROFILE_PHOTO";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String SCAN_CLEAN = "CLEAN";

    /** Kept for existing callers; the list itself lives in MfaRequirementService. */
    static final Set<String> MFA_REQUIRED_ROLES = MfaRequirementService.MFA_REQUIRED_ROLES;

    public AuthTokenResponse login(HttpServletRequest httpRequest, String identifier, String password,
                                   String ipAddress) {
        return login(httpRequest, identifier, password, null, ipAddress);
    }

    public AuthTokenResponse login(HttpServletRequest httpRequest, String identifier, String password,
                                   String totp, String ipAddress) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);
        String realm = realmOf(resolution);
        String username = keycloakUsernameFor(resolution, identifier);

        KeycloakAuthClient.TokenResult tokens;
        if (totp == null || totp.isBlank()) {
            tokens = signInWithoutCode(resolution, realm, identifier, username, password, ipAddress);
        } else {
            tokens = signInWithCode(resolution, realm, identifier, username, password, totp, ipAddress);
        }

        AuthTokenResponse response = toResponse(resolution, tokens);
        auditLogService.record(AuditEventType.LOGIN_SUCCESS, null, identifier, ipAddress,
                totp == null || totp.isBlank() ? null : "Two-step login completed");
        recordSignedInDevice(httpRequest, resolution, response, tokens, ipAddress);
        return response;
    }

    /**
     * POST /api/auth/mfa/enrol - finishes an in-app authenticator set-up (MFA_ENROLMENT_REQUIRED).
     * The password is proven again (through the same password-check client, so wrong guesses
     * count towards Keycloak's lockout exactly like sign-in), then Keycloak's extension checks
     * the code against the secret it holds and saves the authenticator. No tokens are issued
     * here: the screen then asks for the NEXT code and signs in normally, because Keycloak does
     * not accept the same code twice.
     */
    public void confirmMfaEnrolment(HttpServletRequest httpRequest, String identifier, String password,
                                    String enrolmentId, String code, String deviceName, String ipAddress) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);
        String realm = realmOf(resolution);
        String username = keycloakUsernameFor(resolution, identifier);

        PasswordCheck check = keycloakAuthClient.verifyPassword(realm, username, password,
                realmSecurityService.passwordCheckSecret(realm));
        if (check != PasswordCheck.VERIFIED) {
            throw refused(realm, username, identifier, ipAddress, false);
        }

        KeycloakAdminClient.OtpConfirmResult result =
                keycloakAdminClient.confirmOtpEnrolment(realm, username, enrolmentId, code, deviceName);
        switch (result.outcome()) {
            case CONFIRMED -> auditLogService.record(AuditEventType.MFA_ENROLLED, null, identifier, ipAddress,
                    "Authenticator app set up in the EDU 360 sign-in screen");
            case INVALID_CODE -> {
                auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress,
                        "Authenticator set-up: wrong code");
                int left = result.attemptsLeft();
                throw new AuthChallengeException(400, "INVALID_ENROLMENT_CODE",
                        "That code didn't match. Check the time on your phone and enter the current code"
                                + (left > 0 ? " (" + left + " attempt" + (left == 1 ? "" : "s") + " left)." : "."),
                        Map.of("attemptsLeft", left));
            }
            case ALREADY_ENROLLED -> throw new AuthChallengeException(409, "ALREADY_ENROLLED",
                    "An authenticator is already set up for this account. Sign in with its code.");
            case TOO_MANY_ATTEMPTS, NOT_FOUND -> {
                auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress,
                        "Authenticator set-up expired or too many wrong codes");
                throw new AuthChallengeException(410, "ENROLMENT_EXPIRED",
                        "This set-up has expired or had too many wrong codes. Sign in again to start over.");
            }
        }
    }

    // --------------------------------------------------------------------- sign-in steps

    private KeycloakAuthClient.TokenResult signInWithoutCode(Resolution resolution, String realm, String identifier,
                                                             String username, String password, String ipAddress) {
        PasswordCheck check = keycloakAuthClient.verifyPassword(realm, username, password,
                realmSecurityService.passwordCheckSecret(realm));
        if (check != PasswordCheck.VERIFIED) {
            throw refused(realm, username, identifier, ipAddress, false);
        }
        KeycloakUser kcUser = keycloakAdminClient.findUser(realm, username)
                .orElseThrow(() -> refused(realm, username, identifier, ipAddress, false));
        AccountState state = inspectAndCorrect(resolution, realm, kcUser);

        if (state.mfaRequired()) {
            if (!state.hasOtp()) {
                auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress,
                        "Two-step login required; no authenticator set up yet (MFA_ENROLMENT_REQUIRED)");
                throw enrolmentRequired(realm, username);
            }
            auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress,
                    "Password accepted; authenticator code required (MFA_REQUIRED)");
            throw AuthChallengeException.mfaRequired();
        }
        try {
            return keycloakAuthClient.login(realm, username, password);
        } catch (InvalidCredentialsException ex) {
            throw refused(realm, username, identifier, ipAddress, false);
        } catch (AccountSetupRequiredException ex) {
            // The password is proven but Keycloak still holds a required step for this account
            // (the attribute was set by another path after inspectAndCorrect read it).
            throw enrolmentRequired(realm, username);
        }
    }

    private KeycloakAuthClient.TokenResult signInWithCode(Resolution resolution, String realm, String identifier,
                                                          String username, String password, String totp,
                                                          String ipAddress) {
        KeycloakAuthClient.TokenResult tokens;
        try {
            tokens = keycloakAuthClient.login(realm, username, password, totp);
        } catch (InvalidCredentialsException | AccountSetupRequiredException ex) {
            throw refused(realm, username, identifier, ipAddress, true);
        }
        KeycloakUser kcUser = keycloakAdminClient.getUser(realm, tokens.keycloakId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        boolean keycloakAskedForCode = kcUser.attributeTrue(KeycloakAdminClient.MFA_REQUIRED_ATTRIBUTE);
        AccountState state;
        try {
            state = inspectAndCorrect(resolution, realm, kcUser);
        } catch (RuntimeException ex) {
            endSessionQuietly(realm, tokens.sessionId());
            throw ex;
        }
        if (state.mfaRequired() && !keycloakAskedForCode) {
            // Keycloak issued this token without the second step (the flag was missing until
            // just now). Never hand it out: end the session and ask again.
            endSessionQuietly(realm, tokens.sessionId());
            throw state.hasOtp() ? AuthChallengeException.mfaRequired() : enrolmentRequired(realm, username);
        }
        if (state.rotationDue() && !tokens.mustChangePassword() && tokens.refreshToken() != null) {
            // must_change_password was just set; a refreshed token carries it.
            tokens = keycloakAuthClient.refresh(realm, tokens.refreshToken());
        }
        return tokens;
    }

    /** Facts about the account, read after its password was proven. */
    private record AccountState(boolean mfaRequired, boolean hasOtp, boolean rotationDue) {
    }

    /**
     * Reads what the sign-in needs about a proven account and corrects Keycloak where it lags
     * the database: sets/clears mfa_required to match the held roles, sets must_change_password
     * when the rotation period has passed, and refuses an expired printed password (R2-35).
     */
    private AccountState inspectAndCorrect(Resolution resolution, String realm, KeycloakUser kcUser) {
        boolean mfaRequired;
        switch (resolution) {
            case TenantLogin tenantLogin -> {
                Tenant tenant = tenantLogin.tenant();
                TenantUserLookup.ResolvedUser resolved = tenantUserLookup.resolveActiveUser(tenant, realm, kcUser.id());
                Instant expiresAt = resolved.account().getCredentialExpiresAt();
                if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                    throw new BusinessRuleViolationException("PRINTED_PASSWORD_EXPIRED",
                            "Rule R2-35: Printed first-login password has expired after 7 days. Please request an administrator reset.");
                }
                mfaRequired = mfaRequirementService.tenantUserRequiresMfa(tenant.getTenantId(),
                        resolved.account().getId().getUserAccountId());
            }
            case PlatformLogin platformLogin -> {
                PlatformUser user = platformUserRepository.findByIdentitySubject(kcUser.id())
                        .filter(u -> u.getStatus() == PlatformUserStatus.ACTIVE)
                        .orElseThrow(() -> new InvalidCredentialsException("No active platform account for this identity"));
                mfaRequired = MfaRequirementService.requiresMfa(List.of(user.getPlatformRole().name()));
            }
        }

        List<CredentialInfo> credentials = keycloakAdminClient.listCredentials(realm, kcUser.id());
        boolean hasOtp = credentials.stream().anyMatch(c -> "otp".equalsIgnoreCase(c.type()));
        boolean attributeSet = kcUser.attributeTrue(KeycloakAdminClient.MFA_REQUIRED_ATTRIBUTE);
        boolean setupActionMissing = mfaRequired && !hasOtp
                && !kcUser.requiredActions().contains(KeycloakAdminClient.CONFIGURE_TOTP_ACTION);
        if (mfaRequired != attributeSet || setupActionMissing) {
            mfaRequirementService.applyToKeycloak(realm, kcUser.id(), mfaRequired);
        }

        Instant passwordSetAt = credentials.stream()
                .filter(c -> "password".equalsIgnoreCase(c.type()) && c.createdAt() != null)
                .map(CredentialInfo::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        boolean rotationDue = passwordPolicyService.isRotationDue(passwordSetAt, Instant.now());
        if (rotationDue && !kcUser.attributeTrue(KeycloakAdminClient.MUST_CHANGE_PASSWORD_ATTRIBUTE)) {
            keycloakAdminClient.setUserAttribute(realm, kcUser.id(), KeycloakAdminClient.MUST_CHANGE_PASSWORD_ATTRIBUTE, "true");
            log.info("Password of Keycloak user {} in realm {} is older than {} days; change required",
                    kcUser.id(), realm, passwordPolicyService.rotationDays());
        }
        return new AccountState(mfaRequired, hasOtp, rotationDue);
    }

    /**
     * A refused attempt: LOCKED (423) when Keycloak now holds the account locked, otherwise 401.
     * The failure has already been counted by Keycloak.
     */
    private AuthChallengeException refused(String realm, String username, String identifier, String ipAddress,
                                           boolean withCode) {
        boolean locked = false;
        try {
            locked = keycloakAdminClient.findUser(realm, username)
                    .map(u -> keycloakAdminClient.isBruteForceLocked(realm, u.id()))
                    .orElse(false);
        } catch (RuntimeException ex) {
            log.warn("Could not read the lockout state from Keycloak for realm {}: {}", realm, ex.getMessage());
        }
        auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress, locked ? "LOCKED" : null);
        if (locked) {
            return AuthChallengeException.locked(securityPolicyService.current().lockoutMinutes());
        }
        return withCode ? AuthChallengeException.invalidCode() : AuthChallengeException.invalidCredentials();
    }

    /**
     * MFA_ENROLMENT_REQUIRED (401). Preferred: an in-app set-up started through the EDU 360
     * Keycloak extension - the answer carries the QR code, the typed secret and a one-time
     * enrolmentId, and the login screen finishes it via POST /api/auth/mfa/enrol. Fallback when
     * the extension is unavailable: enrolmentUrl, Keycloak's own account page.
     * Only ever sent after the password was proven (signInWithoutCode / signInWithCode).
     */
    private AuthChallengeException enrolmentRequired(String realm, String username) {
        Map<String, Object> details = new HashMap<>();
        java.util.Optional<KeycloakAdminClient.OtpEnrolment> enrolment = keycloakAdminClient.startOtpEnrolment(realm, username);
        if (enrolment.isPresent()) {
            details.put("enrolmentId", enrolment.get().enrolmentId());
            details.put("qrCode", enrolment.get().qrCode());
            details.put("secret", enrolment.get().secret());
            details.put("expiresInSeconds", enrolment.get().expiresInSeconds());
            return new AuthChallengeException(401, "MFA_ENROLMENT_REQUIRED",
                    "Two-step login is required for your role. Set up your authenticator app to continue.", details);
        }
        String publicBase = keycloakProperties.getPublicBaseUrl();
        String message;
        if (publicBase != null && !publicBase.isBlank()) {
            details.put("enrolmentUrl", publicBase.replaceAll("/+$", "") + "/realms/" + realm + "/account");
            message = "Two-step login is required for your role. Set up an authenticator app, then sign in again with its code.";
        } else {
            message = "Two-step login is required for your role, but the authenticator set-up page is not configured "
                    + "on this server. Contact your administrator.";
        }
        return new AuthChallengeException(401, "MFA_ENROLMENT_REQUIRED", message, details);
    }

    private void endSessionQuietly(String realm, String sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            keycloakAdminClient.logoutSession(realm, sessionId);
        } catch (RuntimeException ex) {
            log.warn("Could not end refused session in realm {}: {}", realm, ex.getMessage());
        }
    }

    /**
     * I-02: the Keycloak username for what the user typed. For a tenant sign-in an identifier
     * without "@" is first tried as a Student ID; if no active student owns it, it is passed on
     * unchanged (Keycloak decides).
     */
    private String keycloakUsernameFor(Resolution resolution, String identifier) {
        String trimmed = identifier == null ? "" : identifier.trim();
        if (!(resolution instanceof TenantLogin tenantLogin) || trimmed.isEmpty()) {
            return trimmed;
        }
        Tenant tenant = tenantLogin.tenant();
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef());
        Optional<UUID> accountId = trimmed.contains("@")
                ? studentLoginLookup.accountForVerifiedEmailAlias(trimmed)
                : studentLoginLookup.accountForStudentCode(trimmed);
        if (accountId.isEmpty()) {
            return trimmed;
        }
        return userIdentityLinkRepository
                .findFirstByIdTenantIdAndUserAccountIdAndStatus(tenant.getTenantId(), accountId.get(), IdentityLinkStatus.ACTIVE)
                .filter(link -> tenant.getKeycloakRealm().equals(link.getIdentityRealm()))
                .flatMap(link -> keycloakAdminClient.getUser(link.getIdentityRealm(), link.getIdentitySubject()))
                .map(KeycloakUser::username)
                .orElse(trimmed);
    }

    // ----------------------------------------------------------------------- other actions

    /** POST /api/auth/logout: end the caller's current Keycloak session (never throws). */
    public void endSession(Object principal, org.springframework.security.oauth2.jwt.Jwt jwt) {
        sessionService.signOut(principal, jwt);
    }

    /** Client header carrying a random per-browser id (SES-01: same browser replaces its old session). */
    public static final String BROWSER_ID_HEADER = "X-Browser-Id";

    /**
     * AUTH_DEVICES_MANAGE / SES-01: remember which device this new session is on. Runs after
     * toResponse, so for a tenant sign-in TenantContext is already set (TenantUserLookup) and the
     * RLS-scoped insert into app.user_device is allowed. Best effort - never fails the sign-in.
     */
    private void recordSignedInDevice(HttpServletRequest httpRequest, Resolution resolution,
                                      AuthTokenResponse response, KeycloakAuthClient.TokenResult tokens,
                                      String ipAddress) {
        if (response.user() == null || response.user().id() == null) {
            return;
        }
        SessionService.SignInContext ctx = new SessionService.SignInContext(
                tokens.sessionId(),
                httpRequest.getHeader(BROWSER_ID_HEADER),
                httpRequest.getHeader("User-Agent"),
                ipAddress);
        switch (resolution) {
            case TenantLogin tenantLogin -> sessionService.recordTenantSignIn(tenantLogin.tenant(), response.user().id(), ctx);
            case PlatformLogin platformLogin -> sessionService.recordPlatformSignIn(response.user().id(), ctx);
        }
    }

    /**
     * Identity events (POST /v1/internal/identity-events) used to clear an in-process lockout
     * counter here. Lockout now lives only in Keycloak (I-03), which resets it itself on a
     * successful sign-in, so there is nothing in this process to clear. Kept so the identity
     * event contract (batch B0) is unchanged.
     */
    public void clearLockout(String identifier) {
        // Intentionally empty: see the comment above.
    }

    /** R2-12 / I-06: the saved security policy's password rules (PasswordPolicyService). */
    public void validatePasswordPolicy(String password) {
        passwordPolicyService.validate(password);
    }

    /**
     * Builds the /api/auth/me response for an already-authenticated tenant principal, including
     * their resolved role (see {@link #resolveRole}).
     */
    public AuthUserResponse currentTenantUser(TenantPrincipal principal) {
        RoleInfo role = resolveRole(principal.tenantId(), principal.userAccountId());
        String photoUrl = resolvePhotoUrl(principal.tenantId(), principal.userAccountId());
        boolean mfaRequired = mfaRequirementService.tenantUserRequiresMfa(principal.tenantId(), principal.userAccountId());
        return AuthUserResponse.fromTenant(principal, principal.displayName(), role.roleCode(), role.roleName(), photoUrl, mfaRequired);
    }

    public AuthTokenResponse refresh(HttpServletRequest httpRequest, String refreshToken) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);
        KeycloakAuthClient.TokenResult tokens = keycloakAuthClient.refresh(realmOf(resolution), refreshToken);
        return toResponse(resolution, tokens);
    }

    /**
     * Own password change. The current password is proven through the password-check client
     * (works for two-step accounts too, and creates no session); a wrong one is counted by
     * Keycloak like any failed sign-in (I-03, REG-I03-4) and can lock the account.
     */
    public AuthUserResponse changeTenantPassword(TenantPrincipal principal, String keycloakSubject,
                                                 String currentPassword, String newPassword, String ipAddress) {
        passwordPolicyService.validate(newPassword);
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new InvalidCredentialsException("Tenant no longer exists"));
        UserAccount account = userAccountRepository
                .findById(new UserAccountId(principal.tenantId(), principal.userAccountId()))
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));

        String realm = tenant.getKeycloakRealm();
        String identifier = identifierFor(principal.tenantId(), principal.userAccountId(), account);
        verifyCurrentPassword(realm, keycloakSubject, identifier, currentPassword, ipAddress);
        keycloakAdminClient.resetPassword(realm, keycloakSubject, newPassword);
        if (account.getCredentialExpiresAt() != null) {
            // R2-35: the 7-day limit applies to the printed password only, never to the user's own.
            account.setCredentialExpiresAt(null);
            userAccountRepository.save(account);
        }

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, identifier, ipAddress, null);
        TenantPrincipal updated = new TenantPrincipal(principal.tenantId(), principal.userAccountId(),
                principal.userType(), principal.displayName(), false);
        RoleInfo role = resolveRole(principal.tenantId(), principal.userAccountId());
        String photoUrl = resolvePhotoUrl(principal.tenantId(), principal.userAccountId());
        boolean mfaRequired = mfaRequirementService.tenantUserRequiresMfa(principal.tenantId(), principal.userAccountId());
        return AuthUserResponse.fromTenant(updated, account.getDisplayName(), role.roleCode(), role.roleName(), photoUrl, mfaRequired);
    }

    public AuthUserResponse changePlatformPassword(PlatformPrincipal principal, String keycloakSubject,
                                                   String currentPassword, String newPassword, String ipAddress) {
        passwordPolicyService.validate(newPassword);
        String realm = keycloakProperties.getPlatformRealm();
        verifyCurrentPassword(realm, keycloakSubject, principal.email(), currentPassword, ipAddress);
        keycloakAdminClient.resetPassword(realm, keycloakSubject, newPassword);

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, principal.email(), ipAddress, null);
        PlatformPrincipal updated = new PlatformPrincipal(
                principal.platformUserId(), principal.platformRole(), principal.email(), false);
        return AuthUserResponse.fromPlatform(updated, true);
    }

    private void verifyCurrentPassword(String realm, String keycloakSubject, String identifier, String currentPassword,
                                       String ipAddress) {
        PasswordCheck check = keycloakAuthClient.verifyPassword(realm, identifier, currentPassword,
                realmSecurityService.passwordCheckSecret(realm));
        if (check != PasswordCheck.VERIFIED) {
            boolean locked = false;
            try {
                locked = keycloakAdminClient.isBruteForceLocked(realm, keycloakSubject);
            } catch (RuntimeException ex) {
                log.warn("Could not read the lockout state from Keycloak for realm {}: {}", realm, ex.getMessage());
            }
            auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress,
                    "Change password: current password refused" + (locked ? " (LOCKED)" : ""));
            if (locked) {
                throw AuthChallengeException.locked(securityPolicyService.current().lockoutMinutes());
            }
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        // The check itself is one "failure" in Keycloak's record (see verifyPassword). The user
        // has just proven the password inside an authenticated session, so clear that record,
        // as a successful sign-in would.
        try {
            keycloakAdminClient.clearBruteForceLockout(realm, keycloakSubject);
        } catch (RuntimeException ex) {
            log.warn("Could not clear the password-check entry in Keycloak for realm {}: {}", realm, ex.getMessage());
        }
    }

    /**
     * WHAT: picks the right value to re-verify the current password against Keycloak with -
     * email, student code, or mobile, per AUTH_LOGIN's own per-role login rule (§8.1).
     */
    private String identifierFor(UUID tenantId, UUID userAccountId, UserAccount account) {
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
            endSessionQuietly(tenant.getKeycloakRealm(), tokens.sessionId());
            throw new BusinessRuleViolationException("PRINTED_PASSWORD_EXPIRED",
                    "Rule R2-35: Printed first-login password has expired after 7 days. Please request an administrator reset.");
        }

        TenantPrincipal principal = new TenantPrincipal(tenant.getTenantId(),
                resolved.account().getId().getUserAccountId(), resolved.account().getUserType(),
                resolved.account().getDisplayName(), tokens.mustChangePassword());
        RoleInfo role = resolveRole(tenant.getTenantId(), principal.userAccountId());
        String photoUrl = resolvePhotoUrl(tenant.getTenantId(), principal.userAccountId());
        boolean mfaRequired = mfaRequirementService.tenantUserRequiresMfa(tenant.getTenantId(), principal.userAccountId());
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
     * Resolves the display-only role name/code for a tenant user (TENANT-scoped assignment
     * preferred). Read-only display data; PermissionEvaluatorService still decides every action.
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
