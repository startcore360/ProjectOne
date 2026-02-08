package com.edu360.api.attendance.dto;

import java.time.LocalDate;

import com.edu360.api.attendance.AttendanceStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

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
