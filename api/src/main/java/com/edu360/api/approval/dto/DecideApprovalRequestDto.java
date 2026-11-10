package com.edu360.api.approval.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DecideApprovalRequestDto(
        @NotNull @Min(1) Short stepNo,
        @NotBlank String decision,
        String remark,
        Integer expectedVersion
) {}
