package com.edu360.api.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * L-05 / R2-24 / MK-05: a TRIAL tenant whose trial has ended is read-only until it is paid and activated.
 * Reads (GET, HEAD, OPTIONS) still work so people can see and export their data; every other method from a
 * tenant user gets 403 {@code TRIAL_EXPIRED}. Sign-out and the password change stay open. Platform users are
 * never affected.
 */
@Component
public class TrialReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/logout", "/api/auth/change-password", "/api/auth/refresh");

    private final TenantRepository tenantRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TrialReadOnlyFilter(TenantRepository tenantRepository) {
        this(tenantRepository, Clock.systemUTC());
    }

    TrialReadOnlyFilter(TenantRepository tenantRepository, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal
                && !SAFE_METHODS.contains(request.getMethod())
                && !ALLOWED_PATHS.contains(request.getRequestURI())
                && trialHasEnded(principal)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"error\":\"TRIAL_EXPIRED\",\"code\":\"TRIAL_EXPIRED\","
                    + "\"message\":\"The trial has ended. This workspace is read-only until it is paid and activated.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean trialHasEnded(TenantPrincipal principal) {
        Tenant tenant = tenantRepository.findById(principal.tenantId()).orElse(null);
        return tenant != null
                && tenant.getState() == TenantState.TRIAL
                && tenant.getTrialEndsAt() != null
                && tenant.getTrialEndsAt().isBefore(Instant.now(clock));
    }
}
