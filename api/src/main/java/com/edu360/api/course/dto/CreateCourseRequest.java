package com.edu360.api.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * What a teacher sends us when they want to create a new course: just a name and a code.
 */
@Data
public class CreateCourseRequest {

    @NotBlank(message = "Course name is required")
    private String name;

    @NotBlank(message = "Course code is required")
    private String code;
}
