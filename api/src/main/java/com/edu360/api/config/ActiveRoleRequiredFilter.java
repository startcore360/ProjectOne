package com.edu360.api.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.edu360.api.exception.ActiveRoleRequiredException;
import com.edu360.api.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A-02 (story 6, PO-3): a tenant user who holds several active role assignments and has not
 * chosen one gets 409 ACTIVE_ROLE_REQUIRED on every request except:
 * <ul>
 *   <li>sign-in housekeeping under /api/auth/ (who am I, change password, refresh, sign out);</li>
 *   <li>role switching: POST .../me/active-role;</li>
 *   <li>self-service reads: GET under /api/me or /{api|v1}/tenants/{tenantId}/me.</li>
 * </ul>
 * The flag is computed once per request by TenantUserLookup (after the bearer token was verified)
 * and carried on TenantContext. PermissionEvaluatorService refuses the same case on its own, so a
 * path missed here still cannot act with a role the user did not choose.
 *
 * Registered as a plain servlet filter (Spring Boot picks up the component); it runs after the
 * Spring Security chain, so the authenticated principal and TenantContext are already set.
 */
@Component
public class ActiveRoleRequiredFilter extends OncePerRequestFilter {

    private static final Pattern ROLE_SWITCH = Pattern.compile("^(/api|/v1/tenants/[^/]+|/api/tenants/[^/]+)/me/active-role/?$");
    private static final Pattern SELF_SERVICE = Pattern.compile("^(/api|/v1/tenants/[^/]+|/api/tenants/[^/]+)/me(/.*)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean tenantUser = authentication != null && authentication.getPrincipal() instanceof TenantPrincipal;

        if (tenantUser && TenantContext.isActiveRoleRequired() && !isExempt(request)) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType("application/json");
            String message = "You hold more than one role. Choose the active role before continuing.";
            response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":409,"
                    + "\"code\":\"" + ActiveRoleRequiredException.CODE + "\","
                    + "\"error\":\"" + message + "\",\"message\":\"" + message + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && ROLE_SWITCH.matcher(path).matches()) {
            return true;
        }
        return ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                && SELF_SERVICE.matcher(path).matches();
    }
}
