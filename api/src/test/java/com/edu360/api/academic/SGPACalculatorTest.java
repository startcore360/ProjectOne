package com.edu360.api.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edu360.api.academic.calculator.CGPACalculator;
import com.edu360.api.academic.calculator.SGPACalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SGPACalculatorTest {
    private final SGPACalculator sgpa = new SGPACalculator();
    private final CGPACalculator cgpa = new CGPACalculator();

    @Test
    void calculatesCreditWeightedSemesterAverage() {
        assertThat(sgpa.calculate(List.of(
                new SGPACalculator.CourseGrade(new BigDecimal("3"), new BigDecimal("9")),
                new SGPACalculator.CourseGrade(new BigDecimal("1"), new BigDecimal("7")))))
                .isEqualByComparingTo("8.5000");
    }

    @Test
    void calculatesCreditWeightedCumulativeAverage() {
        assertThat(cgpa.calculate(List.of(
                new CGPACalculator.SemesterGrade(new BigDecimal("8"), new BigDecimal("20")),
                new CGPACalculator.SemesterGrade(new BigDecimal("9"), new BigDecimal("10")))))
                .isEqualByComparingTo("8.3333");
    }

    @Test
    void rejectsZeroCredits() {
        assertThatThrownBy(() -> sgpa.calculate(List.of(
                new SGPACalculator.CourseGrade(BigDecimal.ZERO, BigDecimal.TEN))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptySemester() {
        assertThatThrownBy(() -> cgpa.calculate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
