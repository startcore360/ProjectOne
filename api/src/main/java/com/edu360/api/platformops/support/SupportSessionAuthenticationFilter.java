package com.edu360.api.platformops.support;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.SupportPrincipal;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import com.edu360.api.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/** Authenticates API-signed support tokens before Keycloak bearer-token validation. */
public class SupportSessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern TENANT_PATH = Pattern.compile("^/(?:api|v1)/tenants/([^/]+)(?:/.*)?$");

    private final SupportSessionTokenService tokenService;
    private final SupportSessionRepository sessionRepository;
    private final SupportAccessGrantRepository grantRepository;
    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbc;
    private final PlatformAuditWriter platformAuditWriter;
    private final AuditLogService tenantAuditService;

    public SupportSessionAuthenticationFilter(SupportSessionTokenService tokenService,
            SupportSessionRepository sessionRepository, SupportAccessGrantRepository grantRepository,
            TenantRepository tenantRepository, JdbcTemplate jdbc, PlatformAuditWriter platformAuditWriter,
            AuditLogService tenantAuditService) {
        this.tokenService = tokenService;
        this.sessionRepository = sessionRepository;
        this.grantRepository = grantRepository;
        this.tenantRepository = tenantRepository;
        this.jdbc = jdbc;
        this.platformAuditWriter = platformAuditWriter;
        this.tenantAuditService = tenantAuditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        String token = authorization.substring(7).trim();
        if (!tokenService.isSupportToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        Matcher path = TENANT_PATH.matcher(request.getRequestURI());
        if (!path.matches() || request.getRequestURI().startsWith("/api/platform/")
                || request.getRequestURI().startsWith("/v1/platform/")
                || request.getRequestURI().equals("/api/me") || request.getRequestURI().startsWith("/api/me/")
                || request.getRequestURI().equals("/v1/me") || request.getRequestURI().startsWith("/v1/me/")) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SUPPORT_SESSION_PATH_FORBIDDEN");
            return;
        }

        SupportSessionTokenService.Claims claims = tokenService.verify(token).orElse(null);
        if (claims == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SUPPORT_SESSION");
            return;
        }
        UUID pathTenantId;
        try {
            pathTenantId = UUID.fromString(path.group(1));
        } catch (IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SUPPORT_TENANT_MISMATCH");
            return;
        }
        if (!claims.tenantId().equals(pathTenantId)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SUPPORT_TENANT_MISMATCH");
            return;
        }

        SupportSession session = sessionRepository.findBySupportSessionIdAndEndedAtIsNull(claims.sessionId()).orElse(null);
        if (session == null || !session.getPlatformUserId().equals(claims.platformUserId())
                || !session.getGrantId().equals(claims.grantId())
                || !session.getExpiresAt().equals(claims.expiresAt())) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SUPPORT_SESSION_ENDED");
            return;
        }
        Instant now = Instant.now();
        if (!session.getExpiresAt().isAfter(now)) {
            endSession(session, "EXPIRED");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SUPPORT_SESSION_ENDED");
            return;
        }

        SupportAccessGrant grant = grantRepository.findById(claims.grantId()).orElse(null);
        if (grant == null || !grant.getTenantId().equals(claims.tenantId())
                || !grant.getTicketId().equals(claims.ticketId())
                || !java.util.Objects.equals(grant.getInstitutionId(), claims.institutionId())) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SUPPORT_SESSION_ENDED");
            return;
        }
        if (grant.getWithdrawnAt() != null) {
            endSession(session, "WITHDRAWN");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SUPPORT_SESSION_ENDED");
            return;
        }
        if (!grant.getExpiresAt().isAfter(now)) {
            endSession(session, "EXPIRED");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SUPPORT_SESSION_ENDED");
            return;
        }

        Tenant tenant = tenantRepository.findById(claims.tenantId())
                .filter(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL)
                .orElse(null);
        if (tenant == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SUPPORT_SESSION_ENDED");
            return;
        }

        if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SUPPORT_READ_ONLY");
            return;
        }
        Set<String> actionCodes = SupportReadableOperationRegistry
                .actionCodes(request.getMethod(), request.getRequestURI()).orElse(Set.of());
        if (actionCodes.isEmpty() || !allActionsSupportReadable(actionCodes)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SUPPORT_READ_ONLY");
            return;
        }

        if (claims.institutionId() == null) {
            TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                    "ALL", Set.of(), null, null, null, false);
        } else {
            TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                    "LIST", Set.of(claims.institutionId()), null, null, null, false);
        }
        SupportPrincipal principal = new SupportPrincipal(claims.platformUserId(), claims.tenantId(),
                claims.ticketId(), claims.grantId(), claims.sessionId(), claims.institutionId(), null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token, List.of()));

        // Both audit streams are required for every successful support data view.
        tenantAuditService.recordSupportView(principal, request.getRequestURI());
        platformAuditWriter.record(new PlatformPrincipal(claims.platformUserId(), null, null, false),
                claims.tenantId(), "SUPPORT_SESSION_VIEW", "SUPPORT_ACCESS_USE", "SUPPORT_SESSION", claims.sessionId());

        HttpServletRequest withoutBearer = new HttpServletRequestWrapper(request) {
            @Override public String getHeader(String name) {
                return "Authorization".equalsIgnoreCase(name) ? null : super.getHeader(name);
            }
            @Override public java.util.Enumeration<String> getHeaders(String name) {
                return "Authorization".equalsIgnoreCase(name)
                        ? java.util.Collections.emptyEnumeration() : super.getHeaders(name);
            }
        };
        chain.doFilter(withoutBearer, response);
    }

    private boolean allActionsSupportReadable(Set<String> codes) {
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM ref.action_catalog WHERE support_readable IS TRUE AND action_code IN (" + placeholders + ")",
                Integer.class, codes.toArray());
        return count != null && count == codes.size();
    }

    private void endSession(SupportSession session, String reason) {
        session.setEndedAt(Instant.now());
        session.setEndReason(reason);
        sessionRepository.save(session);
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"error\":\"" + code + "\"}");
    }
}
