package com.edu360.api.attendance.dto;

import com.edu360.api.attendance.AttendanceStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttendanceEntry {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Status is required (PRESENT, ABSENT, LATE)")
    private AttendanceStatus status;
}
