package com.edu360.api.identity;

import org.springframework.stereotype.Component;

import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: the one place that turns "this subject, in this realm, belongs to this tenant" into a
 * real, active UserAccount - shared by both the bearer-token converter
 * (TenantIssuerAuthenticationManagerResolver, after JWT verification succeeds) and
 * AuthService.login (after Keycloak's own password check succeeds), so the two call sites can
 * never drift into checking different things.
 *
 * SECURITY: callers must only invoke this with a tenant and subject that already came from a
 * trusted source - a cryptographically verified JWT, or Keycloak's own just-issued token after
 * a successful password grant. This class sets TenantContext itself (required before the
 * RLS-protected app.user_identity_link/app.user_account queries below can see anything at all),
 * so by the time it runs, the caller must already be certain the tenant is the right one.
 */
@Component
@RequiredArgsConstructor
public class TenantUserLookup {

    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final UserAccountRepository userAccountRepository;

    public record ResolvedUser(UserAccount account, UserIdentityLink link) {
    }

    public ResolvedUser resolveActiveUser(Tenant tenant, String realm, String subject) {
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef());

        UserIdentityLink link = userIdentityLinkRepository
                .findByIdTenantIdAndIdentityRealmAndIdentitySubjectAndStatus(
                        tenant.getTenantId(), realm, subject, IdentityLinkStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("No active account for this identity"));

        UserAccount account = userAccountRepository
                .findById(new UserAccountId(tenant.getTenantId(), link.getUserAccountId()))
                .filter(a -> a.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("Account is not active"));

        return new ResolvedUser(account, link);
    }
}
