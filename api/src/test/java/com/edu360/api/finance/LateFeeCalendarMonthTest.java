package com.edu360.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LateFeeCalendarMonthTest {

    @Test
    void accruesAtCalendarMonthBoundaryRatherThanAfterThirtyDays() {
        LocalDate dueDate = LocalDate.of(2026, 4, 15);

        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                dueDate, 5, LocalDate.of(2026, 4, 20))).isZero();
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                dueDate, 5, LocalDate.of(2026, 4, 30))).isZero();
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                dueDate, 5, LocalDate.of(2026, 5, 1))).isEqualTo(1);
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                dueDate, 5, LocalDate.of(2026, 5, 31))).isEqualTo(1);
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                dueDate, 5, LocalDate.of(2026, 6, 1))).isEqualTo(2);
    }

    @Test
    void countsYearBoundaryAndLeapYearMonths() {
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                LocalDate.of(2026, 12, 31), 0, LocalDate.of(2027, 1, 1))).isEqualTo(1);
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                LocalDate.of(2024, 1, 31), 0, LocalDate.of(2024, 2, 1))).isEqualTo(1);
        assertThat(LateFeeCalculationService.calendarMonthsAfterGrace(
                LocalDate.of(2026, 3, 31), 0, LocalDate.of(2026, 4, 1))).isEqualTo(1);
    }

    @Test
    void rejectsMissingDatesAndNegativeGracePeriod() {
        assertThatThrownBy(() -> LateFeeCalculationService.calendarMonthsAfterGrace(
                null, 0, LocalDate.of(2026, 5, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LateFeeCalculationService.calendarMonthsAfterGrace(
                LocalDate.of(2026, 4, 1), -1, LocalDate.of(2026, 5, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
