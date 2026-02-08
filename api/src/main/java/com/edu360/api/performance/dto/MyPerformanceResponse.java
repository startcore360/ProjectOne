package com.edu360.api.performance.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class MyPerformanceResponse {
    private Long studentId;
    private String studentName;
    private List<StudentPerformance> coursePerformances;
}
