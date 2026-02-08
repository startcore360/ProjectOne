package com.edu360.api.score;

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

import com.edu360.api.score.dto.ScoreResponse;
import com.edu360.api.score.dto.SubmitScoresRequest;
import com.edu360.api.user.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
@Tag(name = "Scores", description = "Submit and view assessment scores")
public class ScoreController {

    private final ScoreService scoreService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Submit scores", description = "TEACHER only — bulk submit scores for an assessment in a course the teacher owns.")
    public ResponseEntity<List<ScoreResponse>> submitScores(
            @Valid @RequestBody SubmitScoresRequest request,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scoreService.submitScores(request, teacher));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get scores by course", description = "TEACHER only — view all scores for a course the teacher owns.")
    public ResponseEntity<List<ScoreResponse>> getScoresByCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(scoreService.getScoresByCourse(courseId, teacher));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my scores", description = "STUDENT only — view your own scores across all enrolled courses.")
    public ResponseEntity<List<ScoreResponse>> getMyScores(
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(scoreService.getMyScores(student));
    }

    @GetMapping("/me/course/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my scores for a course", description = "STUDENT only — view your own scores for a specific course.")
    public ResponseEntity<List<ScoreResponse>> getMyScoresForCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(scoreService.getMyScoresForCourse(courseId, student));
    }
}
