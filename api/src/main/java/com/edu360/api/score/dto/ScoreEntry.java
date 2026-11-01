package com.edu360.api.score.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * One student's score inside a bulk score submission — see SubmitScoresRequest,
 * which holds a list of these (one per student) for a single assessment.
 */
@Data
public class ScoreEntry {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Score is required")
    @Positive(message = "Score must be positive")
    private Double score;
}
