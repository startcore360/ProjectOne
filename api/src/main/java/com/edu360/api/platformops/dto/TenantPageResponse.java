package com.edu360.api.platformops.dto;

import java.util.List;

public record TenantPageResponse(
        List<TenantResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
