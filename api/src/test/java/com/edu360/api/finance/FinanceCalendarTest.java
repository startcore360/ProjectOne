package com.edu360.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinanceCalendarTest {
    @Test
    void usesAprilThroughMarchFinancialYears() {
        assertThat(FinanceCalendar.financialYearStartYear(LocalDate.of(2025, 3, 31))).isEqualTo((short) 2024);
        assertThat(FinanceCalendar.financialYearStartYear(LocalDate.of(2025, 4, 1))).isEqualTo((short) 2025);
        assertThat(FinanceCalendar.financialYearStartYear(LocalDate.of(2026, 3, 31))).isEqualTo((short) 2025);
        assertThat(FinanceCalendar.financialYearStartYear(LocalDate.of(2026, 4, 1))).isEqualTo((short) 2026);
    }

    @Test
    void requiresAnExplicitPostingDate() {
        assertThatThrownBy(() -> FinanceCalendar.financialYearStartYear(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
