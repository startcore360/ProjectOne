package com.edu360.api.performance;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.edu360.api.attendance.AttendanceRepository;
import com.edu360.api.attendance.AttendanceStatus;
import com.edu360.api.course.Course;
import com.edu360.api.course.CourseRepository;
import com.edu360.api.course.CourseService;
import com.edu360.api.performance.dto.CoursePerformanceResponse;
import com.edu360.api.performance.dto.MyPerformanceResponse;
import com.edu360.api.performance.dto.StudentPerformance;
import com.edu360.api.score.ScoreRepository;
import com.edu360.api.user.User;

import lombok.RequiredArgsConstructor;

/**
 * Builds the performance numbers shown to teachers and students: average score and
 * attendance percentage, worked out from the raw scores and attendance records.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final CourseService courseService;
    private final CourseRepository courseRepository;
    private final ScoreRepository scoreRepository;
    private final AttendanceRepository attendanceRepository;

    /**
     * Teacher views performance of all students in a course they own.
     */
    public CoursePerformanceResponse getCoursePerformance(Long courseId, User teacher) {
        Course course = courseService.verifyTeacherOwnsCourse(courseId, teacher);

        List<StudentPerformance> studentPerformances = new ArrayList<>();

        for (User student : course.getStudents()) {
            studentPerformances.add(buildStudentPerformance(student, course));
        }

        Double courseAvg = scoreRepository.getCourseAveragePercentage(course);

        return CoursePerformanceResponse.builder()
                .courseId(course.getId())
                .courseName(course.getName())
                .courseAverageScorePercentage(courseAvg != null ? Math.round(courseAvg * 100.0) / 100.0 : 0)
                .students(studentPerformances)
                .build();
    }

    /**
     * Student views their own performance across all enrolled courses.
     */
    public MyPerformanceResponse getMyPerformance(User student) {
        List<Course> courses = courseRepository.findByStudentsContaining(student);

        List<StudentPerformance> performances = courses.stream()
                .map(course -> buildStudentPerformance(student, course))
                .toList();

        return MyPerformanceResponse.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .coursePerformances(performances)
                .build();
    }

    private StudentPerformance buildStudentPerformance(User student, Course course) {
        Double avgScore = scoreRepository.getAveragePercentage(course, student);
        long totalClasses = attendanceRepository.countByCourseAndStudent(course, student);
        long attended = attendanceRepository.countByCourseAndStudentAndStatus(
                course, student, AttendanceStatus.PRESENT);

        // Turn "classes attended out of total classes" into a percentage, rounded to 2
        // decimal places so the number looks clean on screen (e.g. 86.67, not 86.666666...).
        // Guard against dividing by zero when a student has no classes recorded yet.
        double attendancePct = (totalClasses > 0)
                ? Math.round((double) attended / totalClasses * 100 * 100.0) / 100.0
                : 0;

        return StudentPerformance.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .studentEmail(student.getEmail())
                .courseId(course.getId())
                .courseName(course.getName())
                .averageScorePercentage(avgScore != null ? Math.round(avgScore * 100.0) / 100.0 : 0)
                .totalClasses(totalClasses)
                .classesAttended(attended)
                .attendancePercentage(attendancePct)
                .build();
    }
}
