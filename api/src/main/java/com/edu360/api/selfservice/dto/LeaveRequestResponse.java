package com.edu360.api.selfservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(
        UUID leaveRequestId,
        UUID leaveTypeId,
        String unit,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        BigDecimal daysCounted,
        BigDecimal lossOfPayDays,
        String processCode,
        String status,
        UUID approvalRequestId,
        Instant createdAt) {}
