package com.edu360.api.attendance.staffcheckin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;

/**
 * WHAT: records a DENIED_ATTEMPT audit event for a hard-blocked self-check-in (GPS accuracy worse
 * than the institution's threshold, or a position outside its geofence).
 *
 * WHY a separate bean with REQUIRES_NEW: the refusal is a thrown BusinessRuleViolationException
 * inside StaffCheckInService.submit's transaction, so an audit row written in that transaction
 * would roll back with it. ProfileService records the guardian-mobile DENIED_ATTEMPT by writing
 * the audit before throwing from a method with no transaction of its own; this is the same
 * audit-before-throw, given its own committed transaction so it survives the rollback. It is a
 * separate bean so the REQUIRES_NEW proxy actually applies (no self-invocation).
 *
 * WHAT IS RECORDED: the reason code and the numbers that decided it (accuracy vs threshold,
 * distance vs radius, whole meters) - never the raw coordinates, which are the staff member's
 * personal location. Numbers are stored as numbers, not strings, so the audit writer's
 * phone-number heuristic can never refuse a large distance.
 */
@Component
public class StaffCheckinDeniedAttemptAuditor {

    static final String ENTITY_TYPE = "USER_ACCOUNT";

    private final AuditLogService audit;

    public StaffCheckinDeniedAttemptAuditor(AuditLogService audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordHardBlock(UUID staffUserId, UUID institutionId, String reasonCode, Map<String, Object> facts) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("reason", reasonCode);
        if (facts != null) {
            values.putAll(facts);
        }
        audit.record(new AuditEntry(AuditEventType.DENIED_ATTEMPT, StaffCheckInService.SUBMIT_ACTION, ENTITY_TYPE,
                staffUserId, institutionId, null, values, 0, "P", "DENIED"));
    }
}
