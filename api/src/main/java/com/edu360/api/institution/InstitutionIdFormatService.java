package com.edu360.api.institution;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.InstitutionCodeFormat;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Service
public class InstitutionIdFormatService {
    private final JdbcTemplate jdbc;
    private final InstitutionRepository institutions;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService audit;
    private final StudentProfileRepository students;
    private final StaffProfileRepository staff;

    public InstitutionIdFormatService(JdbcTemplate jdbc, InstitutionRepository institutions,
            PermissionEvaluatorService permissions, AuditLogService audit,
            StudentProfileRepository students, StaffProfileRepository staff) {
        this.jdbc = jdbc;
        this.institutions = institutions;
        this.permissions = permissions;
        this.audit = audit;
        this.students = students;
        this.staff = staff;
    }

    @Transactional(readOnly = true)
    public InstitutionIdFormatsResponse get(RbacPrincipal actor, UUID institutionId) {
        permissions.authorize(actor, "INSTITUTION_CREATE_DRAFT", RbacScope.tenant(), RbacTarget.NONE);
        institutions.findByIdTenantIdAndIdInstitutionId(actor.tenantId(), institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found."));
        String student = findPattern("app.student_code_sequence", actor.tenantId(), institutionId);
        String staffFormat = findPattern("app.staff_code_sequence", actor.tenantId(), institutionId);
        return new InstitutionIdFormatsResponse(student, staffFormat,
                isLocked(actor.tenantId(), institutionId, "STUDENT"),
                isLocked(actor.tenantId(), institutionId, "STAFF"));
    }

    @Transactional
    public InstitutionIdFormatsResponse set(RbacPrincipal actor, UUID institutionId,
            InstitutionIdFormatsRequest request) {
        permissions.authorize(actor, "INSTITUTION_CREATE_DRAFT", RbacScope.tenant(), RbacTarget.NONE);
        Institution institution = institutions.findByIdTenantIdAndIdInstitutionId(actor.tenantId(), institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found."));
        InstitutionCodeFormat studentFormat = parse(request.studentIdFormat(), "studentIdFormat");
        InstitutionCodeFormat staffFormat = request.staffIdFormat() == null || request.staffIdFormat().isBlank()
                ? null : parse(request.staffIdFormat(), "staffIdFormat");

        validateDistinctive(studentFormat, "studentIdFormat");
        if (staffFormat != null) validateDistinctive(staffFormat, "staffIdFormat");

        String oldStudent = findPattern("app.student_code_sequence", actor.tenantId(), institutionId);
        String oldStaff = findPattern("app.staff_code_sequence", actor.tenantId(), institutionId);
        if (isLocked(actor.tenantId(), institutionId, "STUDENT")) {
            throw new BusinessRuleViolationException("ID_FORMAT_LOCKED",
                    "Student ID format is read-only after the first student code has been generated.");
        }
        if (isLocked(actor.tenantId(), institutionId, "STAFF")) {
            if (staffFormat == null || !staffFormat.value().equals(oldStaff)) {
                throw new BusinessRuleViolationException("ID_FORMAT_LOCKED",
                        "Staff ID format is read-only after the first staff code has been generated.");
            }
        }

        validateNoClash(actor.tenantId(), institutionId, studentFormat, "STUDENT", "studentIdFormat");
        if (staffFormat != null) validateNoClash(actor.tenantId(), institutionId, staffFormat, "STAFF", "staffIdFormat");

        upsertStudentPattern(actor, institutionId, studentFormat.value());
        jdbc.query("SELECT app.ensure_institution_code_sequence(?,?,?)",
                rs -> null, actor.tenantId(), institutionId, "STUDENT");

        if (staffFormat == null) {
            jdbc.update("DELETE FROM app.staff_code_sequence WHERE tenant_id=? AND institution_id=?",
                    actor.tenantId(), institutionId);
        } else {
            upsertStaffPattern(actor, institutionId, staffFormat.value());
            jdbc.query("SELECT app.ensure_institution_code_sequence(?,?,?)",
                    rs -> null, actor.tenantId(), institutionId, "STAFF");
        }

        audit.record(new AuditEntry(AuditEventType.INSTITUTION_ID_FORMAT_UPDATED,
                "INSTITUTION_CREATE_DRAFT", "INSTITUTION", institutionId, institutionId,
                Map.of("student_id_format", oldStudent == null ? "" : oldStudent,
                        "staff_id_format", oldStaff == null ? "" : oldStaff),
                Map.of("student_id_format", studentFormat.value(),
                        "staff_id_format", staffFormat == null ? "" : staffFormat.value()),
                1, "N", "SUCCESS"));

        return new InstitutionIdFormatsResponse(studentFormat.value(),
                staffFormat == null ? null : staffFormat.value(),
                false, isLocked(actor.tenantId(), institutionId, "STAFF"));
    }

    /** Allocate a never-reused student code. Call within the transaction that inserts the profile. */
    @Transactional
    public String nextStudentCode(UUID tenantId, UUID institutionId, int academicSessionStartYear) {
        Institution institution = institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found."));
        String pattern = findPattern("app.student_code_sequence", tenantId, institutionId);
        if (pattern == null) {
            throw new BusinessRuleViolationException("STUDENT_ID_FORMAT_NOT_SET",
                    "Set the Student ID format in Institution settings before creating students.");
        }
        InstitutionCodeFormat format = InstitutionCodeFormat.parse(pattern);
        for (int attempt = 0; attempt < 25; attempt++) {
            Long sequence = jdbc.queryForObject(
                    "SELECT app.next_institution_code_value(?,?,?)",
                    Long.class, tenantId, institutionId, "STUDENT");
            String code = format.render(institution.getInstitutionCode(), academicSessionStartYear, sequence);
            if (!students.existsByTenantIdAndStudentCode(tenantId, code)) return code;
            // A collision consumes this value; the next attempt advances the PostgreSQL sequence.
        }
        throw new DuplicateResourceException("Could not allocate a unique student code after 25 sequence values.");
    }

    /** HR-supplied codes win; otherwise use the optional institution staff format or fail closed. */
    @Transactional
    public String resolveStaffCode(UUID tenantId, UUID institutionId, String suppliedCode, int joiningYear) {
        if (suppliedCode != null && !suppliedCode.isBlank()) {
            String code = suppliedCode.trim().toUpperCase(java.util.Locale.ROOT);
            if (code.length() > 30 || staff.existsByIdTenantIdAndStaffCode(tenantId, code)) {
                throw new DuplicateResourceException("Staff code is invalid or already used in this tenant.");
            }
            return code;
        }
        String pattern = findPattern("app.staff_code_sequence", tenantId, institutionId);
        if (pattern == null) {
            throw new BusinessRuleViolationException("STAFF_CODE_REQUIRED",
                    "Enter a unique staff code or set the optional Staff ID format in Institution settings.");
        }
        Institution institution = institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found."));
        InstitutionCodeFormat format = InstitutionCodeFormat.parse(pattern);
        for (int attempt = 0; attempt < 25; attempt++) {
            Long sequence = jdbc.queryForObject("SELECT app.next_institution_code_value(?,?,?)",
                    Long.class, tenantId, institutionId, "STAFF");
            String code = format.render(institution.getInstitutionCode(), joiningYear, sequence);
            if (code.length() <= 30 && !staff.existsByIdTenantIdAndStaffCode(tenantId, code)) return code;
        }
        throw new DuplicateResourceException("Could not allocate a unique staff code after 25 sequence values.");
    }

    private InstitutionCodeFormat parse(String value, String field) {
        try { return InstitutionCodeFormat.parse(value); }
        catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", field + ": " + ex.getMessage());
        }
    }

    private void validateDistinctive(InstitutionCodeFormat format, String field) {
        if (!format.includesInstitutionToken() && format.fixedText().isBlank()) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED",
                    field + " must contain {INST} or distinguishing literal text.");
        }
    }

    private void validateNoClash(UUID tenantId, UUID institutionId, InstitutionCodeFormat candidate,
            String kind, String field) {
        String table = "STUDENT".equals(kind) ? "app.student_code_sequence" : "app.staff_code_sequence";
        List<PatternRow> rows = jdbc.query("""
                SELECT s.pattern, i.institution_name
                  FROM %s s
                  JOIN app.institution i
                    ON i.tenant_id=s.tenant_id AND i.institution_id=s.institution_id
                 WHERE s.tenant_id=? AND s.institution_id<>?
                """.formatted(table),
                (rs, row) -> new PatternRow(rs.getString(1), rs.getString(2)), tenantId, institutionId);
        for (PatternRow row : rows) {
            InstitutionCodeFormat existing = InstitutionCodeFormat.parse(row.pattern());
            if (candidate.clashesWith(existing)) {
                throw new BusinessRuleViolationException("ID_FORMAT_CLASH",
                        field + " clashes with institution '" + row.institutionName() + "'.");
            }
        }
    }

    private String findPattern(String table, UUID tenantId, UUID institutionId) {
        if ("app.student_code_sequence".equals(table)) {
            return jdbc.query("SELECT pattern FROM app.student_code_sequence WHERE tenant_id=? AND institution_id=?",
                    rs -> rs.next() ? rs.getString(1) : null, tenantId, institutionId);
        }
        return jdbc.query("SELECT pattern FROM app.staff_code_sequence WHERE tenant_id=? AND institution_id=?",
                rs -> rs.next() ? rs.getString(1) : null, tenantId, institutionId);
    }

    private boolean isLocked(UUID tenantId, UUID institutionId, String kind) {
        Boolean locked = jdbc.queryForObject("SELECT app.institution_code_format_locked(?,?,?)",
                Boolean.class, tenantId, institutionId, kind);
        return Boolean.TRUE.equals(locked);
    }

    private void upsertStudentPattern(RbacPrincipal actor, UUID institutionId, String pattern) {
        List<UUID> ids = jdbc.query("SELECT student_code_sequence_id FROM app.student_code_sequence WHERE tenant_id=? AND institution_id=? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), actor.tenantId(), institutionId);
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO app.student_code_sequence
                    (tenant_id,student_code_sequence_id,institution_id,pattern,last_value,created_by,updated_by)
                    VALUES (?,?,?,?,0,?,?)
                    """, actor.tenantId(), UUID.randomUUID(), institutionId, pattern,
                    actor.userAccountId(), actor.userAccountId());
        } else {
            jdbc.update("UPDATE app.student_code_sequence SET pattern=?,updated_by=? WHERE tenant_id=? AND institution_id=?",
                    pattern, actor.userAccountId(), actor.tenantId(), institutionId);
        }
    }

    private void upsertStaffPattern(RbacPrincipal actor, UUID institutionId, String pattern) {
        List<UUID> ids = jdbc.query("SELECT staff_code_sequence_id FROM app.staff_code_sequence WHERE tenant_id=? AND institution_id=? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), actor.tenantId(), institutionId);
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO app.staff_code_sequence
                    (tenant_id,staff_code_sequence_id,institution_id,pattern,last_value,created_by,updated_by)
                    VALUES (?,?,?,?,0,?,?)
                    """, actor.tenantId(), UUID.randomUUID(), institutionId, pattern,
                    actor.userAccountId(), actor.userAccountId());
        } else {
            jdbc.update("UPDATE app.staff_code_sequence SET pattern=?,updated_by=? WHERE tenant_id=? AND institution_id=?",
                    pattern, actor.userAccountId(), actor.tenantId(), institutionId);
        }
    }

    private record PatternRow(String pattern, String institutionName) {}
}
