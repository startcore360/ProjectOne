package com.edu360.api.tenant;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * WHAT: guarantees TenantContext.clear() runs after every single request, no matter how it
 * ends (normal response, thrown exception, anything else).
 *
 * WHY a try/finally around the whole filter chain instead of trusting some other cleanup path:
 * TenantContext is a ThreadLocal, and Tomcat's worker threads are pooled and reused across
 * completely unrelated requests. If one request sets a tenant and something throws before any
 * "normal" cleanup runs, the next request on that same thread would silently inherit the
 * previous request's tenant - a real cross-tenant data leak, not just a bug. finally is the
 * only construct that is guaranteed to run regardless of what happens inside doFilter.
 *
 * WHY this is registered as a plain servlet Filter (see TenantContextFilterConfig) instead of a
 * Spring Security filter slotted into the security chain: a plain servlet filter can be placed
 * as the outermost wrapper around EVERYTHING, including Spring Security's own filter chain -
 * guaranteeing the clear() in finally runs even if something inside Spring Security itself
 * misbehaves, not just something inside this app's own controllers.
 */
public class TenantContextClearingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
