package com.edu360.api.performance.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * What we send a teacher when they ask for a course's performance report: the course's
 * overall average score, plus a breakdown per student.
 */
@Data
@AllArgsConstructor
@Builder
public class CoursePerformanceResponse {
    private Long courseId;
    private String courseName;
    private double courseAverageScorePercentage;
    private List<StudentPerformance> students;
}
