package com.edu360.api.score.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ScoreEntry {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Score is required")
    @Positive(message = "Score must be positive")
    private Double score;
}
