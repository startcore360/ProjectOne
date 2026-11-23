package com.edu360.api.selfservice.dto;

import java.util.UUID;

public record StaffLeaveTypeResponse(
        UUID leaveTypeId,
        UUID institutionId,
        String leaveCode,
        String leaveName,
        boolean tracksBalance,
        boolean isLossOfPay) {}
