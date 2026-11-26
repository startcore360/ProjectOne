package com.edu360.api.finance;

import java.time.LocalDate;

/** Shared April–March financial-year convention for all T8 ledger postings. */
final class FinanceCalendar {
    private FinanceCalendar() {}

    static short financialYearStartYear(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("A financial date is required.");
        return (short) (date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1);
    }
}
