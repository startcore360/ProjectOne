package com.edu360.api.audit;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Writes the full structured event to the tenant audit outbox in the business transaction. */
@Component
public class JdbcTenantAuditStore implements TenantAuditStore {
 private static final String INSERT = """
 INSERT INTO app.audit_outbox (tenant_id,audit_event_id,institution_id,occurred_at,actor_kind,
 actor_user_id,active_role_code,operation,action_code,entity_type,entity_id,record_count,
 sensitivity_class,outcome,old_values,new_values,correlation_id,payload_hash,created_by,updated_by)
 VALUES (?, ?, ?, ?, 'USER', ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
 """;
 private final JdbcTemplate jdbc;
 public JdbcTenantAuditStore(DataSource ds){this.jdbc=new JdbcTemplate(ds);}
 @Override public void append(TenantAuditEvent e){
  jdbc.update(INSERT,e.tenantId(),e.auditEventId(),e.institutionId(),Timestamp.from(e.occurredAt()),
   e.actorUserId(),e.activeRoleCode(),e.operation(),e.actionCode(),e.entityType(),e.entityId(),
   e.recordCount(),e.sensitivityClass(),e.outcome(),e.oldValuesJson(),e.newValuesJson(),
   e.correlationId(),e.payloadHash(),e.actorUserId(),e.actorUserId());
 }
}
