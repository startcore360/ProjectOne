package com.edu360.api.audit;
import java.util.Map;
import java.util.UUID;
/** Structured tenant audit payload; values must contain identifiers/state, never credentials, join links, file bytes, names or contact details. */
public record AuditEntry(AuditEventType eventType, String actionCode, String entityType, UUID entityId,
 UUID institutionId, Map<String,Object> oldValues, Map<String,Object> newValues,
 Integer recordCount, String sensitivityClass, String outcome) { }
