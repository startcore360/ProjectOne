package com.edu360.api.academic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class AttendanceCalculationIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void attendanceSchemaUsesOnlyCanonicalV40Statuses() {
        String definition = jdbc.queryForObject("""
            SELECT pg_get_constraintdef(oid) FROM pg_constraint
             WHERE conrelid='app.attendance_entry'::regclass AND conname='ck_attendance_entry_status'
            """, String.class);
        assertThat(definition).contains("PRESENT", "ABSENT", "ON_APPROVED_LEAVE", "NOT_HELD")
                .doesNotContain("LATE", "EXCUSED");
    }

    @Test
    void medicalAdjustedPercentageAndPot02EligibilityGateAreInstalled() {
        Integer view = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.views
             WHERE table_schema='app' AND table_name='v_attendance_percentage'
            """, Integer.class);
        Integer function = jdbc.queryForObject("""
            SELECT count(*) FROM pg_proc WHERE oid='app.fn_attendance_exam_eligibility(uuid,uuid,uuid)'::regprocedure
            """, Integer.class);
        Integer trigger = jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger WHERE tgrelid='app.attendance_session'::regclass
             AND tgname='trg_check_term_lock_attendance' AND NOT tgisinternal
            """, Integer.class);
        assertThat(view).isEqualTo(1);
        assertThat(function).isEqualTo(1);
        assertThat(trigger).isEqualTo(1);
    }

    @Test
    void thresholdPolicyExposesDefaultAndIsConfigurable() {
        String defaultValue = jdbc.queryForObject("""
            SELECT column_default FROM information_schema.columns
             WHERE table_schema='app' AND table_name='attendance_threshold_policy'
               AND column_name='minimum_attendance_percentage'
            """, String.class);
        assertThat(defaultValue).contains("75");
    }
}