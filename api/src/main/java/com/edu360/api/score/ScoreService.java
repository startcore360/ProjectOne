package com.edu360.api.score;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.course.Course;
import com.edu360.api.course.CourseService;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.score.dto.ScoreEntry;
import com.edu360.api.score.dto.ScoreResponse;
import com.edu360.api.score.dto.SubmitScoresRequest;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final CourseService courseService;
    private final UserRepository userRepository;

    @Transactional
    public List<ScoreResponse> submitScores(SubmitScoresRequest request, User teacher) {
        Course course = courseService.verifyTeacherOwnsCourse(request.getCourseId(), teacher);

        List<Score> savedScores = new ArrayList<>();

        for (ScoreEntry entry : request.getScores()) {
            User student = userRepository.findById(entry.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Student not found: " + entry.getStudentId()));

            Score score = Score.builder()
                    .course(course)
                    .student(student)
                    .assessmentName(request.getAssessmentName())
                    .score(entry.getScore())
                    .maxScore(request.getMaxScore())
                    .build();

            savedScores.add(scoreRepository.save(score));
        }

        return savedScores.stream().map(this::toResponse).toList();
    }

    public List<ScoreResponse> getScoresByCourse(Long courseId, User teacher) {
        Course course = courseService.verifyTeacherOwnsCourse(courseId, teacher);
        return scoreRepository.findByCourse(course).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ScoreResponse> getMyScores(User student) {
        return scoreRepository.findByStudent(student).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ScoreResponse> getMyScoresForCourse(Long courseId, User student) {
        Course course = courseService.verifyStudentEnrolled(courseId, student);
        return scoreRepository.findByCourseAndStudent(course, student).stream()
                .map(this::toResponse)
                .toList();
    }

    private ScoreResponse toResponse(Score s) {
        double percentage = (s.getMaxScore() > 0) ? (s.getScore() / s.getMaxScore() * 100) : 0;
        return ScoreResponse.builder()
                .id(s.getId())
                .courseId(s.getCourse().getId())
                .courseName(s.getCourse().getName())
                .studentId(s.getStudent().getId())
                .studentName(s.getStudent().getName())
                .assessmentName(s.getAssessmentName())
                .score(s.getScore())
                .maxScore(s.getMaxScore())
                .percentage(Math.round(percentage * 100.0) / 100.0)
                .build();
    }
}
