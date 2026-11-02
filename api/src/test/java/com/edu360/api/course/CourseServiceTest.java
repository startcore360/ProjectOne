package com.edu360.api.course;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.user.Role;
import com.edu360.api.user.User;

class CourseServiceTest {

    @Test
    void permitsStudentWhoIsEnrolled() {
        User student = user(10L, Role.STUDENT);
        Course course = courseWith(student);

        CourseService service = new CourseService(null, null);

        assertDoesNotThrow(() -> service.verifyStudentEnrolled(course, student));
    }

    @Test
    void rejectsStudentWhoIsNotEnrolled() {
        User enrolledStudent = user(10L, Role.STUDENT);
        User otherStudent = user(11L, Role.STUDENT);
        Course course = courseWith(enrolledStudent);

        CourseService service = new CourseService(null, null);

        assertThrows(UnauthorizedException.class,
                () -> service.verifyStudentEnrolled(course, otherStudent));
    }

    @Test
    void rejectsNonStudentAccountEvenIfAddedToMembershipSet() {
        User teacher = user(12L, Role.TEACHER);
        Course course = courseWith(teacher);

        CourseService service = new CourseService(null, null);

        assertThrows(UnauthorizedException.class,
                () -> service.verifyStudentEnrolled(course, teacher));
    }

    private static Course courseWith(User... users) {
        return Course.builder()
                .id(1L)
                .name("Mathematics")
                .code("MATH-01")
                .teacher(user(1L, Role.TEACHER))
                .students(new HashSet<>(Set.of(users)))
                .build();
    }

    private static User user(Long id, Role role) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email("user" + id + "@example.test")
                .password("not-a-real-password")
                .role(role)
                .build();
    }
}
