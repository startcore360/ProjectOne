package com.edu360.api.rbac.dto;

import java.util.List;

/** WHAT: the response body for GET /api/tenants/{tenantId}/users (USER_VIEW, list form). */
public record UserListResponse(List<UserSummaryResponse> users) {
}
