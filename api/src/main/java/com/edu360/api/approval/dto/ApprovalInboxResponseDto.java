package com.edu360.api.approval.dto;

import java.util.List;

public record ApprovalInboxResponseDto(
        List<ApprovalInboxItemDto> items,
        PageInfoDto page
) {
    public record PageInfoDto(int totalElements, int totalPages, int pageNumber, int pageSize) {}
}
