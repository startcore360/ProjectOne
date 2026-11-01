package com.edu360.api.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * What a teacher sends us to add a student to their course: just the student's ID.
 */
@Data
public class EnrollRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;
}
