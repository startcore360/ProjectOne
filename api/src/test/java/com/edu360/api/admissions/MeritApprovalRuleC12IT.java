package com.edu360.api.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executes the actual fn_validate_merit_approval definition extracted from migration 64
 * against PostgreSQL 16, exercising the trigger boundary rather than checking SQL text only.
 */
@Testcontainers
class MeritApprovalRuleC12IT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LIST = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PRINCIPAL = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DELEGATE = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID NON_PRINCIPAL = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PRINCIPAL_ROLE = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ROLE = UUID.fromString("40000000-0000-0000-0000-000000000002");

    @BeforeEach
    void installMigrationFunctionAndMinimalSchema() throws Exception {
        Path migrationPath = Files.exists(Path.of("sql", "64_v41_merit_list_and_seat_allocation.sql"))
                ? Path.of("sql", "64_v41_merit_list_and_seat_allocation.sql")
                : Path.of("..", "sql", "64_v41_merit_list_and_seat_allocation.sql");
        String migration = Files.readString(migrationPath);
        String marker = "CREATE OR REPLACE FUNCTION app.fn_validate_merit_approval()";
        int start = migration.indexOf(marker);
        int end = migration.indexOf("$$;", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String function = migration.substring(start, end + 3);

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS app CASCADE");
            statement.execute("CREATE SCHEMA app");
            statement.execute("""
                CREATE TABLE app.role_definition(
                  tenant_id uuid NOT NULL, role_definition_id uuid NOT NULL, role_code text NOT NULL, status text NOT NULL,
                  PRIMARY KEY(tenant_id,role_definition_id))
                """);
            statement.execute("""
                CREATE TABLE app.user_role_assignment(
                  tenant_id uuid NOT NULL, user_account_id uuid NOT NULL, role_definition_id uuid NOT NULL,
                  status text NOT NULL, effective_from timestamptz NOT NULL, effective_to timestamptz)
                """);
            statement.execute("""
                CREATE TABLE app.delegation(
                  tenant_id uuid NOT NULL, delegate_user_id uuid NOT NULL, status text NOT NULL,
                  starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL, process_code text)
                """);
            statement.execute("""
                CREATE TABLE app.merit_list(
                  tenant_id uuid NOT NULL, id uuid NOT NULL, status text NOT NULL,
                  approved_by_user_id uuid, approved_at timestamptz,
                  PRIMARY KEY(tenant_id,id))
                """);
            statement.execute(function);
            statement.execute("""
                CREATE TRIGGER trg_merit_approval_guard
                BEFORE UPDATE OF status,approved_by_user_id ON app.merit_list
                FOR EACH ROW EXECUTE FUNCTION app.fn_validate_merit_approval()
                """);
            statement.execute("INSERT INTO app.role_definition VALUES ('" + TENANT + "','" + PRINCIPAL_ROLE + "','PRINCIPAL_COLLEGE','ACTIVE')");
            statement.execute("INSERT INTO app.role_definition VALUES ('" + TENANT + "','" + ADMIN_ROLE + "','COLLEGE_ADMIN','ACTIVE')");
            statement.execute("INSERT INTO app.user_role_assignment VALUES ('" + TENANT + "','" + PRINCIPAL + "','" + PRINCIPAL_ROLE + "','ACTIVE',now()-interval '1 day',null)");
            statement.execute("INSERT INTO app.user_role_assignment VALUES ('" + TENANT + "','" + DELEGATE + "','" + PRINCIPAL_ROLE + "','ACTIVE',now()-interval '1 day',null)");
            statement.execute("INSERT INTO app.user_role_assignment VALUES ('" + TENANT + "','" + NON_PRINCIPAL + "','" + ADMIN_ROLE + "','ACTIVE',now()-interval '1 day',null)");
            statement.execute("INSERT INTO app.merit_list VALUES ('" + TENANT + "','" + LIST + "','PROVISIONAL',null,null)");
        }
    }

    @Test
    void substantivePrincipalCanApproveIndividually() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('app.user_id','" + PRINCIPAL + "',false)");
            statement.execute("UPDATE app.merit_list SET status='APPROVED',approved_by_user_id='" + PRINCIPAL + "' WHERE tenant_id='" + TENANT + "' AND id='" + LIST + "'");
            try (var result = statement.executeQuery("SELECT status,approved_at FROM app.merit_list WHERE tenant_id='" + TENANT + "' AND id='" + LIST + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("APPROVED");
                assertThat(result.getTimestamp("approved_at")).isNotNull();
            }
        }
    }

    @Test
    void delegatedPrincipalCannotApprove() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO app.delegation VALUES ('" + TENANT + "','" + DELEGATE + "','ACTIVE',now()-interval '1 minute',now()+interval '1 day','MERIT_LIST_APPROVE')");
            statement.execute("SELECT set_config('app.user_id','" + DELEGATE + "',false)");
            SQLException error = assertThrows(SQLException.class, () -> statement.execute(
                    "UPDATE app.merit_list SET status='APPROVED',approved_by_user_id='" + DELEGATE + "' WHERE tenant_id='" + TENANT + "' AND id='" + LIST + "'"));
            assertThat(error.getSQLState()).isEqualTo("42501");
            assertThat(error.getMessage()).contains("MERIT_APPROVAL_NON_DELEGABLE");
        }
    }

    @Test
    void actingForAnotherRoleCannotApprove() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('app.user_id','" + PRINCIPAL + "',false)");
            statement.execute("SELECT set_config('app.acting_for_role','VICE_CHANCELLOR',false)");
            SQLException error = assertThrows(SQLException.class, () -> statement.execute(
                    "UPDATE app.merit_list SET status='APPROVED',approved_by_user_id='" + PRINCIPAL + "' WHERE tenant_id='" + TENANT + "' AND id='" + LIST + "'"));
            assertThat(error.getSQLState()).isEqualTo("42501");
            assertThat(error.getMessage()).contains("MERIT_APPROVAL_NON_DELEGABLE");
        }
    }

    @Test
    void nonPrincipalCannotApprove() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('app.user_id','" + NON_PRINCIPAL + "',false)");
            SQLException error = assertThrows(SQLException.class, () -> statement.execute(
                    "UPDATE app.merit_list SET status='APPROVED',approved_by_user_id='" + NON_PRINCIPAL + "' WHERE tenant_id='" + TENANT + "' AND id='" + LIST + "'"));
            assertThat(error.getSQLState()).isEqualTo("42501");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
