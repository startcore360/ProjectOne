package com.edu360.api.performance.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class CoursePerformanceResponse {
    private Long courseId;
    private String courseName;
    private double courseAverageScorePercentage;
    private List<StudentPerformance> students;
}
