package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Real-Postgres contract test for support-session persistence and tenant audit RLS.
 * Uses the same already-provisioned local Postgres connection as TenantRoutingDataSourceInstitutionScopeIT.
 * It is intentionally not an H2 test: the actor-kind/support-grant checks and RLS policy are PostgreSQL behavior.
 */
@TestInstance(PER_CLASS)
class SupportSessionIT {
    private static final String POSTGRES_URL = "jdbc:postgresql://127.0.0.1:5433/edu360db";
    private static final String POSTGRES_USER = "edu360";
    private static final String POSTGRES_PASSWORD = "edu360";

    private static String readSql(String file) throws IOException {
        return Files.readString(Path.of("..", "sql", file), StandardCharsets.UTF_8);
    }

    private void ensureMigrations() throws Exception {
        try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
             Statement st = conn.createStatement()) {
            boolean baseSchemaPresent;
            try (ResultSet rs = st.executeQuery("SELECT to_regclass('app.tenant_local') IS NOT NULL")) {
                rs.next();
                baseSchemaPresent = rs.getBoolean(1);
            }
            if (!baseSchemaPresent) {
                st.execute(readSql("01_schema.sql"));
                st.execute(readSql("02_rules.sql"));
                st.execute(readSql("03_bindings.sql"));
                st.execute(readSql("03b_fk_indexes.sql"));
            }

            boolean supportActionPresent;
            try (ResultSet rs = st.executeQuery(
                    "SELECT EXISTS (SELECT 1 FROM ref.action_catalog WHERE action_code='SUPPORT_ACCESS_USE')")) {
                rs.next();
                supportActionPresent = rs.getBoolean(1);
            }
            if (!supportActionPresent) st.execute(readSql("05_ref_data.sql"));

            boolean sessionTablePresent;
            try (ResultSet rs = st.executeQuery("SELECT to_regclass('platform.support_session') IS NOT NULL")) {
                rs.next();
                sessionTablePresent = rs.getBoolean(1);
            }
            if (!sessionTablePresent) st.execute(readSql("40_v41_support_session.sql"));

            boolean readableColumnPresent;
            try (ResultSet rs = st.executeQuery(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ref' AND table_name='action_catalog' AND column_name='support_readable')")) {
                rs.next();
                readableColumnPresent = rs.getBoolean(1);
            }
            if (!readableColumnPresent) st.execute(readSql("41_v41_support_readable.sql"));
        }
    }

    @Test
    void platformSessionStorageIsRoleSeparatedAndTenantSupportAuditRemainsRlsScoped() throws Exception {
        ensureMigrations();
        UUID tenantId = UUID.randomUUID();
        UUID platformUserId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Instant now = Instant.now();

        try {
            UUID planId;
            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT plan_id FROM platform.plan LIMIT 1")) {
                if (!rs.next()) throw new IllegalStateException("A provisioned platform plan is required for SupportSessionIT.");
                planId = rs.getObject(1, UUID.class);
            }

            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.platform_user (platform_user_id,email,full_name,platform_role,identity_subject,created_by,updated_by) VALUES (?,?,?,'SUPPORT_MANAGER',?,?,?)")) {
                    ps.setObject(1, platformUserId);
                    ps.setString(2, "support-session-" + suffix + "@example.test");
                    ps.setString(3, "Support Session IT");
                    ps.setString(4, "support-session-it-" + suffix);
                    ps.setObject(5, platformUserId);
                    ps.setObject(6, platformUserId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.tenant (tenant_id,tenant_name,tenant_type,plan_id,state,isolation_mode,subdomain,created_by,updated_by) VALUES (?,?, 'STANDALONE_COLLEGE',?,'ACTIVE','SHARED',?,?,?)")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "Support Session IT " + suffix);
                    ps.setObject(3, planId);
                    ps.setString(4, "it-" + suffix);
                    ps.setObject(5, platformUserId);
                    ps.setObject(6, platformUserId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO app.tenant_local (tenant_id,tenant_type) VALUES (?,'STANDALONE_COLLEGE')")) {
                    ps.setObject(1, tenantId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.support_ticket (support_ticket_id,tenant_id,ticket_number,raised_by_user_id,raised_by_name,subject,description,priority,status,created_by,updated_by) VALUES (?,?,?,?,?,?,?,'MEDIUM','NEW',?,?)")) {
                    ps.setObject(1, ticketId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "SUP-IT-" + suffix);
                    ps.setObject(4, UUID.randomUUID());
                    ps.setString(5, "Tenant requester");
                    ps.setString(6, "Support session IT ticket");
                    ps.setString(7, "Seed ticket for support session RLS test.");
                    ps.setObject(8, platformUserId);
                    ps.setObject(9, platformUserId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.support_access_grant (support_access_grant_id,ticket_id,tenant_id,granted_by_user_id,expires_at,created_by,updated_by) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setObject(1, grantId);
                    ps.setObject(2, ticketId);
                    ps.setObject(3, tenantId);
                    ps.setObject(4, UUID.randomUUID());
                    ps.setObject(5, now.plusSeconds(20 * 3600));
                    ps.setObject(6, platformUserId);
                    ps.setObject(7, platformUserId);
                    ps.executeUpdate();
                }
            }

            // Session storage is writable by edu_platform, not by the ordinary tenant application role.
            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
                 Statement st = conn.createStatement()) {
                st.execute("SET ROLE edu_platform");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.support_session (support_session_id,grant_id,platform_user_id,issued_at,expires_at) VALUES (?,?,?,?,?)")) {
                    ps.setObject(1, sessionId);
                    ps.setObject(2, grantId);
                    ps.setObject(3, platformUserId);
                    ps.setObject(4, now);
                    ps.setObject(5, now.plusSeconds(1800));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.support_session (support_session_id,grant_id,platform_user_id,issued_at,expires_at) VALUES (?,?,?,?,?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, grantId);
                    ps.setObject(3, platformUserId);
                    ps.setObject(4, now);
                    ps.setObject(5, now.plusSeconds(7200));
                    assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
                }
            }

            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
                 Statement st = conn.createStatement()) {
                st.execute("SET ROLE edu_app");
                try (PreparedStatement set = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                    set.setString(1, tenantId.toString());
                    set.execute();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO app.audit_outbox (tenant_id,audit_event_id,actor_kind,actor_user_id,support_grant_id,operation,action_code,entity_type,entity_id,sensitivity_class,outcome,correlation_id,payload_hash,created_by,updated_by,new_values) VALUES (?,?,'SUPPORT_UNDER_GRANT',?,?,'VIEW_SENSITIVE','SUPPORT_ACCESS_USE','SUPPORT_SESSION',?,'P','SUCCESS','support-session-it',repeat('a',64),?,?,?::jsonb)")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, auditId);
                    ps.setObject(3, platformUserId);
                    ps.setObject(4, grantId);
                    ps.setObject(5, sessionId);
                    ps.setObject(6, platformUserId);
                    ps.setObject(7, platformUserId);
                    ps.setString(8, "{\"ticket_id\":\"" + ticketId + "\",\"request_path\":\"/api/tenants/" + tenantId + "/classes\"}");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM app.audit_outbox WHERE tenant_id = ?")) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                }
                try (PreparedStatement set = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                    set.setString(1, UUID.randomUUID().toString());
                    set.execute();
                }
                try (Statement query = conn.createStatement();
                     ResultSet rs = query.executeQuery("SELECT count(*) FROM app.audit_outbox")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
            }

            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
                 Statement st = conn.createStatement()) {
                st.execute("SET ROLE edu_app");
                assertThatThrownBy(() -> {
                    try (ResultSet ignored = st.executeQuery("SELECT support_session_id FROM platform.support_session")) {
                        // Query must fail at the platform schema/table privilege boundary.
                    }
                }).isInstanceOf(SQLException.class);
            }
        } finally {
            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
                 PreparedStatement audit = conn.prepareStatement("DELETE FROM app.audit_outbox WHERE tenant_id = ?");
                 PreparedStatement tenantLocal = conn.prepareStatement("DELETE FROM app.tenant_local WHERE tenant_id = ?");
                 PreparedStatement session = conn.prepareStatement("DELETE FROM platform.support_session WHERE grant_id = ?");
                 PreparedStatement grant = conn.prepareStatement("DELETE FROM platform.support_access_grant WHERE support_access_grant_id = ?");
                 PreparedStatement ticket = conn.prepareStatement("DELETE FROM platform.support_ticket WHERE support_ticket_id = ?");
                 PreparedStatement tenant = conn.prepareStatement("DELETE FROM platform.tenant WHERE tenant_id = ?");
                 PreparedStatement user = conn.prepareStatement("DELETE FROM platform.platform_user WHERE platform_user_id = ?")) {
                audit.setObject(1, tenantId); audit.executeUpdate();
                tenantLocal.setObject(1, tenantId); tenantLocal.executeUpdate();
                session.setObject(1, grantId); session.executeUpdate();
                grant.setObject(1, grantId); grant.executeUpdate();
                ticket.setObject(1, ticketId); ticket.executeUpdate();
                tenant.setObject(1, tenantId); tenant.executeUpdate();
                user.setObject(1, platformUserId); user.executeUpdate();
            }
        }
    }
}
