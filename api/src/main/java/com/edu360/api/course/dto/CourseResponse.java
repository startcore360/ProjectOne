package com.edu360.api.course.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class CourseResponse {
    private Long id;
    private String name;
    private String code;
    private TeacherInfo teacher;
    private List<StudentInfo> students;

    @Data
    @AllArgsConstructor
    @Builder
    public static class TeacherInfo {
        private Long id;
        private String name;
        private String email;
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class StudentInfo {
        private Long id;
        private String name;
        private String email;
    }
}
