package com.edu360.api.selfservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffLeaveBalanceResponse(
        UUID leaveBalanceId,
        UUID staffProfileId,
        UUID leaveTypeId,
        short leaveYear,
        BigDecimal creditedDays,
        BigDecimal usedDays,
        BigDecimal availableDays) {}
