package com.edu360.api.selfservice.dto;

public record StaffLeaveTypeSetRequest(
        String leaveCode,
        String leaveName,
        boolean tracksBalance,
        boolean isLossOfPay) {}
