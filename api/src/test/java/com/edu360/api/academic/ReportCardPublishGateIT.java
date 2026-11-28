package com.edu360.api.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Database contract smoke test for the migration-provided publish gate.
 * Runs only when an explicit disposable PostgreSQL test URL is supplied.
 */
@EnabledIfEnvironmentVariable(named = "EDU360_PUBLISH_GATE_JDBC_URL", matches = ".+")
class ReportCardPublishGateIT {
    @Test
    void publishGateFunctionIsInstalled() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                    System.getenv("EDU360_PUBLISH_GATE_JDBC_URL"),
                    System.getenv("EDU360_PUBLISH_GATE_JDBC_USER"),
                    System.getenv("EDU360_PUBLISH_GATE_JDBC_PASSWORD"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                         WHERE n.nspname = 'app' AND p.proname = 'fn_publish_gate')
                       AND to_regprocedure('app.fn_fee_balance(uuid,uuid)') IS NOT NULL
                       AND position('PUBLISH_GATE_BLOCKED_FEE_BALANCE_UNAVAILABLE' IN
                           pg_get_functiondef('app.fn_publish_gate(uuid,uuid)'::regprocedure)) > 0
                     """)) {
            result.next();
            assertThat(result.getBoolean(1))
                    .as("the publish gate must use the T8 fee-balance contract and retain fail-closed handling")
                    .isTrue();
        }
    }
}
