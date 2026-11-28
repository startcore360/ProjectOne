package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real PostgreSQL integration test for the migration-58 GiST exclusion constraint.
 * Two independent transactions insert overlapping terms for different program years
 * in the same institution. The second insert must wait on the first transaction and
 * then fail with PostgreSQL exclusion_violation (23P01).
 */
@Testcontainers(disabledWithoutDocker = true)
class SemesterExclusionConstraintTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static UUID tenantId;
    private static UUID institutionId;
    private static UUID firstProgramYearId;
    private static UUID secondProgramYearId;
    private static UUID systemUserId;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String migration : List.of(
                    "01_schema.sql", "02_rules.sql", "03_bindings.sql", "03b_fk_indexes.sql",
                    "57_v41_academic_hierarchy_constraints.sql", "58_v41_department_program_section_schema.sql")) {
                String sql = Files.readString(Path.of("..", "sql", migration), StandardCharsets.UTF_8);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }

            tenantId = UUID.randomUUID();
            institutionId = UUID.randomUUID();
            systemUserId = UUID.randomUUID();
            UUID departmentId = UUID.randomUUID();
            UUID firstProgramId = UUID.randomUUID();
            UUID secondProgramId = UUID.randomUUID();
            firstProgramYearId = UUID.randomUUID();
            secondProgramYearId = UUID.randomUUID();

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES (?, 'UNIVERSITY_GROUP')")) {
                statement.setObject(1, tenantId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app.institution
                      (tenant_id, institution_id, institution_type, institution_code, institution_name,
                       status, under_university, created_by, updated_by)
                    VALUES (?, ?, 'COLLEGE', 'COLL-01', 'Overlap Test College', 'DRAFT', true, ?, ?)
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, institutionId);
                statement.setObject(3, systemUserId);
                statement.setObject(4, systemUserId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app.department
                      (tenant_id, department_id, institution_id, department_code, department_name, created_by, updated_by)
                    VALUES (?, ?, ?, 'CSE', 'Computer Science', ?, ?)
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, departmentId);
                statement.setObject(3, institutionId);
                statement.setObject(4, systemUserId);
                statement.setObject(5, systemUserId);
                statement.executeUpdate();
            }
            insertProgramAndYear(connection, firstProgramId, firstProgramYearId, departmentId, "BTECH1", "Year 1");
            insertProgramAndYear(connection, secondProgramId, secondProgramYearId, departmentId, "BTECH2", "Year 1");
        }
    }

    private static void insertProgramAndYear(Connection connection, UUID programId, UUID programYearId,
            UUID departmentId, String code, String yearLabel) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app.program
                  (tenant_id, program_id, institution_id, department_id, program_code, program_name,
                   duration_years, semesters_per_year, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 4, 2, ?, ?)
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, programId);
            statement.setObject(3, institutionId);
            statement.setObject(4, departmentId);
            statement.setString(5, code);
            statement.setString(6, "Test Program " + code);
            statement.setObject(7, systemUserId);
            statement.setObject(8, systemUserId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app.program_year
                  (tenant_id, program_year_id, institution_id, program_id, year_number, label, created_by, updated_by)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, programYearId);
            statement.setObject(3, institutionId);
            statement.setObject(4, programId);
            statement.setString(5, yearLabel);
            statement.setObject(6, systemUserId);
            statement.setObject(7, systemUserId);
            statement.executeUpdate();
        }
    }

    @Test
    void concurrentOverlappingTermInsertIsRejectedByInstitutionGiSTConstraint() throws Exception {
        assertThat(constraintExists()).isTrue();
        UUID firstSemesterId = UUID.randomUUID();
        UUID secondSemesterId = UUID.randomUUID();

        try (Connection first = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            first.setAutoCommit(false);
            insertSemester(first, firstSemesterId, firstProgramYearId, "2026-07-01", "2026-11-30");

            CountDownLatch secondAttempting = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> secondResult = executor.submit(() -> {
                    try (Connection second = DriverManager.getConnection(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                        second.setAutoCommit(false);
                        try (Statement statement = second.createStatement()) {
                            statement.execute("SET application_name = 'semester-overlap-second'");
                        }
                        secondAttempting.countDown();
                        try {
                            insertSemester(second, secondSemesterId, secondProgramYearId, "2026-08-01", "2026-12-15");
                            second.commit();
                            return "COMMITTED";
                        } catch (SQLException exception) {
                            second.rollback();
                            return exception.getSQLState();
                        }
                    }
                });

                assertThat(secondAttempting.await(5, TimeUnit.SECONDS)).isTrue();
                boolean waitedOnFirstTransaction = awaitSecondInsertLock();
                first.commit();

                String result = secondResult.get(10, TimeUnit.SECONDS);
                assertThat(waitedOnFirstTransaction)
                        .as("second transaction must be concurrently blocked on the uncommitted overlapping range")
                        .isTrue();
                assertThat(result).isEqualTo("23P01");
            } finally {
                if (!first.getAutoCommit()) first.rollback();
                executor.shutdownNow();
            }
        }
    }

    private static void insertSemester(Connection connection, UUID semesterId, UUID programYearId,
            String startDate, String endDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app.semester
                  (tenant_id, semester_id, institution_id, program_year_id, semester_number, label,
                   start_date, end_date, created_by, updated_by)
                VALUES (?, ?, ?, ?, 1, 'Semester 1', ?::date, ?::date, ?, ?)
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, semesterId);
            statement.setObject(3, institutionId);
            statement.setObject(4, programYearId);
            statement.setString(5, startDate);
            statement.setString(6, endDate);
            statement.setObject(7, systemUserId);
            statement.setObject(8, systemUserId);
            statement.executeUpdate();
        }
    }

    private static boolean constraintExists() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM pg_constraint WHERE conname = 'ex_semester_institution_nonoverlap'");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1) == 1;
        }
    }

    private static boolean awaitSecondInsertLock() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT count(*) FROM pg_stat_activity
                          WHERE application_name = 'semester-overlap-second'
                            AND wait_event_type = 'Lock'
                         """);
                 ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) > 0) return true;
            }
            Thread.sleep(25);
        }
        return false;
    }
}
