package com.edu360.api.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.edu360.api.admissions.service.MeritCalculationService;

class MeritListCalculationIT {
    private final MeritCalculationService calculation = new MeritCalculationService();

    @Test
    void ranksHighestQualifyingMarksFirstAndPreservesEqualScoreOrder() {
        List<Map<String,Object>> rows = List.of(
                Map.of("applicationId","a","qualifying_marks_percent",new BigDecimal("72.50")),
                Map.of("applicationId","b","qualifying_marks_percent",new BigDecimal("91.00")),
                Map.of("applicationId","c","qualifying_marks_percent",new BigDecimal("91.00")));
        var ranked = calculation.rank(rows);
        assertThat(ranked).extracting(row -> row.get("applicationId")).containsExactly("b","c","a");
        assertThat(calculation.score(new BigDecimal("91"))).isEqualByComparingTo("91.000");
    }
}
