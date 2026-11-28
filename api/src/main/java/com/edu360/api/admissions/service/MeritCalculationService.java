package com.edu360.api.admissions.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Deterministic ranking: qualifying exam percentage is the score; ties preserve submission order. */
@Service
public class MeritCalculationService {
    public List<Map<String,Object>> rank(List<Map<String,Object>> applications) {
        return applications.stream()
                .sorted(Comparator.<Map<String,Object>,BigDecimal>comparing(
                        row -> new BigDecimal(String.valueOf(row.get("qualifying_marks_percent"))))
                        .reversed())
                .toList();
    }
    public BigDecimal score(BigDecimal qualifyingMarksPercent) {
        if (qualifyingMarksPercent == null || qualifyingMarksPercent.signum() < 0
                || qualifyingMarksPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Qualifying marks must be between 0 and 100.");
        }
        return qualifyingMarksPercent.setScale(3, RoundingMode.HALF_UP);
    }
}
