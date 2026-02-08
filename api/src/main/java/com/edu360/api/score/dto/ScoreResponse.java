package com.edu360.api.score.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ScoreResponse {
    private Long id;
    private Long courseId;
    private String courseName;
    private Long studentId;
    private String studentName;
    private String assessmentName;
    private Double score;
    private Double maxScore;
    private Double percentage;
}
