package com.edu360.api.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCourseRequest {

    @NotBlank(message = "Course name is required")
    private String name;

    @NotBlank(message = "Course code is required")
    private String code;
}
