package com.edu360.api.staff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StaffAppraisalIT {
    @Autowired JdbcTemplate jdbc;

    private boolean table(String name) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='app' AND table_name=?",
                Integer.class, name);
        return count != null && count == 1;
    }

    @Test
    void appraisalTablesAndSecurityPoliciesExist() {
        assertThat(table("appraisal_cycle")).isTrue();
        assertThat(table("appraisal_submission")).isTrue();
        assertThat(table("appraisal_review")).isTrue();
        Integer secured = jdbc.queryForObject("""
            SELECT count(*) FROM pg_class
            WHERE relname IN ('appraisal_cycle','appraisal_submission','appraisal_review')
              AND relrowsecurity AND relforcerowsecurity
            """, Integer.class);
        assertThat(secured).isEqualTo(3);
    }

    @Test
    void appraisalActionsAreRegistered() {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM ref.action_catalog
            WHERE action_code IN ('STAFF_APPRAISAL_CYCLE_MANAGE','STAFF_APPRAISAL_VIEW',
              'STAFF_APPRAISAL_SELF_SUBMIT','STAFF_APPRAISAL_PEER_REVIEW',
              'STAFF_APPRAISAL_HOD_SCORE','STAFF_APPRAISAL_PRINCIPAL_REVIEW')
            """, Integer.class);
        assertThat(count).isEqualTo(6);
    }

    @Test
    void provisionalScoringWeightsAreDocumentedInDatabaseMetadata() {
        String comment = jdbc.queryForObject("""
            SELECT obj_description('app.appraisal_submission'::regclass, 'pg_class')
            """, String.class);
        assertThat(comment).contains("20% self + 50% HOD + 30% Principal");
    }
}
