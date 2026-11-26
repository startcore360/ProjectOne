package com.edu360.api.governance.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileService;
import com.edu360.api.files.FileUploadResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Service
public class DisciplinaryIncidentService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final FileService files;
    private final AuditLogService audit;

    public DisciplinaryIncidentService(JdbcTemplate jdbc, PermissionEvaluatorService permissions,
            FileService files, AuditLogService audit) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.files = files;
        this.audit = audit;
    }

    public record Incident(UUID incidentId, UUID institutionId, UUID studentId, String studentName,
            LocalDate incidentDate, String severity, String description, String actionTaken,
            String status, OffsetDateTime createdAt) { }
    public record Evidence(UUID evidenceId, UUID fileId, UUID uploadedByUserId, OffsetDateTime createdAt) { }
    public record Action(UUID actionId, String actionType, String actionNotes,
            OffsetDateTime actionAt, UUID performedByUserId, UUID noticeFileId,
            UUID minutesFileId, String penalty) { }

    @Transactional
    public Incident create(TenantPrincipal principal, UUID studentId, LocalDate incidentDate,
            String severity, String description) {
        UUID institutionId = studentInstitution(principal.tenantId(), studentId);
        authorize(principal, institutionId, "DISCIPLINARY_INCIDENT_MANAGE");
        if (incidentDate == null || !List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity)
                || description == null || description.isBlank() || description.length() > 5000) {
            throw new BusinessRuleViolationException("DISCIPLINARY_INPUT_INVALID",
                    "Incident date, severity and a description up to 5,000 characters are required.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.disciplinary_incident
                  (tenant_id,disciplinary_incident_id,institution_id,student_id,reported_by_user_id,
                   incident_date,severity,description,status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,'RECORDED',?,?)
                """, principal.tenantId(), id, institutionId, studentId, principal.userAccountId(),
                incidentDate, severity, description.trim(), principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "DISCIPLINARY_INCIDENT_MANAGE",
                "DISCIPLINARY_INCIDENT", id, institutionId, null,
                Map.of("severity", severity, "incidentDate", incidentDate.toString()), 1, "P", "SUCCESS"));
        return get(principal, id);
    }

    @Transactional
    public List<Incident> list(TenantPrincipal principal, UUID institutionId) {
        authorize(principal, institutionId, "DISCIPLINARY_INCIDENT_VIEW");
        List<Incident> rows = jdbc.query("""
                SELECT di.disciplinary_incident_id,di.institution_id,di.student_id,sp.full_name,
                       di.incident_date,di.severity,di.description,di.action_taken,di.status,di.created_at
                  FROM app.disciplinary_incident di
                  JOIN app.student_profile sp ON sp.tenant_id=di.tenant_id
                   AND sp.student_profile_id=di.student_id
                 WHERE di.tenant_id=? AND di.institution_id=?
                 ORDER BY di.incident_date DESC,di.created_at DESC
                """, this::mapIncident, principal.tenantId(), institutionId);
        rows.forEach(row -> audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW,
                "DISCIPLINARY_INCIDENT_VIEW", "DISCIPLINARY_INCIDENT", row.incidentId(),
                institutionId, null, Map.of("view", "LIST"), 1, "P", "SUCCESS")));
        return rows;
    }

    @Transactional
    public Incident get(TenantPrincipal principal, UUID incidentId) {
        Incident row = jdbc.query("""
                SELECT di.disciplinary_incident_id,di.institution_id,di.student_id,sp.full_name,
                       di.incident_date,di.severity,di.description,di.action_taken,di.status,di.created_at
                  FROM app.disciplinary_incident di
                  JOIN app.student_profile sp ON sp.tenant_id=di.tenant_id
                   AND sp.student_profile_id=di.student_id
                 WHERE di.tenant_id=? AND di.disciplinary_incident_id=?
                """, rs -> rs.next() ? mapIncident(rs, 0) : null, principal.tenantId(), incidentId);
        if (row == null) throw new ResourceNotFoundException("Disciplinary incident not found.");
        authorize(principal, row.institutionId(), "DISCIPLINARY_INCIDENT_VIEW");
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "DISCIPLINARY_INCIDENT_VIEW",
                "DISCIPLINARY_INCIDENT", incidentId, row.institutionId(), null,
                Map.of("view", "DETAIL"), 1, "P", "SUCCESS"));
        return row;
    }

    @Transactional
    public Incident update(TenantPrincipal principal, UUID incidentId, String actionTaken,
            String status, String notes) {
        Incident current = get(principal, incidentId);
        authorize(principal, current.institutionId(), "DISCIPLINARY_INCIDENT_MANAGE");
        if (!List.of("RECORDED", "UNDER_REVIEW", "CLOSED").contains(status)
                || (actionTaken != null && actionTaken.length() > 5000)) {
            throw new BusinessRuleViolationException("DISCIPLINARY_UPDATE_INVALID", "Invalid status or action details.");
        }
        jdbc.update("""
                UPDATE app.disciplinary_incident SET action_taken=?,status=?,updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND disciplinary_incident_id=?
                """, actionTaken, status, principal.userAccountId(), principal.tenantId(), incidentId);
        appendAction(principal, current, "FOLLOW_UP",
                notes == null || notes.isBlank() ? "Incident updated." : notes, null, null, null);
        audit.record(new AuditEntry(AuditEventType.EDIT, "DISCIPLINARY_INCIDENT_MANAGE",
                "DISCIPLINARY_INCIDENT", incidentId, current.institutionId(),
                Map.of("status", current.status()), Map.of("status", status), 1, "P", "SUCCESS"));
        return get(principal, incidentId);
    }

    @Transactional
    public FileUploadResponse uploadEvidence(TenantPrincipal principal, UUID incidentId, MultipartFile file) {
        Incident incident = get(principal, incidentId);
        authorize(principal, incident.institutionId(), "DISCIPLINARY_INCIDENT_MANAGE");
        FileUploadResponse uploaded = files.upload(principal, file, "DISCIPLINARY_EVIDENCE",
                incident.institutionId(), "DISCIPLINARY_INCIDENT", incidentId);
        jdbc.update("""
                INSERT INTO app.disciplinary_evidence
                  (tenant_id,disciplinary_evidence_id,disciplinary_incident_id,file_id,uploaded_by_user_id,created_by)
                VALUES (?,?,?,?,?,?)
                """, principal.tenantId(), UUID.randomUUID(), incidentId, uploaded.fileId(),
                principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "DISCIPLINARY_INCIDENT_MANAGE",
                "DISCIPLINARY_INCIDENT", incidentId, incident.institutionId(), null,
                Map.of("evidenceFileId", uploaded.fileId().toString()), 1, "P", "SUCCESS"));
        return uploaded;
    }

    @Transactional
    public List<Evidence> evidence(TenantPrincipal principal, UUID incidentId) {
        Incident incident = get(principal, incidentId);
        authorize(principal, incident.institutionId(), "DISCIPLINARY_INCIDENT_VIEW");
        List<Evidence> result = jdbc.query("""
                SELECT disciplinary_evidence_id,file_id,uploaded_by_user_id,created_at
                  FROM app.disciplinary_evidence
                 WHERE tenant_id=? AND disciplinary_incident_id=? ORDER BY created_at
                """, (rs, n) -> new Evidence(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, OffsetDateTime.class)),
                principal.tenantId(), incidentId);
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "DISCIPLINARY_INCIDENT_VIEW",
                "DISCIPLINARY_EVIDENCE", null, incident.institutionId(), null,
                Map.of("recordCount", result.size()), result.size(), "H", "SUCCESS"));
        return result;
    }

    @Transactional
    public List<Action> actions(TenantPrincipal principal, UUID incidentId) {
        Incident incident = get(principal, incidentId);
        authorize(principal, incident.institutionId(), "DISCIPLINARY_INCIDENT_VIEW");
        List<Action> result = jdbc.query("""
                SELECT disciplinary_action_id,action_type,action_notes,action_at,performed_by_user_id,
                       notice_file_id,minutes_file_id,penalty
                  FROM app.disciplinary_action
                 WHERE tenant_id=? AND disciplinary_incident_id=?
                 ORDER BY action_at,created_at
                """, (rs, n) -> new Action(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getObject(4, OffsetDateTime.class), rs.getObject(5, UUID.class),
                rs.getObject(6, UUID.class), rs.getObject(7, UUID.class), rs.getString(8)),
                principal.tenantId(), incidentId);
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "DISCIPLINARY_INCIDENT_VIEW",
                "DISCIPLINARY_ACTION", null, incident.institutionId(), null,
                Map.of("incidentId", incidentId.toString(), "recordCount", result.size()),
                result.size(), "H", "SUCCESS"));
        return result;
    }

    @Transactional
    public void action(TenantPrincipal principal, UUID incidentId, String type,
            String notes, UUID noticeFileId, UUID minutesFileId, String penalty) {
        Incident incident = get(principal, incidentId);
        authorize(principal, incident.institutionId(), "DISCIPLINARY_INCIDENT_MANAGE");
        if (!List.of("HEARING_NOTICE", "MINUTES_RECORDED", "PENALTY_ISSUED", "FOLLOW_UP", "CLOSED").contains(type)
                || notes == null || notes.isBlank() || notes.length() > 5000
                || (penalty != null && penalty.length() > 2000)) {
            throw new BusinessRuleViolationException("DISCIPLINARY_ACTION_INVALID", "Invalid hearing, minutes or penalty details.");
        }
        appendAction(principal, incident, type, notes, noticeFileId, minutesFileId, penalty);
    }

    private void appendAction(TenantPrincipal principal, Incident incident, String type, String notes,
            UUID noticeFileId, UUID minutesFileId, String penalty) {
        jdbc.update("""
                INSERT INTO app.disciplinary_action
                  (tenant_id,disciplinary_action_id,disciplinary_incident_id,action_type,action_notes,
                   performed_by_user_id,notice_file_id,minutes_file_id,penalty,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, principal.tenantId(), UUID.randomUUID(), incident.incidentId(), type, notes.trim(),
                principal.userAccountId(), noticeFileId, minutesFileId, penalty, principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.EDIT, "DISCIPLINARY_INCIDENT_MANAGE",
                "DISCIPLINARY_INCIDENT", incident.incidentId(), incident.institutionId(), null,
                Map.of("actionType", type), 1, "P", "SUCCESS"));
    }

    private UUID studentInstitution(UUID tenantId, UUID studentId) {
        return jdbc.query("""
                SELECT institution_id FROM app.student_profile
                 WHERE tenant_id=? AND student_profile_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, studentId);
    }

    private void authorize(TenantPrincipal principal, UUID institutionId, String action) {
        if (institutionId == null) throw new ResourceNotFoundException("Institution not found.");
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        permissions.authorize(caller, action, scope, RbacTarget.NONE);
    }

    private Incident mapIncident(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Incident(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, LocalDate.class),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getObject(10, OffsetDateTime.class));
    }
}
