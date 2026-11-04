package com.edu360.api.selfservice;

import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;

import lombok.RequiredArgsConstructor;

/**
 * PROFILE_EDIT_OWN (Sign-in epic, Scope: Self): "see and edit own profile and contact details."
 *
 * WHO "own" is: always resolved from the already-authenticated TenantPrincipal/PlatformPrincipal
 * the caller hands in (ProfileController builds these the same way AuthController.me() does,
 * from the Authentication principal) - never from a path/query parameter, so there is no way to
 * ask for someone else's profile through this class.
 *
 * API SHAPE DEVIATION: the V4.0 spec's own self-service path is /v1/tenants/{tenantId}/me - this
 * app has no such route family yet and every other authenticated endpoint already lives under
 * this app's own /api prefix (see AuthController's class comment, and the same /api-prefix
 * deviation UserCreationController already took for USER_CREATE). GET/PATCH /api/me/profile
 * follows that existing convention instead of introducing a one-off /v1 path.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final PlatformUserRepository platformUserRepository;
    private final AuditLogService auditLogService;

    public ProfileResponse getTenantProfile(TenantPrincipal principal) {
        UserAccount account = loadTenantAccount(principal);
        return ProfileResponse.fromTenant(principal, account);
    }

    public ProfileResponse getPlatformProfile(PlatformPrincipal principal) {
        PlatformUser user = loadPlatformUser(principal);
        return ProfileResponse.fromPlatform(principal, user);
    }

    /** Applies only the fields ProfileUpdateRequest actually declares (displayName, mobile) -
     *  see that class's comment for why role/email are not among them. A null field on the
     *  request means "leave this one unchanged", not "clear it". */
    public ProfileResponse updateTenantProfile(TenantPrincipal principal, ProfileUpdateRequest request) {
        UserAccount account = loadTenantAccount(principal);
        if (request.getDisplayName() != null) {
            account.setDisplayName(request.getDisplayName());
        }
        if (request.getMobile() != null) {
            account.setMobile(request.getMobile());
        }
        account.setUpdatedBy(principal.userAccountId());
        UserAccount saved = userAccountRepository.save(account);
        // Same actor-identifier convention as AuthController.logout: tenant principal -> displayName.
        auditLogService.record(AuditEventType.PROFILE_UPDATED, null, principal.displayName(), null, null);
        return ProfileResponse.fromTenant(principal, saved);
    }

    public ProfileResponse updatePlatformProfile(PlatformPrincipal principal, ProfileUpdateRequest request) {
        PlatformUser user = loadPlatformUser(principal);
        if (request.getDisplayName() != null) {
            user.setFullName(request.getDisplayName());
        }
        // request.getMobile() is deliberately ignored here: platform.platform_user has no
        // mobile column at all (see PlatformUser's class comment on why it is a wholly separate
        // identity space from the tenant user model) - silently accepting it would promise a
        // field this account type can never actually have.
        user.setUpdatedBy(principal.platformUserId());
        PlatformUser saved = platformUserRepository.save(user);
        // Same actor-identifier convention as AuthController.logout: platform principal -> email.
        auditLogService.record(AuditEventType.PROFILE_UPDATED, null, principal.email(), null, null);
        return ProfileResponse.fromPlatform(principal, saved);
    }

    private UserAccount loadTenantAccount(TenantPrincipal principal) {
        return userAccountRepository.findById(new UserAccountId(principal.tenantId(), principal.userAccountId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private PlatformUser loadPlatformUser(PlatformPrincipal principal) {
        return platformUserRepository.findById(principal.platformUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }
}
