package com.edu360.api.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnrollRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;
}
