package com.edu360.api.performance.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * What we send a student when they ask for their own performance: their name plus a
 * performance summary for every course they're enrolled in.
 */
@Data
@AllArgsConstructor
@Builder
public class MyPerformanceResponse {
    private Long studentId;
    private String studentName;
    private List<StudentPerformance> coursePerformances;
}
