package com.edu360.api.attendance;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.attendance.dto.AttendanceEntry;
import com.edu360.api.attendance.dto.AttendanceResponse;
import com.edu360.api.attendance.dto.MarkAttendanceRequest;
import com.edu360.api.course.Course;
import com.edu360.api.course.CourseService;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final CourseService courseService;
    private final UserRepository userRepository;

    @Transactional
    public List<AttendanceResponse> markAttendance(MarkAttendanceRequest request, User teacher) {
        Course course = courseService.verifyTeacherOwnsCourse(request.getCourseId(), teacher);

        List<Attendance> savedRecords = new ArrayList<>();

        for (AttendanceEntry entry : request.getRecords()) {
            User student = userRepository.findById(entry.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Student not found: " + entry.getStudentId()));

            // Upsert: update if exists, insert if not
            Attendance attendance = attendanceRepository
                    .findByCourseAndStudentAndDate(course, student, request.getDate())
                    .orElse(Attendance.builder()
                            .course(course)
                            .student(student)
                            .date(request.getDate())
                            .build());

            attendance.setStatus(entry.getStatus());
            savedRecords.add(attendanceRepository.save(attendance));
        }

        return savedRecords.stream().map(this::toResponse).toList();
    }

    public List<AttendanceResponse> getAttendanceByCourse(Long courseId, User teacher) {
        Course course = courseService.verifyTeacherOwnsCourse(courseId, teacher);
        return attendanceRepository.findByCourse(course).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AttendanceResponse> getMyAttendance(User student) {
        return attendanceRepository.findByStudent(student).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AttendanceResponse> getMyAttendanceForCourse(Long courseId, User student) {
        Course course = courseService.verifyStudentEnrolled(courseId, student);
        return attendanceRepository.findByCourseAndStudent(course, student).stream()
                .map(this::toResponse)
                .toList();
    }

    private AttendanceResponse toResponse(Attendance a) {
        return AttendanceResponse.builder()
                .id(a.getId())
                .courseId(a.getCourse().getId())
                .courseName(a.getCourse().getName())
                .studentId(a.getStudent().getId())
                .studentName(a.getStudent().getName())
                .date(a.getDate())
                .status(a.getStatus())
                .build();
    }
}
