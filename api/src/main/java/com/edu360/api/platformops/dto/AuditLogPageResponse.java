package com.edu360.api.platformops.dto;

import java.util.List;

/** Page wrapper for GET /api/platform/audit-logs - a plain record rather than returning Spring's
 *  own Page&lt;T&gt; directly, matching this codebase's existing DTO style (plain records, no
 *  framework pagination types leaking into the API contract). */
public record AuditLogPageResponse(
        List<AuditLogEntryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
