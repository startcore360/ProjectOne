package com.edu360.api.score;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.edu360.api.course.Course;
import com.edu360.api.user.User;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    List<Score> findByCourse(Course course);

    List<Score> findByCourseAndStudent(Course course, User student);

    List<Score> findByStudent(User student);

    @Query("SELECT AVG(s.score / s.maxScore * 100) FROM Score s WHERE s.course = :course AND s.student = :student")
    Double getAveragePercentage(@Param("course") Course course, @Param("student") User student);

    @Query("SELECT AVG(s.score / s.maxScore * 100) FROM Score s WHERE s.course = :course")
    Double getCourseAveragePercentage(@Param("course") Course course);
}
