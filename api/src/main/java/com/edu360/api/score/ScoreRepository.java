package com.edu360.api.score;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.edu360.api.course.Course;
import com.edu360.api.user.User;

/**
 * Lets the rest of the app read and search Score rows in the database. The plain
 * findByX methods are filled in automatically by Spring Data JPA just from their
 * names — only the two average-percentage queries needed to be written by hand.
 */
public interface ScoreRepository extends JpaRepository<Score, Long> {

    List<Score> findByCourse(Course course);

    List<Score> findByCourseAndStudent(Course course, User student);

    List<Score> findByStudent(User student);

    // Works out one student's average score (as a percentage) across every
    // assessment in a course, so we don't have to fetch every row and do the math in Java.
    @Query("SELECT AVG(s.score / s.maxScore * 100) FROM Score s WHERE s.course = :course AND s.student = :student")
    Double getAveragePercentage(@Param("course") Course course, @Param("student") User student);

    // Same idea, but for the whole course's average instead of one student's.
    @Query("SELECT AVG(s.score / s.maxScore * 100) FROM Score s WHERE s.course = :course")
    Double getCourseAveragePercentage(@Param("course") Course course);
}
