package com.edu360.api.course;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.course.dto.CourseResponse;
import com.edu360.api.course.dto.CreateCourseRequest;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request, User teacher) {
        Course course = Course.builder()
                .name(request.getName())
                .code(request.getCode())
                .teacher(teacher)
                .build();

        course = courseRepository.save(course);
        return toResponse(course);
    }

    public List<CourseResponse> getMyCourses(User user) {
        List<Course> courses;
        if (user.getRole() == Role.TEACHER) {
            courses = courseRepository.findByTeacher(user);
        } else {
            courses = courseRepository.findByStudentsContaining(user);
        }
        return courses.stream().map(this::toResponse).toList();
    }

    public CourseResponse getCourseById(Long courseId, User user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        // Teachers can only view their own courses, students only enrolled ones
        if (user.getRole() == Role.TEACHER && !course.getTeacher().getId().equals(user.getId())) {
            throw new UnauthorizedException("You do not own this course");
        }
        if (user.getRole() == Role.STUDENT && !course.getStudents().contains(user)) {
            throw new UnauthorizedException("You are not enrolled in this course");
        }

        return toResponse(course);
    }

    @Transactional
    public CourseResponse enrollStudent(Long courseId, Long studentId, User teacher) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new UnauthorizedException("You do not own this course");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        if (student.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("User is not a student");
        }

        course.getStudents().add(student);
        course = courseRepository.save(course);
        return toResponse(course);
    }

    // Helper: verify teacher owns the course (used by other services)
    public Course verifyTeacherOwnsCourse(Long courseId, User teacher) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new UnauthorizedException("You do not own this course");
        }
        return course;
    }

    // Helper: verify student is enrolled in the course
    public Course verifyStudentEnrolled(Long courseId, User student) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!course.getStudents().contains(student)) {
            throw new UnauthorizedException("You are not enrolled in this course");
        }
        return course;
    }

    private CourseResponse toResponse(Course course) {
        var teacherInfo = CourseResponse.TeacherInfo.builder()
                .id(course.getTeacher().getId())
                .name(course.getTeacher().getName())
                .email(course.getTeacher().getEmail())
                .build();

        var students = course.getStudents().stream()
                .map(s -> CourseResponse.StudentInfo.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .email(s.getEmail())
                        .build())
                .toList();

        return CourseResponse.builder()
                .id(course.getId())
                .name(course.getName())
                .code(course.getCode())
                .teacher(teacherInfo)
                .students(students)
                .build();
    }
}
