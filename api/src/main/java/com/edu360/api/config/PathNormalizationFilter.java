package com.edu360.api.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that transparently normalizes duplicate path prefixes (e.g. /api/api/... or /api/v1/...)
 * caused by frontend client base-URL concatenations or external scripts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PathNormalizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null) {
            if (uri.startsWith("/api/api/")) {
                final String normalizedUri = uri.substring(4); // /api/api/... -> /api/...
                filterChain.doFilter(new NormalizedRequestWrapper(request, normalizedUri), response);
                return;
            } else if (uri.startsWith("/api/v1/")) {
                final String normalizedUri = uri.substring(4); // /api/v1/... -> /v1/...
                filterChain.doFilter(new NormalizedRequestWrapper(request, normalizedUri), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static class NormalizedRequestWrapper extends HttpServletRequestWrapper {
        private final String normalizedUri;

        public NormalizedRequestWrapper(HttpServletRequest request, String normalizedUri) {
            super(request);
            this.normalizedUri = normalizedUri;
        }

        @Override
        public String getRequestURI() {
            return normalizedUri;
        }

        @Override
        public String getServletPath() {
            return normalizedUri;
        }
    }
}
