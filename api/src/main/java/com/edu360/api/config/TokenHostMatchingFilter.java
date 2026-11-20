package com.edu360.api.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.tenant.LoginTenantResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Binds every authenticated tenant/platform/support token to the request Host. */
public class TokenHostMatchingFilter extends OncePerRequestFilter {
    private final LoginTenantResolver loginTenantResolver;

    public TokenHostMatchingFilter(LoginTenantResolver loginTenantResolver) {
        this.loginTenantResolver = loginTenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        Object principal = authentication.getPrincipal();
        boolean matches;
        try {
            LoginTenantResolver.Resolution host = loginTenantResolver.resolve(request);
            if (principal instanceof PlatformPrincipal) {
                matches = host instanceof LoginTenantResolver.PlatformLogin;
            } else if (principal instanceof TenantPrincipal tenant) {
                matches = host instanceof LoginTenantResolver.TenantLogin login
                        && tenant.tenantId().equals(login.tenant().getTenantId());
            } else if (principal instanceof SupportPrincipal support) {
                matches = host instanceof LoginTenantResolver.TenantLogin login
                        && support.tenantId().equals(login.tenant().getTenantId());
            } else {
                matches = true;
            }
        } catch (InvalidCredentialsException ex) {
            matches = false;
        }

        if (!matches) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"code\":\"TENANT_HOST_MISMATCH\",\"error\":\"TENANT_HOST_MISMATCH\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
