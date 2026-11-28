package com.edu360.api.selfservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestCreateRequest(
        UUID leaveTypeId,
        LocalDate startDate,
        LocalDate endDate,
        String unit,
        String halfDayPart,
        String reason,
        UUID documentFileId,
        UUID designatedDelegateStaffProfileId) {}
