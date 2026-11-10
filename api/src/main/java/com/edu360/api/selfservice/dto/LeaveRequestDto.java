package com.edu360.api.selfservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record LeaveRequestDto(
        UUID leaveRequestId,
        UUID leaveTypeId,
        String unit,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        BigDecimal daysCounted,
        String processCode,
        String status,
        Instant createdAt
) {}
