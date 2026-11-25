package com.edu360.api.academic.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Calculates cumulative GPA weighted by each completed semester's total credits. */
public final class CGPACalculator {
    private static final int SCALE = 4;

    public BigDecimal calculate(List<SemesterGrade> semesters) {
        Objects.requireNonNull(semesters, "Semester grades are required.");
        if (semesters.isEmpty()) {
            throw new IllegalArgumentException("At least one semester is required to calculate CGPA.");
        }
        BigDecimal weightedPoints = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (SemesterGrade semester : semesters) {
            Objects.requireNonNull(semester, "Semester grade entries cannot be null.");
            if (semester.sgpa() == null || semester.sgpa().signum() < 0) {
                throw new IllegalArgumentException("SGPA cannot be negative or null.");
            }
            if (semester.totalCredits() == null || semester.totalCredits().signum() <= 0) {
                throw new IllegalArgumentException("Semester credits must be greater than zero.");
            }
            weightedPoints = weightedPoints.add(semester.sgpa().multiply(semester.totalCredits()));
            totalCredits = totalCredits.add(semester.totalCredits());
        }
        return weightedPoints.divide(totalCredits, SCALE, RoundingMode.HALF_UP);
    }

    public record SemesterGrade(BigDecimal sgpa, BigDecimal totalCredits) {
    }
}
