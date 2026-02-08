package com.edu360.api.attendance.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MarkAttendanceRequest {

    @NotNull(message = "Course ID is required")
    private Long courseId;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @Valid
    @NotNull(message = "Attendance records are required")
    private List<AttendanceEntry> records;
}
