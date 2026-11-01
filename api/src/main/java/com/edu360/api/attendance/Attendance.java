package com.edu360.api.attendance;

import java.time.LocalDate;

import com.edu360.api.course.Course;
import com.edu360.api.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of attendance: did this student attend this course on this date, and
 * what was the result (present, absent, late). This is the database table shape,
 * not what gets sent over the API (see dto/AttendanceResponse for that).
 */
@Entity
@Table(name = "attendance",
       // A student can only have one attendance record per course per day — the
       // database itself blocks a duplicate row instead of relying on app code to catch it.
       uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "student_id", "date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;
}
