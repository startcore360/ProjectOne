package com.edu360.api.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** M13 schema acceptance checks against the PostgreSQL fixture built by sql/run_all.sh. */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class StaffAppraisalIT {
    private Connection connection() throws SQLException {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (url == null || username == null || password == null) {
            throw new IllegalStateException("PostgreSQL test datasource environment is required.");
        }
        return DriverManager.getConnection(url, username, password);
    }

    private int count(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    @Test
    void appraisalTablesAndSecurityPoliciesExist() throws SQLException {
        try (Connection connection = connection()) {
            for (String table : new String[]{"appraisal_cycle", "appraisal_submission", "appraisal_review"}) {
                assertThat(count(connection,
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema='app' AND table_name=?",
                        table)).as(table + " exists").isEqualTo(1);
            }
            assertThat(count(connection, """
                    SELECT count(*) FROM pg_class c
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='app'
                      AND c.relname IN ('appraisal_cycle','appraisal_submission','appraisal_review')
                      AND c.relrowsecurity AND c.relforcerowsecurity
                    """)).isEqualTo(3);
        }
    }

    @Test
    void appraisalActionsAreRegistered() throws SQLException {
        try (Connection connection = connection()) {
            assertThat(count(connection, """
                    SELECT count(*) FROM ref.action_catalog
                    WHERE action_code IN ('STAFF_APPRAISAL_CYCLE_MANAGE','STAFF_APPRAISAL_VIEW',
                      'STAFF_APPRAISAL_SELF_SUBMIT','STAFF_APPRAISAL_PEER_REVIEW',
                      'STAFF_APPRAISAL_HOD_SCORE','STAFF_APPRAISAL_PRINCIPAL_REVIEW')
                    """)).isEqualTo(6);
        }
    }

    @Test
    void provisionalScoringWeightsAreDocumentedInDatabaseMetadata() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT obj_description('app.appraisal_submission'::regclass, 'pg_class')");
             ResultSet result = statement.executeQuery()) {
            result.next();
            assertThat(result.getString(1)).contains("20% self + 50% HOD + 30% Principal");
        }
    }
}
