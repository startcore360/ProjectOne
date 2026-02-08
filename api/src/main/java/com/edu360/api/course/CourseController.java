package com.edu360.api.course;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.course.dto.CourseResponse;
import com.edu360.api.course.dto.CreateCourseRequest;
import com.edu360.api.course.dto.EnrollRequest;
import com.edu360.api.user.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Courses", description = "Course management — create courses, enroll students")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Create a course", description = "TEACHER only — create a new course assigned to the authenticated teacher.")
    public ResponseEntity<CourseResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request, teacher));
    }

    @GetMapping
    @Operation(summary = "List my courses", description = "Returns courses taught (TEACHER) or enrolled in (STUDENT).")
    public ResponseEntity<List<CourseResponse>> getMyCourses(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(courseService.getMyCourses(user));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get course by ID", description = "Returns a single course. Teachers must own it; students must be enrolled.")
    public ResponseEntity<CourseResponse> getCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(courseService.getCourseById(courseId, user));
    }

    @PostMapping("/{courseId}/enroll")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Enroll a student", description = "TEACHER only — enroll a student into a course the teacher owns.")
    public ResponseEntity<CourseResponse> enrollStudent(
            @PathVariable Long courseId,
            @Valid @RequestBody EnrollRequest request,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(courseService.enrollStudent(courseId, request.getStudentId(), teacher));
    }
}
