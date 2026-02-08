package com.edu360.api.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edu360.api.user.User;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacher(User teacher);
    List<Course> findByStudentsContaining(User student);
}
