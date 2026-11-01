package com.edu360.api.attendance.dto;

import java.time.LocalDate;

import com.edu360.api.attendance.AttendanceStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * What one attendance record looks like when it's sent back over the API.
 * This is a simple data holder (a DTO) — it doesn't do anything itself, it
 * just carries the course name/student name alongside the IDs so the
 * frontend doesn't need a second request to show readable names.
 */
@Data
@AllArgsConstructor
@Builder
public class AttendanceResponse {
    private Long id;
    private Long courseId;
    private String courseName;
    private Long studentId;
    private String studentName;
    private LocalDate date;
    private AttendanceStatus status;
}
