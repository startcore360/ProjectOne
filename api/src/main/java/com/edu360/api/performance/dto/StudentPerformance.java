package com.edu360.api.performance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * One student's performance in one course: their average score and how much of class
 * they've attended. This is the building block both performance reports are made of.
 */
@Data
@AllArgsConstructor
@Builder
public class StudentPerformance {
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private Long courseId;
    private String courseName;
    private double averageScorePercentage;
    private long totalClasses;
    private long classesAttended;
    private double attendancePercentage;
}
