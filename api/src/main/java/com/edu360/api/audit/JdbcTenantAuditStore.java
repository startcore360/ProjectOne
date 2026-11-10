package com.edu360.api.audit;

import java.sql.Timestamp;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * WHAT: inserts one row into app.audit_outbox with plain JDBC.
 *
 * WHY JDBC and not a JPA entity: new_values is jsonb, and mapping jsonb would need a JSON type
 * mapping this project does not have; an explicit CAST in SQL is simpler and is checked by the
 * database. The insert runs on the @Primary DataSource (TenantRoutingDataSource, user edu_app),
 * so the connection carries app.tenant_id / app.institution_scope / app.institution_ids and the
 * RLS WITH CHECK policies decide whether the row may be written. Inside a JPA transaction
 * JpaTransactionManager exposes the same connection to JdbcTemplate, so the audit row commits or
 * rolls back with the business change (D4 PTD-11).
 */
@Component
public class JdbcTenantAuditStore implements TenantAuditStore {

    private static final String INSERT = """
            INSERT INTO app.audit_outbox (tenant_id, audit_event_id, institution_id, occurred_at, actor_kind,
                actor_user_id, active_role_code, operation, entity_type, outcome, new_values, correlation_id,
                payload_hash, created_by, updated_by)
            VALUES (?, ?, ?, ?, 'USER', ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcTenantAuditStore(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void append(TenantAuditEvent e) {
        jdbcTemplate.update(INSERT,
                e.tenantId(), e.auditEventId(), e.institutionId(), Timestamp.from(e.occurredAt()),
                e.actorUserId(), e.activeRoleCode(), e.operation(), e.entityType(), e.outcome(),
                e.newValuesJson(), e.correlationId(), e.payloadHash(), e.actorUserId(), e.actorUserId());
    }
}
