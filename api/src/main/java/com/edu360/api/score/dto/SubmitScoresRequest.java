package com.edu360.api.score.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SubmitScoresRequest {

    @NotNull(message = "Course ID is required")
    private Long courseId;

    @NotBlank(message = "Assessment name is required")
    private String assessmentName;

    @NotNull(message = "Max score is required")
    @Positive(message = "Max score must be positive")
    private Double maxScore;

    @Valid
    @NotNull(message = "Score entries are required")
    private List<ScoreEntry> scores;
}
