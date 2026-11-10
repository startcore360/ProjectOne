package com.edu360.api.selfservice.dto;

import java.util.Map;

/**
 * Payload for PUT /v1/tenants/{tenantId}/me/dashboard-preferences (and /api/me/dashboard-preferences).
 */
public record DashboardPreferencesRequest(
        Map<String, Object> preferences
) {}
