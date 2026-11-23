package com.edu360.api.student;

import static org.junit.jupiter.api.Assertions.*;

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

/** Behavioural tests execute the production migration, not a test copy of its trigger logic. */
@Testcontainers
class StudentServiceIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void applyProductionMigration() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS app CASCADE");
            s.execute("DROP ROLE IF EXISTS edu_app");
            s.execute("CREATE ROLE edu_app");
            s.execute("CREATE SCHEMA app");
            s.execute("""
                CREATE TABLE app.student_profile (
                    tenant_id uuid NOT NULL,
                    student_profile_id uuid PRIMARY KEY,
                    student_code text NOT NULL,
                    institution_id uuid,
                    status text NOT NULL
                )
                """);
            s.execute("""
                CREATE TABLE app.student_guardian_link (
                    tenant_id uuid NOT NULL,
                    student_guardian_link_id uuid PRIMARY KEY,
                    student_profile_id uuid NOT NULL,
                    relationship text NOT NULL,
                    status text NOT NULL,
                    is_primary boolean NOT NULL DEFAULT false,
                    created_at timestamptz NOT NULL DEFAULT now()
                )
                """);
            Path migration = Path.of("sql/61_v41_student_profile_and_guardians.sql");
            if (!Files.exists(migration)) migration = Path.of("..", "sql", "61_v41_student_profile_and_guardians.sql");
            assertTrue(Files.exists(migration), "production migration must be available from the Maven working directory");
            s.execute(Files.readString(migration));
        }
    }

    @Test
    void permitsThreeActiveGuardiansRejectsFourthAndAllowsBlockedFourth() throws Exception {
        try (Connection c = connection()) {
            insertGuardian(c, true, "ACTIVE");
            insertGuardian(c, false, "ACTIVE");
            insertGuardian(c, false, "ACTIVE");
            SQLException rejected = assertThrows(SQLException.class, () -> insertGuardian(c, false, "ACTIVE"));
            assertTrue(rejected.getMessage().contains("GUARDIAN_LIMIT_REACHED"), rejected.getMessage());
            insertGuardian(c, false, "BLOCKED");
        }
    }

    @Test
    void deferredConstraintRejectsNonPrimaryGuardianAtTransactionCommit() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            insertGuardian(c, false, "ACTIVE");
            SQLException rejected = assertThrows(SQLException.class, c::commit);
            assertTrue(rejected.getMessage().contains("PRIMARY_GUARDIAN_REQUIRED"), rejected.getMessage());
            c.rollback();
        }
    }

    @Test
    void deferredConstraintAllowsAtomicPrimaryGuardianHandoff() throws Exception {
        try (Connection c = connection()) {
            UUID first = insertGuardian(c, true, "ACTIVE");
            UUID second = insertGuardian(c, false, "ACTIVE");
            c.setAutoCommit(false);
            try (var clear = c.prepareStatement("UPDATE app.student_guardian_link SET is_primary=false WHERE student_guardian_link_id=?");
                 var promote = c.prepareStatement("UPDATE app.student_guardian_link SET is_primary=true WHERE student_guardian_link_id=?")) {
                clear.setObject(1, first); clear.executeUpdate();
                promote.setObject(1, second); promote.executeUpdate();
            }
            assertDoesNotThrow(c::commit);
        }
    }

    @Test
    void directStudentProfileDeleteIsRejectedByProductionTrigger() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO app.student_profile(tenant_id,student_profile_id,student_code,status) VALUES ('" +
                    TENANT + "','" + STUDENT + "','S-001','ACTIVE')");
            SQLException rejected = assertThrows(SQLException.class,
                    () -> s.executeUpdate("DELETE FROM app.student_profile WHERE student_profile_id='" + STUDENT + "'"));
            assertTrue(rejected.getMessage().contains("STUDENT_HARD_DELETE_PROHIBITED"), rejected.getMessage());
        }
    }

    @Test
    void eduAppHasNoDeletePrivilegeOnStudentProfile() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT has_table_privilege('edu_app','app.student_profile','DELETE')")) {
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1), "edu_app must not have DELETE on app.student_profile");
        }
    }

    @Test
    void cursorOrderingAndStudentStatusColumnsAreSupported() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("CREATE TEMP TABLE student_profile_cursor(student_code text NOT NULL, student_profile_id uuid NOT NULL, status text NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED','WITHDRAWN','ALUMNI')), PRIMARY KEY(student_code,student_profile_id))");
            s.execute("INSERT INTO student_profile_cursor VALUES ('S-001','00000000-0000-0000-0000-000000000001','ACTIVE'),('S-002','00000000-0000-0000-0000-000000000002','SUSPENDED')");
            try (var rs = s.executeQuery("SELECT student_code,status FROM student_profile_cursor WHERE (student_code,student_profile_id)>('S-001','00000000-0000-0000-0000-000000000001') ORDER BY student_code LIMIT 1")) {
                assertTrue(rs.next()); assertEquals("S-002", rs.getString(1)); assertEquals("SUSPENDED", rs.getString(2));
            }
            try (var rs = s.executeQuery("SELECT count(*) FROM student_profile_cursor")) {
                assertTrue(rs.next()); assertEquals(2, rs.getInt(1));
            }
        }
    }

    private static UUID insertGuardian(Connection c, boolean primary, String status) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var ps = c.prepareStatement("""
                INSERT INTO app.student_guardian_link
                    (tenant_id,student_guardian_link_id,student_profile_id,relationship,status,is_primary)
                VALUES (?,?,?,?,?,?)
                """)) {
            ps.setObject(1, TENANT); ps.setObject(2, id); ps.setObject(3, STUDENT);
            ps.setString(4, "FATHER"); ps.setString(5, status); ps.setBoolean(6, primary);
            ps.executeUpdate();
        }
        return id;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
