package com.edu360.api.attendance;

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

import com.edu360.api.attendance.dto.AttendanceResponse;
import com.edu360.api.attendance.dto.MarkAttendanceRequest;
import com.edu360.api.user.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Mark and view attendance records")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Mark attendance", description = "TEACHER only — bulk mark attendance for students in a course on a given date. Upserts existing records.")
    public ResponseEntity<List<AttendanceResponse>> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.markAttendance(request, teacher));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get attendance by course", description = "TEACHER only — view all attendance records for a course the teacher owns.")
    public ResponseEntity<List<AttendanceResponse>> getAttendanceByCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(attendanceService.getAttendanceByCourse(courseId, teacher));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my attendance", description = "STUDENT only — view your own attendance across all enrolled courses.")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendance(
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(attendanceService.getMyAttendance(student));
    }

    @GetMapping("/me/course/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my attendance for a course", description = "STUDENT only — view your own attendance for a specific course.")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendanceForCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(attendanceService.getMyAttendanceForCourse(courseId, student));
    }
}
