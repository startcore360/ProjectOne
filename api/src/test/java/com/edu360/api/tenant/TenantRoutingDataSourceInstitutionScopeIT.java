package com.edu360.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Proves, against a REAL Postgres connection with RLS actually enforced, that
 * TenantRoutingDataSource.applyTenantGucs(...) wires institution scope (OD-03) correctly:
 *   (a) a connection scoped to one institution sees only that institution's rows,
 *   (b) empty institution scope is fail-closed (zero institution-scoped rows),
 *   (c) a stale app.institution_ids value from a PREVIOUS borrow of the same pooled connection
 *       never leaks into a later borrow that has a different (or no) institution scope.
 *
 * Uses sql/04_tests.sql's own DB-06 scenario (app.department, two institutions under one
 * tenant) as the reference fixture - same table, same shape of seed data - rather than
 * inventing a new one, and reuses sql/01-03 against the already-running local dev Postgres
 * (docker-compose at the repo root) exactly the way TenantIssuerAuthenticationManagerResolverIT
 * does, for the same reason given there: this sandbox blocks Testcontainers' own Docker-socket
 * bootstrap, while the already-running compose stack works fine. CI (unrestricted Docker access)
 * can switch this to Testcontainers if preferred.
 *
 * Connects as the edu360 superuser and then SET ROLE edu_app, mirroring exactly how
 * sql/04_tests.sql itself runs its RLS assertions (SET ROLE edu_app after a superuser seed) -
 * edu_app is the non-bypassrls role sql/03_bindings.sql's policies are written for, and a
 * superuser session bypasses RLS entirely regardless of FORCE ROW LEVEL SECURITY.
 *
 * Does NOT modify any file under sql/ - only ever reads the committed bundle to apply it if it
 * is not already applied, the same as the existing IT test in this module does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantRoutingDataSourceInstitutionScopeIT {

    private static final String POSTGRES_URL = "jdbc:postgresql://127.0.0.1:5433/edu360db";
    private static final String POSTGRES_USER = "edu360";
    private static final String POSTGRES_PASSWORD = "edu360";

    private static final HikariDataSource UNUSED_SHARED = new HikariDataSource();
    private static final TenantSecretsResolver UNUSED_RESOLVER =
            ref -> new TenantDatabaseCredentials("jdbc:postgresql://127.0.0.1:1/unused-" + ref, "u", "p");

    private final TenantRoutingDataSource routing =
            new TenantRoutingDataSource(UNUSED_SHARED, UNUSED_RESOLVER, 50, Duration.ofHours(1));

    @BeforeAll
    void ensureSchemaApplied() throws Exception {
        try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
            try (Statement check = conn.createStatement()) {
                ResultSet rs = check.executeQuery("SELECT to_regclass('app.tenant_local') IS NOT NULL");
                rs.next();
                if (rs.getBoolean(1)) {
                    return; // already applied by docker/postgres/provision-edu-app.ps1 or an earlier test
                }
            }
            for (String file : new String[] {"01_schema.sql", "02_rules.sql", "03_bindings.sql", "03b_fk_indexes.sql"}) {
                try (Statement st = conn.createStatement()) {
                    st.execute(readSqlBundleFile(file));
                }
            }
        }
    }

    @AfterEach
    void clearTenantContext() {
        // Same discipline TenantContextClearingFilter enforces in production - a ThreadLocal
        // left set after one test method would leak into the next one on the same JVM thread.
        TenantContext.clear();
    }

    private static String readSqlBundleFile(String name) throws IOException {
        // Surefire's working directory is the module root (api/); the committed bundle at
        // repo-root sql/ is one directory up - same path construction as the existing IT test.
        return Files.readString(Path.of("..", "sql", name), StandardCharsets.UTF_8);
    }

    /** A connection to the real database, as the edu_app application role (never the edu360
     *  superuser) - RLS only applies to a non-bypassrls role. */
    private Connection appConnection() throws Exception {
        Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
        try (Statement st = conn.createStatement()) {
            st.execute("SET ROLE edu_app");
        }
        return conn;
    }

    /** Seeds one fresh tenant with two institutions, one department under each - the exact
     *  shape sql/04_tests.sql's own DB-06 (PTD-I1) test uses - as the edu360 superuser so RLS
     *  never gets in the way of the seed itself. Returns the tenant id and the id of the
     *  "home" institution (the one tests will scope access to). */
    private record Fixture(UUID tenantId, UUID homeInstitutionId, UUID otherInstitutionId) {
    }

    private Fixture seedTenantWithTwoInstitutions() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID homeInstitutionId = UUID.randomUUID();
        UUID otherInstitutionId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES (?, 'STANDALONE_COLLEGE')")) {
                ps.setObject(1, tenantId);
                ps.executeUpdate();
            }
            insertInstitution(conn, tenantId, homeInstitutionId, "H" + shortCode(), actor);
            insertInstitution(conn, tenantId, otherInstitutionId, "O" + shortCode(), actor);
            insertDepartment(conn, tenantId, homeInstitutionId, "HD" + shortCode(), actor);
            insertDepartment(conn, tenantId, otherInstitutionId, "OD" + shortCode(), actor);
        }
        return new Fixture(tenantId, homeInstitutionId, otherInstitutionId);
    }

    private static void insertInstitution(Connection conn, UUID tenantId, UUID institutionId, String code, UUID actor)
            throws Exception {
        try (var ps = conn.prepareStatement(
                "INSERT INTO app.institution (tenant_id, institution_id, institution_type, institution_code, "
                        + "institution_name, created_by, updated_by) VALUES (?, ?, 'COLLEGE', ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, institutionId);
            ps.setString(3, code);
            ps.setString(4, "Institution " + code);
            ps.setObject(5, actor);
            ps.setObject(6, actor);
            ps.executeUpdate();
        }
    }

    private static void insertDepartment(Connection conn, UUID tenantId, UUID institutionId, String code, UUID actor)
            throws Exception {
        try (var ps = conn.prepareStatement(
                "INSERT INTO app.department (tenant_id, department_id, institution_id, department_code, "
                        + "department_name, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, institutionId);
            ps.setString(4, code);
            ps.setString(5, "Department " + code);
            ps.setObject(6, actor);
            ps.setObject(7, actor);
            ps.executeUpdate();
        }
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private static int countDepartments(Connection conn, UUID tenantId) throws Exception {
        try (var ps = conn.prepareStatement("SELECT count(*) FROM app.department WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---------------------------------------------------------------------------- the tests

    @Test
    void connectionScopedToOneInstitutionSeesOnlyThatInstitutionsRows() throws Exception {
        Fixture fixture = seedTenantWithTwoInstitutions();

        try (Connection conn = appConnection()) {
            TenantContext.set(fixture.tenantId(), IsolationMode.SHARED, null, Set.of(fixture.homeInstitutionId()));
            routing.applyTenantGucs(conn);

            assertThat(countDepartments(conn, fixture.tenantId()))
                    .as("p_institution must restrict to the one department under the scoped institution")
                    .isEqualTo(1);
        }
    }

    @Test
    void emptyInstitutionScopeIsFailClosedEvenWithAValidTenant() throws Exception {
        Fixture fixture = seedTenantWithTwoInstitutions();

        try (Connection conn = appConnection()) {
            TenantContext.set(fixture.tenantId(), IsolationMode.SHARED, null, Set.of());
            routing.applyTenantGucs(conn);

            assertThat(countDepartments(conn, fixture.tenantId()))
                    .as("empty institutionIds (TenantContext's fail-closed default) must deny every "
                            + "institution-scoped row even though the tenant itself is valid and has rows")
                    .isZero();
        }
    }

    @Test
    void institutionIdsFromAPreviousBorrowNeverLeaksIntoALaterBorrowOnTheSamePooledConnection() throws Exception {
        Fixture fixture = seedTenantWithTwoInstitutions();

        try (Connection conn = appConnection()) {
            // Borrow 1: a request scoped to the home institution - exactly like a real Hikari
            // checkout for one HTTP request.
            TenantContext.set(fixture.tenantId(), IsolationMode.SHARED, null, Set.of(fixture.homeInstitutionId()));
            routing.applyTenantGucs(conn);
            assertThat(countDepartments(conn, fixture.tenantId())).isEqualTo(1);

            // Borrow 2: the SAME physical connection, re-borrowed for a different request that
            // has no institution access at all - simulating Hikari handing this connection back
            // to the pool and a different request borrowing it next. If app.institution_ids were
            // only RESET-or-skipped on some path instead of always explicitly SET, the previous
            // request's institution id could still be sitting on this session.
            TenantContext.clear();
            TenantContext.set(fixture.tenantId(), IsolationMode.SHARED, null, Set.of());
            routing.applyTenantGucs(conn);

            assertThat(countDepartments(conn, fixture.tenantId()))
                    .as("the previous borrow's institution id must not still be visible to this one")
                    .isZero();
        }
    }

    @Test
    void noTenantContextResetsInstitutionIdsTooNotJustTenantId() throws Exception {
        Fixture fixture = seedTenantWithTwoInstitutions();

        try (Connection conn = appConnection()) {
            TenantContext.set(fixture.tenantId(), IsolationMode.SHARED, null, Set.of(fixture.homeInstitutionId()));
            routing.applyTenantGucs(conn);
            assertThat(countDepartments(conn, fixture.tenantId())).isEqualTo(1);

            // No tenant context at all (Platform Admin, or pre-authentication) - the RESET path.
            TenantContext.clear();
            routing.applyTenantGucs(conn);

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT current_setting('app.institution_ids', true)")) {
                rs.next();
                assertThat(rs.getString(1))
                        .as("the RESET path must actually clear app.institution_ids, not just app.tenant_id")
                        .isNullOrEmpty();
            }
            assertThat(countDepartments(conn, fixture.tenantId()))
                    .as("with no tenant context, p_tenant alone already yields zero rows")
                    .isZero();
        }
    }
}
