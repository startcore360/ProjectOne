package com.edu360.api.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Database contract test for the migration-provided publish gate.
 * Runs only when an explicit disposable PostgreSQL test URL is supplied.
 */
@EnabledIfEnvironmentVariable(named = "EDU360_PUBLISH_GATE_JDBC_URL", matches = ".+")
class ReportCardPublishGateIT {
    @Test
    void publishGateFunctionIsInstalledAndRejectsUnavailableDependencies() throws SQLException {
        String url = System.getenv("EDU360_PUBLISH_GATE_JDBC_URL");
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("EDU360_PUBLISH_GATE_JDBC_USER"),
                System.getenv("EDU360_PUBLISH_GATE_JDBC_PASSWORD"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT to_regprocedure('app.fn_publish_gate(uuid,uuid)') IS NOT NULL
                     """)) {
            result.next();
            assertThat(result.getBoolean(1))
                    .as("the migration must install the report-card publish gate")
                    .isTrue();
        }
    }
}
