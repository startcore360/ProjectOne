package com.edu360.api.academic.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Calculates a semester grade point average using credit-weighted course grade points.
 * Values are kept as BigDecimal throughout to avoid binary floating-point drift.
 */
public final class SGPACalculator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public BigDecimal calculate(List<CourseGrade> courses) {
        Objects.requireNonNull(courses, "Courses are required.");
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("At least one course is required to calculate SGPA.");
        }

        BigDecimal weightedPoints = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (CourseGrade course : courses) {
            Objects.requireNonNull(course, "Course grade entries cannot be null.");
            if (course.credits() == null || course.credits().signum() <= 0) {
                throw new IllegalArgumentException("Course credits must be greater than zero.");
            }
            if (course.gradePoint() == null || course.gradePoint().signum() < 0) {
                throw new IllegalArgumentException("Grade point cannot be negative or null.");
            }
            weightedPoints = weightedPoints.add(course.credits().multiply(course.gradePoint()));
            totalCredits = totalCredits.add(course.credits());
        }

        if (totalCredits.signum() <= 0) {
            throw new IllegalArgumentException("Total semester credits must be greater than zero.");
        }
        return weightedPoints.divide(totalCredits, SCALE, ROUNDING);
    }

    public record CourseGrade(BigDecimal credits, BigDecimal gradePoint) {
    }
}
