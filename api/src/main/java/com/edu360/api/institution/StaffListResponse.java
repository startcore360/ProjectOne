package com.edu360.api.institution;

import java.util.List;

public record StaffListResponse(List<StaffProfileResponse> items, PageInfo page) {
    public record PageInfo(int limit, String nextCursor, boolean hasMore, Long totalCount) {}
}
