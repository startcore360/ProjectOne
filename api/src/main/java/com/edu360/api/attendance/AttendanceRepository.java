package com.edu360.api.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edu360.api.course.Course;
import com.edu360.api.user.User;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByCourseAndDate(Course course, LocalDate date);

    List<Attendance> findByCourseAndStudent(Course course, User student);

    List<Attendance> findByStudent(User student);

    List<Attendance> findByCourse(Course course);

    Optional<Attendance> findByCourseAndStudentAndDate(Course course, User student, LocalDate date);

    long countByCourseAndStudent(Course course, User student);

    long countByCourseAndStudentAndStatus(Course course, User student, AttendanceStatus status);
}
