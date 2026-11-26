package com.edu360.api.governance.service;

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
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.files.FileService;
import com.edu360.api.files.FileUploadResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Service
public class GrievanceService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final FileService files;
    private final AuditLogService audit;

    public GrievanceService(JdbcTemplate jdbc, PermissionEvaluatorService permissions,
            FileService files, AuditLogService audit) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.files = files;
        this.audit = audit;
    }

    public record SubmissionReceipt(String caseNumber, String status, OffsetDateTime submittedAt) { }
    public record CaseView(UUID grievanceCaseId, String caseNumber, UUID institutionId,
            String category, String details, UUID accusedUserId, boolean anonymousSubmission,
            String status, UUID assignedToUserId, OffsetDateTime nextHearingAt,
            String resolutionNotes, OffsetDateTime submittedAt) { }
    public record Evidence(UUID grievanceEvidenceId, UUID fileId, UUID uploadedByUserId,
            OffsetDateTime createdAt) { }
    public record CaseAction(UUID actionId, String actionType, String actionNotes,
            OffsetDateTime actionAt, UUID performedByUserId, UUID noticeFileId,
            UUID minutesFileId, String penalty) { }
    public record Hearing(UUID hearingId, OffsetDateTime hearingAt, String hearingNotes,
            UUID noticeFileId, UUID minutesFileId, String minutesNotes, String status,
            OffsetDateTime updatedAt) { }

    @Transactional
    public SubmissionReceipt submit(TenantPrincipal principal, UUID institutionId, String category,
            String details, UUID accusedUserId, boolean anonymous) {
        authorize(principal, institutionId, "GRIEVANCE_CASE_SUBMIT", RbacTarget.NONE);
        if (!List.of("HARASSMENT", "DISCRIMINATION", "ACADEMIC_MISCONDUCT", "GENERAL").contains(category)
                || details == null || details.isBlank() || details.length() > 10000) {
            throw new BusinessRuleViolationException("GRIEVANCE_INPUT_INVALID",
                    "Select a valid grievance category and provide details up to 10,000 characters.");
        }
        String caseNumber = "GRV-" + java.time.Year.now() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.grievance_case
                  (tenant_id,grievance_case_id,institution_id,complainant_user_id,accused_user_id,
                   category,case_number,details,anonymous_submission,status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,?,'SUBMITTED',?,?)
                """, principal.tenantId(), id, institutionId,
                anonymous ? null : principal.userAccountId(), accusedUserId, category,
                caseNumber, details.trim(), anonymous, principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "GRIEVANCE_CASE_SUBMIT",
                "GRIEVANCE_CASE", id, institutionId, null,
                Map.of("category", category, "anonymous", anonymous), 1, "H", "SUCCESS"));
        // The complainant receives a receipt only. No case ID or detail endpoint is granted.
        return new SubmissionReceipt(caseNumber, "SUBMITTED", OffsetDateTime.now());
    }

    @Transactional
    public List<CaseView> list(TenantPrincipal principal, UUID institutionId) {
        if (!can(principal, institutionId, "GRIEVANCE_CASE_VIEW")) return List.of();
        List<CaseView> rows = jdbc.query("""
                SELECT grievance_case_id,case_number,institution_id,category,details,accused_user_id,
                       anonymous_submission,status,assigned_to_user_id,next_hearing_at,resolution_notes,submitted_at
                  FROM app.grievance_case
                 WHERE tenant_id=? AND institution_id=?
                 ORDER BY submitted_at DESC
                """, this::caseRow, principal.tenantId(), institutionId);
        rows.forEach(row -> audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW,
                "GRIEVANCE_CASE_VIEW", "GRIEVANCE_CASE", row.grievanceCaseId(),
                institutionId, null, Map.of("view", "LIST"), 1, "H", "SUCCESS")));
        return rows;
    }

    @Transactional
    public CaseView get(TenantPrincipal principal, UUID caseId) {
        Map<String, Object> scopeRow = jdbc.query("""
                SELECT institution_id FROM app.grievance_case
                 WHERE tenant_id=? AND grievance_case_id=?
                """, rs -> rs.next() ? Map.of("institutionId", rs.getObject(1, UUID.class)) : null,
                principal.tenantId(), caseId);
        if (scopeRow == null) throw new ResourceNotFoundException("Grievance case not found.");
        UUID institutionId = (UUID) scopeRow.get("institutionId");
        if (!can(principal, institutionId, "GRIEVANCE_CASE_VIEW")) {
            // PO-T05 masks case existence for every non-authorized reader, including accused/HOD.
            throw new ResourceNotFoundException("Grievance case not found.");
        }
        CaseView row = jdbc.query("""
                SELECT grievance_case_id,case_number,institution_id,category,details,accused_user_id,
                       anonymous_submission,status,assigned_to_user_id,next_hearing_at,resolution_notes,submitted_at
                  FROM app.grievance_case
                 WHERE tenant_id=? AND grievance_case_id=?
                """, rs -> rs.next() ? caseRow(rs, 0) : null, principal.tenantId(), caseId);
        if (row == null) throw new ResourceNotFoundException("Grievance case not found.");
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "GRIEVANCE_CASE_VIEW",
                "GRIEVANCE_CASE", caseId, institutionId, null, Map.of("view", "DETAIL"), 1, "H", "SUCCESS"));
        return row;
    }

    @Transactional
    public CaseView assign(TenantPrincipal principal, UUID caseId, UUID investigatorUserId, String notes) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_CASE_ASSIGN", RbacTarget.NONE);
        if (investigatorUserId == null) throw new BusinessRuleViolationException("INVESTIGATOR_REQUIRED", "An investigator is required.");
        Integer exists = jdbc.query("""
                SELECT count(*) FROM app.user_account WHERE tenant_id=? AND user_account_id=?
                """, rs -> rs.next() ? rs.getInt(1) : 0, principal.tenantId(), investigatorUserId);
        if (exists == 0) throw new ResourceNotFoundException("Investigator not found.");
        if (investigatorUserId.equals(current.accusedUserId())) {
            throw new BusinessRuleViolationException("INVESTIGATOR_CONFLICT", "The accused party cannot be assigned to investigate this case.");
        }
        Integer authorizedInvestigator = jdbc.query("""
                SELECT count(*) FROM app.user_active_role active
                JOIN app.user_role_assignment ura ON ura.tenant_id=active.tenant_id
                 AND ura.user_account_id=active.user_account_id
                 AND ura.user_role_assignment_id=active.user_role_assignment_id
                JOIN app.role_definition rd ON rd.tenant_id=ura.tenant_id
                 AND rd.role_definition_id=ura.role_definition_id
                WHERE active.tenant_id=? AND active.user_account_id=?
                  AND ura.scope_type='INSTITUTION' AND ura.institution_id=?
                  AND ura.status='ACTIVE' AND ura.effective_from<=now()
                  AND (ura.effective_to IS NULL OR ura.effective_to>now())
                  AND rd.status='ACTIVE' AND rd.role_origin='STANDARD' AND rd.is_sensitive_role=true
                  AND rd.base_standard_role=rd.role_code
                  AND rd.role_code IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')
                """, rs -> rs.next() ? rs.getInt(1) : 0,
                principal.tenantId(), investigatorUserId, current.institutionId());
        if (authorizedInvestigator == 0) {
            throw new BusinessRuleViolationException("INVESTIGATOR_NOT_AUTHORIZED",
                    "The investigator must hold an active institution-scoped ICC or Principal role.");
        }
        Boolean immediateHod = jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1
                    FROM app.staff_profile accused_sp
                    JOIN app.staff_assignment accused_sa ON accused_sa.tenant_id=accused_sp.tenant_id
                     AND accused_sa.staff_profile_id=accused_sp.staff_profile_id
                    JOIN app.department d ON d.tenant_id=accused_sa.tenant_id
                     AND d.institution_id=accused_sa.institution_id
                     AND d.department_id=accused_sa.department_id
                    JOIN app.staff_assignment hod_sa ON hod_sa.tenant_id=d.tenant_id
                     AND hod_sa.staff_assignment_id=d.hod_staff_assignment_id
                    JOIN app.staff_profile hod_sp ON hod_sp.tenant_id=hod_sa.tenant_id
                     AND hod_sp.staff_profile_id=hod_sa.staff_profile_id
                   WHERE accused_sp.tenant_id=? AND accused_sp.user_account_id=?
                     AND hod_sp.user_account_id=?
                     AND accused_sa.institution_id=? AND accused_sa.status='ACTIVE'
                     AND hod_sa.status='ACTIVE'
                     AND accused_sa.effective_from<=current_date
                     AND (accused_sa.effective_to IS NULL OR accused_sa.effective_to>=current_date)
                     AND hod_sa.effective_from<=current_date
                     AND (hod_sa.effective_to IS NULL OR hod_sa.effective_to>=current_date)
                )
                """, Boolean.class, principal.tenantId(), current.accusedUserId(),
                investigatorUserId, current.institutionId());
        if (Boolean.TRUE.equals(immediateHod)) {
            throw new BusinessRuleViolationException("INVESTIGATOR_CONFLICT",
                    "The accused party's immediate Head of Department cannot investigate this case.");
        }
        jdbc.update("""
                UPDATE app.grievance_case SET assigned_to_user_id=?,status='UNDER_INVESTIGATION',
                       updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND grievance_case_id=?
                """, investigatorUserId, principal.userAccountId(), principal.tenantId(), caseId);
        appendAction(principal, current, "ASSIGNED",
                notes == null || notes.isBlank() ? "Case assigned to investigator." : notes,
                null, null, null, OffsetDateTime.now());
        return get(principal, caseId);
    }

    @Transactional
    public CaseView scheduleHearing(TenantPrincipal principal, UUID caseId,
            OffsetDateTime hearingAt, String notes, UUID noticeFileId) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_HEARING_MANAGE", RbacTarget.NONE);
        if (hearingAt == null || !hearingAt.isAfter(OffsetDateTime.now())
                || notes == null || notes.isBlank() || notes.length() > 5000) {
            throw new BusinessRuleViolationException("HEARING_DETAILS_INVALID", "A future hearing time and notes are required.");
        }
        jdbc.update("""
                UPDATE app.grievance_case SET status='HEARING_SCHEDULED',next_hearing_at=?,
                       updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND grievance_case_id=?
                """, hearingAt, principal.userAccountId(), principal.tenantId(), caseId);
        jdbc.update("""
                INSERT INTO app.grievance_hearing
                  (tenant_id,grievance_hearing_id,grievance_case_id,hearing_at,hearing_notes,
                   notice_file_id,status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,'SCHEDULED',?,?)
                """, principal.tenantId(), UUID.randomUUID(), caseId, hearingAt, notes.trim(),
                noticeFileId, principal.userAccountId(), principal.userAccountId());
        appendAction(principal, current, "HEARING_SCHEDULED", notes, noticeFileId, null, null, hearingAt);
        return get(principal, caseId);
    }

    @Transactional
    public void recordMinutes(TenantPrincipal principal, UUID caseId, String notes, UUID minutesFileId) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_HEARING_MANAGE", RbacTarget.NONE);
        if (notes == null || notes.isBlank() || notes.length() > 5000) {
            throw new BusinessRuleViolationException("HEARING_MINUTES_REQUIRED", "Hearing minutes are required.");
        }
        int updated = jdbc.update("""
                UPDATE app.grievance_hearing
                   SET status='HELD',minutes_notes=?,minutes_file_id=?,updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND grievance_hearing_id=(
                   SELECT grievance_hearing_id FROM app.grievance_hearing
                    WHERE tenant_id=? AND grievance_case_id=? AND status='SCHEDULED'
                    ORDER BY hearing_at DESC LIMIT 1)
                """, notes.trim(), minutesFileId, principal.userAccountId(),
                principal.tenantId(), principal.tenantId(), caseId);
        if (updated != 1) {
            throw new BusinessRuleViolationException("HEARING_NOT_SCHEDULED",
                    "Schedule a hearing before recording its minutes.");
        }
        appendAction(principal, current, "MINUTES_RECORDED", notes, null, minutesFileId, null, OffsetDateTime.now());
    }

    @Transactional
    public void issuePenalty(TenantPrincipal principal, UUID caseId, String penalty, String notes) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_CASE_RESOLVE", grievanceTarget(current));
        if (penalty == null || penalty.isBlank() || penalty.length() > 2000
                || notes == null || notes.isBlank() || notes.length() > 5000) {
            throw new BusinessRuleViolationException("PENALTY_DETAILS_INVALID", "Penalty and action notes are required.");
        }
        appendAction(principal, current, "PENALTY_ISSUED", notes, null, null, penalty, OffsetDateTime.now());
    }

    @Transactional
    public CaseView resolve(TenantPrincipal principal, UUID caseId, String outcome, String notes) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_CASE_RESOLVE", grievanceTarget(current));
        if (!List.of("RESOLVED", "DISMISSED").contains(outcome)
                || notes == null || notes.isBlank() || notes.length() > 5000) {
            throw new BusinessRuleViolationException("RESOLUTION_INVALID", "Choose RESOLVED or DISMISSED and provide a reason.");
        }
        jdbc.update("""
                UPDATE app.grievance_case SET status=?,resolution_notes=?,updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND grievance_case_id=?
                """, outcome, notes.trim(), principal.userAccountId(), principal.tenantId(), caseId);
        appendAction(principal, current, outcome, notes.trim(), null, null, null, OffsetDateTime.now());
        audit.record(new AuditEntry(AuditEventType.APPROVAL, "GRIEVANCE_CASE_RESOLVE",
                "GRIEVANCE_CASE", caseId, current.institutionId(), Map.of("status", current.status()),
                Map.of("status", outcome), 1, "H", "SUCCESS"));
        return get(principal, caseId);
    }

    @Transactional
    public FileUploadResponse uploadEvidence(TenantPrincipal principal, UUID caseId, MultipartFile file) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_EVIDENCE_UPLOAD", RbacTarget.NONE);
        FileUploadResponse uploaded = files.upload(principal, file, "GRIEVANCE_EVIDENCE",
                current.institutionId(), "GRIEVANCE_CASE", caseId);
        jdbc.update("""
                INSERT INTO app.grievance_evidence
                  (tenant_id,grievance_evidence_id,grievance_case_id,file_id,uploaded_by_user_id,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?)
                """, principal.tenantId(), UUID.randomUUID(), caseId, uploaded.fileId(),
                principal.userAccountId(), principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "GRIEVANCE_EVIDENCE_UPLOAD",
                "GRIEVANCE_CASE", caseId, current.institutionId(), null,
                Map.of("fileId", uploaded.fileId().toString()), 1, "H", "SUCCESS"));
        return uploaded;
    }

    @Transactional
    public List<Evidence> evidence(TenantPrincipal principal, UUID caseId) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_EVIDENCE_VIEW", RbacTarget.NONE);
        List<Evidence> result = jdbc.query("""
                SELECT grievance_evidence_id,file_id,uploaded_by_user_id,created_at
                  FROM app.grievance_evidence
                 WHERE tenant_id=? AND grievance_case_id=?
                 ORDER BY created_at
                """, (rs, n) -> new Evidence(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, OffsetDateTime.class)),
                principal.tenantId(), caseId);
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "GRIEVANCE_EVIDENCE_VIEW",
                "GRIEVANCE_EVIDENCE", null, current.institutionId(), null,
                Map.of("recordCount", result.size()), result.size(), "H", "SUCCESS"));
        return result;
    }

    @Transactional
    public List<Hearing> hearings(TenantPrincipal principal, UUID caseId) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_CASE_VIEW", RbacTarget.NONE);
        List<Hearing> result = jdbc.query("""
                SELECT grievance_hearing_id,hearing_at,hearing_notes,notice_file_id,
                       minutes_file_id,minutes_notes,status,updated_at
                  FROM app.grievance_hearing
                 WHERE tenant_id=? AND grievance_case_id=?
                 ORDER BY hearing_at DESC
                """, (rs, n) -> new Hearing(rs.getObject(1, UUID.class),
                rs.getObject(2, OffsetDateTime.class), rs.getString(3), rs.getObject(4, UUID.class),
                rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                rs.getObject(8, OffsetDateTime.class)), principal.tenantId(), caseId);
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "GRIEVANCE_CASE_VIEW",
                "GRIEVANCE_HEARING", null, current.institutionId(), null,
                Map.of("caseId", caseId.toString(), "recordCount", result.size()),
                result.size(), "H", "SUCCESS"));
        return result;
    }

    @Transactional
    public List<CaseAction> actions(TenantPrincipal principal, UUID caseId) {
        CaseView current = get(principal, caseId);
        authorize(principal, current.institutionId(), "GRIEVANCE_CASE_VIEW", RbacTarget.NONE);
        List<CaseAction> result = jdbc.query("""
                SELECT grievance_action_id,action_type,action_notes,action_at,performed_by_user_id,
                       notice_file_id,minutes_file_id,penalty
                  FROM app.grievance_action
                 WHERE tenant_id=? AND grievance_case_id=?
                 ORDER BY action_at,created_at
                """, (rs, n) -> new CaseAction(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getObject(4, OffsetDateTime.class), rs.getObject(5, UUID.class),
                rs.getObject(6, UUID.class), rs.getObject(7, UUID.class), rs.getString(8)),
                principal.tenantId(), caseId);
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "GRIEVANCE_CASE_VIEW",
                "GRIEVANCE_ACTION", null, current.institutionId(), null,
                Map.of("caseId", caseId.toString(), "recordCount", result.size()),
                result.size(), "H", "SUCCESS"));
        return result;
    }

    private void appendAction(TenantPrincipal principal, CaseView current, String type, String notes,
            UUID noticeFileId, UUID minutesFileId, String penalty, OffsetDateTime actionAt) {
        jdbc.update("""
                INSERT INTO app.grievance_action
                  (tenant_id,grievance_action_id,grievance_case_id,action_type,action_notes,
                   action_at,performed_by_user_id,notice_file_id,minutes_file_id,penalty,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, principal.tenantId(), UUID.randomUUID(), current.grievanceCaseId(), type,
                notes.trim(), actionAt, principal.userAccountId(), noticeFileId, minutesFileId,
                penalty, principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.EDIT, actionCode(type),
                "GRIEVANCE_CASE", current.grievanceCaseId(), current.institutionId(), null,
                Map.of("actionType", type), 1, "H", "SUCCESS"));
    }

    private String actionCode(String type) {
        return switch (type) {
            case "ASSIGNED" -> "GRIEVANCE_CASE_ASSIGN";
            case "HEARING_SCHEDULED", "NOTICE_ISSUED", "MINUTES_RECORDED" -> "GRIEVANCE_HEARING_MANAGE";
            case "PENALTY_ISSUED", "RESOLVED", "DISMISSED" -> "GRIEVANCE_CASE_RESOLVE";
            default -> "GRIEVANCE_CASE_VIEW";
        };
    }

    private boolean can(TenantPrincipal principal, UUID institutionId, String action) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        PermissionDecision decision = permissions.can(caller, action, scope, RbacTarget.NONE);
        return decision != null && decision.allowed();
    }

    private RbacTarget grievanceTarget(CaseView current) {
        return current.accusedUserId() == null ? RbacTarget.NONE : RbacTarget.of(current.accusedUserId());
    }

    private void authorize(TenantPrincipal principal, UUID institutionId, String action, RbacTarget target) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        permissions.authorize(caller, action, scope, target);
    }

    private CaseView caseRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CaseView(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getBoolean(7),
                rs.getString(8), rs.getObject(9, UUID.class), rs.getObject(10, OffsetDateTime.class),
                rs.getString(11), rs.getObject(12, OffsetDateTime.class));
    }
}
