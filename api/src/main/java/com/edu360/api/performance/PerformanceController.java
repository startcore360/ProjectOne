package com.edu360.api.performance;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.performance.dto.CoursePerformanceResponse;
import com.edu360.api.performance.dto.MyPerformanceResponse;
import com.edu360.api.user.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "Aggregated performance analytics")
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get course performance", description = "TEACHER only — view aggregated performance (avg scores, attendance %) for all students in a course.")
    public ResponseEntity<CoursePerformanceResponse> getCoursePerformance(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(performanceService.getCoursePerformance(courseId, teacher));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my performance", description = "STUDENT only — view your aggregated performance (avg scores, attendance %) across all enrolled courses.")
    public ResponseEntity<MyPerformanceResponse> getMyPerformance(
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(performanceService.getMyPerformance(student));
    }
}
