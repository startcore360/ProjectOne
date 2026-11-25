package com.edu360.api.academic;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileService;
import com.edu360.api.files.FileUploadResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssignmentService {
    private final JdbcTemplate jdbc;
    private final FileService files;
    private final PermissionEvaluatorService permissions;
    private final ObjectMapper objectMapper;

    public AssignmentService(JdbcTemplate jdbc, FileService files, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.files = files;
        this.permissions = permissions;
    }

    @Transactional
    public Map<String, Object> create(UUID tenantId, TenantPrincipal actor, CreateAssignmentRequest request) {
        authorize(tenantId, actor, "ASSIGNMENT_CREATE", request.institutionId());
        UUID id = UUID.randomUUID();
        String rubric = request.rubricJson() == null || request.rubricJson().isBlank()
                ? "[{\\"criterion\\":\\"Overall\\",\\"weight\\":100}]" : request.rubricJson();
        validateRubric(rubric);
        jdbc.update("""
                INSERT INTO app.assignment
                (tenant_id, assignment_id, institution_id, section_id, course_offering_id, teacher_user_id,
                 title, description, max_marks, due_at, allow_late_submission, late_penalty_percent_per_day,
                 rubric_json, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PUBLISHED', ?, ?)
                """, tenantId, id, request.institutionId(), request.sectionId(), request.courseOfferingId(),
                actor.userAccountId(), request.title().trim(), request.description(), request.maxMarks(),
                request.dueAt(), request.allowLateSubmission(),
                request.latePenaltyPercentPerDay() == null ? BigDecimal.ZERO : request.latePenaltyPercentPerDay(),
                rubric, actor.userAccountId(), actor.userAccountId());
        return get(tenantId, actor, id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId, TenantPrincipal actor, UUID institutionId, UUID sectionId) {
        authorize(tenantId, actor, "ASSIGNMENT_VIEW", institutionId);
        return jdbc.queryForList("""
                SELECT assignment_id, institution_id, section_id, course_offering_id, teacher_user_id,
                       title, description, max_marks, due_at, allow_late_submission,
                       late_penalty_percent_per_day, rubric_json, status, created_at
                  FROM app.assignment
                 WHERE tenant_id = ? AND (?::uuid IS NULL OR institution_id = ?)
                   AND (?::uuid IS NULL OR section_id = ?)
                 ORDER BY due_at DESC
                """, tenantId, institutionId, institutionId, sectionId, sectionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, TenantPrincipal actor, UUID assignmentId) {
        Map<String, Object> row = jdbc.query("""
                SELECT assignment_id, institution_id, section_id, course_offering_id, teacher_user_id,
                       title, description, max_marks, due_at, allow_late_submission,
                       late_penalty_percent_per_day, rubric_json, status, created_at
                  FROM app.assignment WHERE tenant_id = ? AND assignment_id = ?
                """, rs -> rs.next() ? new java.util.LinkedHashMap<String, Object>() {{
                    put("assignmentId", rs.getObject("assignment_id"));
                    put("institutionId", rs.getObject("institution_id"));
                    put("sectionId", rs.getObject("section_id"));
                    put("courseOfferingId", rs.getObject("course_offering_id"));
                    put("teacherUserId", rs.getObject("teacher_user_id"));
                    put("title", rs.getString("title"));
                    put("description", rs.getString("description"));
                    put("maxMarks", rs.getBigDecimal("max_marks"));
                    put("dueAt", rs.getTimestamp("due_at").toInstant());
                    put("allowLateSubmission", rs.getBoolean("allow_late_submission"));
                    put("latePenaltyPercentPerDay", rs.getBigDecimal("late_penalty_percent_per_day"));
                    put("rubric", rs.getString("rubric_json"));
                    put("status", rs.getString("status"));
                    put("createdAt", rs.getTimestamp("created_at").toInstant());
                }} : null, tenantId, assignmentId);
        if (row == null) throw new ResourceNotFoundException("Assignment not found.");
        authorize(tenantId, actor, "ASSIGNMENT_VIEW", (UUID) row.get("institutionId"));
        return row;
    }

    @Transactional
    public Map<String, Object> submit(UUID tenantId, TenantPrincipal student, UUID assignmentId,
            UUID studentProfileId, UUID institutionId, MultipartFile upload) {
        authorize(tenantId, student, "ASSIGNMENT_SUBMIT", institutionId);
        Map<String, Object> assignment = jdbc.queryForMap("""
                SELECT institution_id, due_at, allow_late_submission, max_marks, late_penalty_percent_per_day, status
                  FROM app.assignment WHERE tenant_id = ? AND assignment_id = ?
                """, tenantId, assignmentId);
        if (!"PUBLISHED".equals(assignment.get("status"))) throw new BusinessRuleViolationException("ASSIGNMENT_CLOSED", "This assignment is not accepting submissions.");
        Instant now = Instant.now();
        Instant dueAt = ((java.sql.Timestamp) assignment.get("due_at")).toInstant();
        boolean late = now.isAfter(dueAt);
        if (late && !Boolean.TRUE.equals(assignment.get("allow_late_submission"))) {
            throw new BusinessRuleViolationException("LATE_SUBMISSION_NOT_ALLOWED", "The assignment deadline has passed.");
        }
        Boolean ownsProfile = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM app.student_profile
                                WHERE tenant_id = ? AND student_profile_id = ? AND user_account_id = ? AND status = 'ACTIVE')
                """, Boolean.class, tenantId, studentProfileId, student.userAccountId());
        if (!Boolean.TRUE.equals(ownsProfile)) throw new ResourceNotFoundException("Active student profile not found.");
        FileUploadResponse file = files.upload(student, upload, "ASSIGNMENT_SUBMISSION", (UUID) assignment.get("institution_id"));
        if (!"CLEAN".equals(file.scanStatus()) || !"ACTIVE".equals(file.status())) {
            throw new BusinessRuleViolationException("SUBMISSION_FILE_NOT_CLEAN", "Submission was not accepted because the uploaded file did not pass malware scanning.");
        }
        BigDecimal penalty = BigDecimal.ZERO;
        if (late) {
            long days = Math.max(1, (Duration.between(dueAt, now).toSeconds() + 86399) / 86400);
            penalty = ((BigDecimal) assignment.get("max_marks"))
                    .multiply((BigDecimal) assignment.get("late_penalty_percent_per_day"))
                    .multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .min((BigDecimal) assignment.get("max_marks"));
        }
        UUID submissionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.assignment_submission
                (tenant_id, submission_id, assignment_id, student_profile_id, submitted_at, file_id, file_sha256,
                 is_late, late_penalty_marks, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?)
                """, tenantId, submissionId, assignmentId, studentProfileId, now, file.fileId(),
                file.sha256(), late, penalty, student.userAccountId(), student.userAccountId());
        return jdbc.queryForMap("""
                SELECT submission_id, assignment_id, student_profile_id, submitted_at, is_late,
                       late_penalty_marks, status FROM app.assignment_submission
                 WHERE tenant_id = ? AND submission_id = ?
                """, tenantId, submissionId);
    }

    @Transactional
    public Map<String, Object> grade(UUID tenantId, TenantPrincipal teacher, UUID submissionId,
            BigDecimal marks, String feedback) {
        Map<String, Object> submission = jdbc.queryForMap("""
                SELECT s.assignment_id, s.late_penalty_marks, a.max_marks, a.institution_id, a.rubric_json
                  FROM app.assignment_submission s JOIN app.assignment a
                    ON a.tenant_id = s.tenant_id AND a.assignment_id = s.assignment_id
                 WHERE s.tenant_id = ? AND s.submission_id = ?
                """, tenantId, submissionId);
        authorize(tenantId, teacher, "ASSIGNMENT_EVALUATE", (UUID) submission.get("institution_id"));
        BigDecimal max = (BigDecimal) submission.get("max_marks");
        if (marks == null || marks.signum() < 0 || marks.compareTo(max) > 0) {
            throw new BusinessRuleViolationException("INVALID_MARKS", "Marks must be between zero and the assignment maximum.");
        }
        BigDecimal finalMarks = marks.subtract((BigDecimal) submission.get("late_penalty_marks")).max(BigDecimal.ZERO);
        jdbc.update("""
                UPDATE app.assignment_submission
                   SET marks_awarded = ?, final_marks = ?, feedback = ?, status = 'GRADED',
                       graded_by_user_id = ?, graded_at = now(), updated_at = now(), updated_by = ?,
                       row_version = row_version + 1
                 WHERE tenant_id = ? AND submission_id = ?
                """, marks, finalMarks, feedback, teacher.userAccountId(), teacher.userAccountId(), tenantId, submissionId);
        return jdbc.queryForMap("""
                SELECT submission_id, marks_awarded, late_penalty_marks, final_marks, feedback, status, graded_at
                  FROM app.assignment_submission WHERE tenant_id = ? AND submission_id = ?
                """, tenantId, submissionId);
    }

    private void validateRubric(String rubric) {
        try {
            JsonNode root = objectMapper.readTree(rubric);
            if (!root.isArray() || root.isEmpty()) {
                throw new BusinessRuleViolationException("INVALID_ASSIGNMENT_RUBRIC", "Rubric must be a non-empty array of weighted criteria.");
            }
            BigDecimal total = BigDecimal.ZERO;
            for (JsonNode item : root) {
                JsonNode weight = item.get("weight");
                if (weight == null || !weight.isNumber() || weight.decimalValue().signum() <= 0
                        || item.get("criterion") == null || !item.get("criterion").isTextual()
                        || item.get("criterion").asText().isBlank()) {
                    throw new BusinessRuleViolationException("INVALID_ASSIGNMENT_RUBRIC", "Each rubric criterion requires a name and positive numeric weight.");
                }
                total = total.add(weight.decimalValue());
            }
            if (total.compareTo(new BigDecimal("100")) != 0) {
                throw new BusinessRuleViolationException("INVALID_ASSIGNMENT_RUBRIC", "Rubric weights must total exactly 100 percent.");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessRuleViolationException("INVALID_ASSIGNMENT_RUBRIC", "Rubric must contain valid JSON.");
        }
    }

    private void authorize(UUID tenantId, TenantPrincipal actor, String action, UUID institutionId) {
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()), action,
                institutionId == null ? RbacScope.tenant() : RbacScope.institution(institutionId), RbacTarget.NONE);
    }
}
