package com.edu360.api.audit;

import java.util.List;

/**
 * Writes tenant audit events to app.audit_outbox (D4). An interface so AuditLogService can be
 * unit-tested without a database (the H2 test profile has no app.* tables).
 */
public interface TenantAuditStore {

    void append(TenantAuditEvent event);

    default void appendAll(List<TenantAuditEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (TenantAuditEvent event : events) {
            append(event);
        }
    }
}
