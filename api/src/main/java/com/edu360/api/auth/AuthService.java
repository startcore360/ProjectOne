package com.edu360.api.auth;

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
import com.edu360.api.exception.InvalidCredentialsException;
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

    public AuthTokenResponse login(HttpServletRequest httpRequest, String identifier, String password,
                                    String ipAddress) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);

        KeycloakAuthClient.TokenResult tokens;
        try {
            tokens = keycloakAuthClient.login(realmOf(resolution), identifier, password);
        } catch (InvalidCredentialsException ex) {
            auditLogService.record(AuditEventType.LOGIN_FAILURE, null, identifier, ipAddress, null);
            throw ex;
        }

        AuthTokenResponse response = toResponse(resolution, tokens);
        auditLogService.record(AuditEventType.LOGIN_SUCCESS, null, identifier, ipAddress, null);
        return response;
    }

    public AuthTokenResponse refresh(HttpServletRequest httpRequest, String refreshToken) {
        Resolution resolution = loginTenantResolver.resolve(httpRequest);
        KeycloakAuthClient.TokenResult tokens = keycloakAuthClient.refresh(realmOf(resolution), refreshToken);
        return toResponse(resolution, tokens);
    }

    /**
     * Used both for the forced first-time change and a later voluntary one - the current
     * password is re-verified against Keycloak itself (not trusted from the bearer token alone)
     * before the new one is accepted.
     *
     * WHY realm/email are looked up here from the VERIFIED principal's own tenantId/
     * userAccountId, never from the request's Host header: unlike login/refresh (where the Host
     * header is the only information available before any identity exists at all), this runs on
     * an already-authenticated request - trusting anything other than the token-derived
     * principal here would let a mismatched Host header point the password change at the wrong
     * tenant's Keycloak realm.
     *
     * keycloakSubject comes from the caller (AuthController passes the same already-verified
     * JWT's "sub" claim it authenticated this request with) - cheaper than a second DB lookup
     * for something the controller already has in hand.
     */
    public AuthUserResponse changeTenantPassword(TenantPrincipal principal, String keycloakSubject,
                                                   String currentPassword, String newPassword, String ipAddress) {
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new InvalidCredentialsException("Tenant no longer exists"));
        UserAccount account = userAccountRepository
                .findById(new UserAccountId(principal.tenantId(), principal.userAccountId()))
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));

        String identifier = identifierFor(principal.tenantId(), principal.userAccountId(), account);
        keycloakAuthClient.login(tenant.getKeycloakRealm(), identifier, currentPassword);
        keycloakAdminClient.resetPassword(tenant.getKeycloakRealm(), keycloakSubject, newPassword);

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, identifier, ipAddress, null);
        // Not just AuthUserResponse.fromTenant(principal, ...): principal is the token-derived
        // snapshot from BEFORE this change, so its mustChangePassword is already stale - found
        // by actually calling this endpoint and seeing the response still say "true" right after
        // successfully clearing it. resetPassword(...) above just cleared the flag in Keycloak,
        // so the response has to say false explicitly, not echo what the caller walked in with.
        TenantPrincipal updated = new TenantPrincipal(principal.tenantId(), principal.userAccountId(),
                principal.userType(), principal.displayName(), false);
        return AuthUserResponse.fromTenant(updated, account.getDisplayName());
    }

    public AuthUserResponse changePlatformPassword(PlatformPrincipal principal, String keycloakSubject,
                                                     String currentPassword, String newPassword, String ipAddress) {
        String realm = keycloakProperties.getPlatformRealm();
        keycloakAuthClient.login(realm, principal.email(), currentPassword);
        keycloakAdminClient.resetPassword(realm, keycloakSubject, newPassword);

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, principal.email(), ipAddress, null);
        PlatformPrincipal updated = new PlatformPrincipal(
                principal.platformUserId(), principal.platformRole(), principal.email(), false);
        return AuthUserResponse.fromPlatform(updated);
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
        TenantPrincipal principal = new TenantPrincipal(tenant.getTenantId(),
                resolved.account().getId().getUserAccountId(), resolved.account().getUserType(),
                resolved.account().getDisplayName(), tokens.mustChangePassword());
        return AuthUserResponse.fromTenant(principal, resolved.account().getDisplayName());
    }

    private AuthUserResponse toPlatformUserResponse(KeycloakAuthClient.TokenResult tokens) {
        PlatformUser user = platformUserRepository.findByIdentitySubject(tokens.keycloakId())
                .filter(u -> u.getStatus() == PlatformUserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("No active platform account for this identity"));
        PlatformPrincipal principal = new PlatformPrincipal(user.getPlatformUserId(), user.getPlatformRole(),
                user.getEmail(), tokens.mustChangePassword());
        return AuthUserResponse.fromPlatform(principal);
    }
}
