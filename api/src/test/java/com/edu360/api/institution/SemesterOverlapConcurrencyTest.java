package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL integration proof that the institution-wide GiST exclusion constraint
 * rejects overlapping semesters inserted concurrently under different program years.
 * The service-level overlap check is deliberately bypassed: both writes use independent
 * JDBC transactions and target different program_year rows in the same institution.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SemesterOverlapConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("edu360")
                    .withUsername("edu360")
                    .withPassword("edu360");

    private UUID tenantId;
    private UUID institutionId;
    private UUID firstProgramYearId;
    private UUID secondProgramYearId;
    private UUID actorId;

    @BeforeAll
    void applySchemaAndSeedTwoProgramYears() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String file : List.of(
                    "01_schema.sql",
                    "02_rules.sql",
                    "03_bindings.sql",
                    "03b_fk_indexes.sql",
                    "57_v41_academic_hierarchy_constraints.sql",
                    "58_v41_department_program_section_schema.sql")) {
                statement.execute(Files.readString(Path.of("..", "sql", file), StandardCharsets.UTF_8));
            }
        }

        tenantId = UUID.randomUUID();
        institutionId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        firstProgramYearId = UUID.randomUUID();
        secondProgramYearId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID firstProgramId = UUID.randomUUID();
        UUID secondProgramId = UUID.randomUUID();

        try (Connection connection = connection()) {
            insert(connection, "INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES (?, 'STANDALONE_COLLEGE')",
                    tenantId);
            insert(connection, """
                    INSERT INTO app.institution
                      (tenant_id, institution_id, institution_type, institution_code, institution_name, created_by, updated_by)
                    VALUES (?, ?, 'COLLEGE', 'T2TEST', 'T2 overlap test college', ?, ?)
                    """, tenantId, institutionId, actorId, actorId);
            insert(connection, """
                    INSERT INTO app.department
                      (tenant_id, department_id, institution_id, department_code, department_name, created_by, updated_by)
                    VALUES (?, ?, ?, 'T2DEPT', 'T2 overlap test department', ?, ?)
                    """, tenantId, departmentId, institutionId, actorId, actorId);
            insert(connection, """
                    INSERT INTO app.program
                      (tenant_id, program_id, institution_id, department_id, program_code, program_name,
                       duration_years, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'T2PRA', 'Program A', 4, ?, ?),
                           (?, ?, ?, ?, 'T2PRB', 'Program B', 4, ?, ?)
                    """,
                    tenantId, firstProgramId, institutionId, departmentId, actorId, actorId,
                    tenantId, secondProgramId, institutionId, departmentId, actorId, actorId);
            insert(connection, """
                    INSERT INTO app.program_year
                      (tenant_id, program_year_id, institution_id, program_id, year_number, label, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 1, 'Year 1 A', ?, ?),
                           (?, ?, ?, ?, 1, 'Year 1 B', ?, ?)
                    """,
                    tenantId, firstProgramYearId, institutionId, firstProgramId, actorId, actorId,
                    tenantId, secondProgramYearId, institutionId, secondProgramId, actorId, actorId);
        }
    }

    @Test
    void concurrentOverlappingSemesterInsertsAreRejectedByGiSTConstraint() throws Exception {
        LocalDate start = LocalDate.parse("2027-01-10");
        LocalDate end = LocalDate.parse("2027-05-20");
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondAttemptingInsert = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> {
                try (Connection connection = connection()) {
                    connection.setAutoCommit(false);
                    insertSemester(connection, firstProgramYearId, start, end);
                    firstInserted.countDown();
                    if (!allowFirstCommit.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release first semester transaction");
                    }
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                if (!firstInserted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First semester transaction did not reach its insert");
                }
                try (Connection connection = connection()) {
                    connection.setAutoCommit(false);
                    secondAttemptingInsert.countDown();
                    try {
                        insertSemester(connection, secondProgramYearId, start, end);
                        connection.commit();
                        return false;
                    } catch (SQLException exception) {
                        connection.rollback();
                        if ("23P01".equals(exception.getSQLState())) {
                            return true;
                        }
                        throw exception;
                    }
                }
            });

            assertThat(secondAttemptingInsert.await(10, TimeUnit.SECONDS)).isTrue();
            // Give PostgreSQL time to register the second transaction's conflicting insert
            // while the first transaction still holds its uncommitted exclusion entry.
            Thread.sleep(250);
            allowFirstCommit.countDown();
            first.get(10, TimeUnit.SECONDS);

            assertThat(second.get(10, TimeUnit.SECONDS))
                    .as("the competing insert must fail with SQLSTATE 23P01 from the GiST exclusion constraint")
                    .isTrue();
        }

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT count(*) FROM app.semester
                      WHERE tenant_id = ? AND institution_id = ?
                        AND start_date = ? AND end_date = ?
                     """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, institutionId);
            statement.setObject(3, start);
            statement.setObject(4, end);
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void insertSemester(Connection connection, UUID programYearId, LocalDate start, LocalDate end)
            throws SQLException {
        insert(connection, """
                INSERT INTO app.semester
                  (tenant_id, semester_id, institution_id, program_year_id, semester_number, label,
                   start_date, end_date, created_by, updated_by)
                VALUES (?, ?, ?, ?, 1, 'Concurrent term', ?, ?, ?, ?)
                """,
                tenantId, UUID.randomUUID(), institutionId, programYearId, start, end, actorId, actorId);
    }

    private static void insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }
}
