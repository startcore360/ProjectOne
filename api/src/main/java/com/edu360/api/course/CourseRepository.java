package com.edu360.api.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edu360.api.user.User;

/** Lets the rest of the app look up courses by teacher or by an enrolled student. */
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacher(User teacher);
    List<Course> findByStudentsContaining(User student);
}
