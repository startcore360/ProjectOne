package com.edu360.api.selfservice;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.selfservice.dto.SessionResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self) - see SessionResponse's class comment for why
 * this is deliberately the single-current-session slice, not a full multi-device list.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final TenantRepository tenantRepository;
    private final KeycloakProperties keycloakProperties;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;

    /** Always a list of at most one element today (the session the caller is making THIS
     *  request with) - a list, not a single object, so the response shape does not have to
     *  change again once Phase 2 adds real multi-session listing. */
    public List<SessionResponse> listCurrentSessions(Jwt jwt) {
        return List.of(toSessionResponse(jwt));
    }

    public void revokeCurrentSession(TenantPrincipal principal, Jwt jwt) {
        String sessionId = requireSessionId(jwt);
        revokeSession(principal, sessionId);
    }

    public void revokeSession(TenantPrincipal principal, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required for revocation");
        }
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no longer exists"));
        keycloakAdminClient.logoutSession(tenant.getKeycloakRealm(), sessionId);
        auditLogService.record(AuditEventType.SESSION_REVOKED, null, principal.displayName(), null, null);
    }

    public void revokeCurrentSession(PlatformPrincipal principal, Jwt jwt) {
        String sessionId = requireSessionId(jwt);
        revokeSession(principal, sessionId);
    }

    public void revokeSession(PlatformPrincipal principal, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required for revocation");
        }
        keycloakAdminClient.logoutSession(keycloakProperties.getPlatformRealm(), sessionId);
        auditLogService.record(AuditEventType.SESSION_REVOKED, null, principal.email(), null, null);
    }

    public void registerPushSubscription(TenantPrincipal principal, String endpoint) {
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.displayName(), null, "Push subscription registered");
    }

    private String requireSessionId(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) {
            // Mapped to 400 by GlobalExceptionHandler, not 404/500: the caller IS authenticated
            // and the request is otherwise well-formed, there is just no "sid" claim on this
            // particular access token to revoke - see SessionResponse's class comment.
            throw new IllegalArgumentException(
                    "This session cannot be revoked: the current access token has no session id");
        }
        return sessionId;
    }

    private SessionResponse toSessionResponse(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        return SessionResponse.builder()
                .sessionId(sessionId)
                .current(true)
                .revocable(sessionId != null && !sessionId.isBlank())
                .issuedAt(jwt.getIssuedAt())
                .expiresAt(jwt.getExpiresAt())
                .build();
    }
}
