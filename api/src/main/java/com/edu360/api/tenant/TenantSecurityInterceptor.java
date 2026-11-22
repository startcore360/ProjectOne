package com.edu360.api.tenant;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Request-side tenant boundary check. The routing datasource remains the enforcement point
 * for PostgreSQL RLS GUCs; this interceptor rejects URL/header tenant and institution selectors
 * that disagree with the verified request TenantContext before controller execution.
 */
@Component
public class TenantSecurityInterceptor implements HandlerInterceptor {
    private static final Pattern TENANT_PATH = Pattern.compile("/(?:api|v1)/tenants/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern INSTITUTION_PATH = Pattern.compile("/institutions/([0-9a-fA-F-]{36})(?:/|$)");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantId == null) return true; // platform/anonymous requests remain fail-closed at RLS
        Matcher tenantPath = TENANT_PATH.matcher(request.getRequestURI());
        if (tenantPath.find() && !tenantId.toString().equalsIgnoreCase(tenantPath.group(1))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        String requestedTenant = request.getHeader("X-Tenant-ID");
        if (requestedTenant != null && !requestedTenant.isBlank()
                && !tenantId.toString().equalsIgnoreCase(requestedTenant.trim())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        String requestedInstitution = request.getParameter("institutionId");
        if (requestedInstitution == null) requestedInstitution = request.getParameter("institution_id");
        Matcher institutionPath = INSTITUTION_PATH.matcher(request.getRequestURI());
        if (requestedInstitution == null && institutionPath.find()) requestedInstitution = institutionPath.group(1);
        if (requestedInstitution != null && !requestedInstitution.isBlank()
                && !"ALL".equals(TenantContext.currentInstitutionScope())) {
            try {
                UUID institutionId = UUID.fromString(requestedInstitution.trim());
                if (!TenantContext.currentInstitutionIds().contains(institutionId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return false;
                }
            } catch (IllegalArgumentException ex) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }
        return true;
    }
}
