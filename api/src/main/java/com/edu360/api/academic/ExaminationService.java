package com.edu360.api.academic;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExaminationService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    public ExaminationService(JdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional
    public Map<String, Object> createCycle(UUID tenantId, TenantPrincipal actor, ExamCycleRequest request) {
        authorize(tenantId, actor, "EXAM_SCHEDULE_INTERNAL", request.institutionId());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.exam_cycle
                (tenant_id, exam_cycle_id, institution_id, academic_session_id, semester_id,
                 cycle_name, cycle_type, start_date, end_date, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """, tenantId, id, request.institutionId(), request.academicSessionId(), request.semesterId(),
                request.name(), request.cycleType(), request.startDate(), request.endDate(),
                actor.userAccountId(), actor.userAccountId());
        return jdbc.queryForMap("SELECT * FROM app.exam_cycle WHERE tenant_id = ? AND exam_cycle_id = ?", tenantId, id);
    }

    @Transactional
    public Map<String, Object> schedule(UUID tenantId, TenantPrincipal actor, ExamScheduleRequest request) {
        UUID institutionId = jdbc.query("""
                SELECT institution_id FROM app.exam_cycle WHERE tenant_id = ? AND exam_cycle_id = ? AND status IN ('DRAFT','SCHEDULED')
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, request.examCycleId());
        if (institutionId == null) throw new ResourceNotFoundException("Exam cycle not found or no longer editable.");
        authorize(tenantId, actor, "EXAM_SCHEDULE_INTERNAL", institutionId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.exam_schedule
                (tenant_id, exam_schedule_id, exam_cycle_id, course_offering_id, exam_date,
                 start_time, end_time, max_marks, pass_marks, room_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tenantId, id, request.examCycleId(), request.courseOfferingId(), request.examDate(),
                request.startTime(), request.endTime(), request.maxMarks(), request.passMarks(),
                request.roomId(), actor.userAccountId(), actor.userAccountId());
        return jdbc.queryForMap("SELECT * FROM app.exam_schedule WHERE tenant_id = ? AND exam_schedule_id = ?", tenantId, id);
    }

    @Transactional
    public void assignInvigilator(UUID tenantId, TenantPrincipal actor, UUID scheduleId, UUID staffUserId) {
        UUID institutionId = institutionForSchedule(tenantId, scheduleId);
        authorize(tenantId, actor, "EXAM_SCHEDULE_INTERNAL", institutionId);
        jdbc.update("""
                INSERT INTO app.exam_invigilator
                (tenant_id, exam_invigilator_id, exam_schedule_id, staff_user_id, assigned_by)
                VALUES (?, ?, ?, ?, ?)
                """, tenantId, UUID.randomUUID(), scheduleId, staffUserId, actor.userAccountId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> schedules(UUID tenantId, TenantPrincipal actor, UUID cycleId) {
        UUID institutionId = jdbc.query("""
                SELECT institution_id FROM app.exam_cycle WHERE tenant_id = ? AND exam_cycle_id = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, cycleId);
        if (institutionId == null) throw new ResourceNotFoundException("Exam cycle not found.");
        authorize(tenantId, actor, "EXAM_SCHEDULE_INTERNAL", institutionId);
        return jdbc.queryForList("""
                SELECT es.*, ec.cycle_name, ec.cycle_type
                  FROM app.exam_schedule es JOIN app.exam_cycle ec
                    ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
                 WHERE es.tenant_id = ? AND es.exam_cycle_id = ?
                 ORDER BY es.exam_date, es.start_time
                """, tenantId, cycleId);
    }

    @Transactional
    public Map<String, Object> enterMarks(UUID tenantId, TenantPrincipal actor, MarksEntryRequest request) {
        UUID institutionId = institutionForSchedule(tenantId, request.examScheduleId());
        authorize(tenantId, actor, "MARKS_ENTER", institutionId);
        if (request.isAbsent() && request.marksObtained() != null) {
            throw new BusinessRuleViolationException("ABSENT_MARKS_CONFLICT", "An absent student cannot also have a marks value.");
        }
        if (!request.isAbsent() && request.marksObtained() == null) {
            throw new BusinessRuleViolationException("MARKS_REQUIRED", "Marks are required unless the student is absent.");
        }
        BigDecimal max = jdbc.queryForObject("SELECT max_marks FROM app.exam_schedule WHERE tenant_id = ? AND exam_schedule_id = ?",
                BigDecimal.class, tenantId, request.examScheduleId());
        if (request.marksObtained() != null && (request.marksObtained().signum() < 0 || request.marksObtained().compareTo(max) > 0)) {
            throw new BusinessRuleViolationException("INVALID_MARKS", "Marks must be within the exam's maximum marks.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.exam_mark
                (tenant_id, exam_mark_id, exam_schedule_id, student_profile_id, marks_obtained, is_absent,
                 course_teacher_user_id, external_examiner_user_id, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ENTERED', ?, ?)
                ON CONFLICT (tenant_id, exam_schedule_id, student_profile_id)
                DO UPDATE SET marks_obtained = EXCLUDED.marks_obtained, is_absent = EXCLUDED.is_absent,
                              status = 'ENTERED', course_teacher_decision = 'PENDING',
                              external_examiner_user_id = EXCLUDED.external_examiner_user_id, external_examiner_decision = 'PENDING', moderated_marks = NULL,
                              updated_at = now(), updated_by = EXCLUDED.updated_by,
                              row_version = app.exam_mark.row_version + 1
                """, tenantId, id, request.examScheduleId(), request.studentProfileId(), request.marksObtained(),
                request.isAbsent(), actor.userAccountId(), request.externalExaminerUserId(),
                actor.userAccountId(), actor.userAccountId());
        return jdbc.queryForMap("""
                SELECT * FROM app.exam_mark WHERE tenant_id = ? AND exam_schedule_id = ? AND student_profile_id = ?
                """, tenantId, request.examScheduleId(), request.studentProfileId());
    }

    @Transactional
    public Map<String, Object> courseTeacherApprove(UUID tenantId, TenantPrincipal actor, UUID markId) {
        Map<String, Object> mark = mark(tenantId, markId);
        if (!actor.userAccountId().equals(mark.get("course_teacher_user_id"))) {
            throw new BusinessRuleViolationException("COURSE_TEACHER_REQUIRED", "Only the assigned course teacher may complete the first moderation tier.");
        }
        authorize(tenantId, actor, "MARKS_APPROVE_HOD", institutionForSchedule(tenantId, (UUID) mark.get("exam_schedule_id")));
        jdbc.update("""
                UPDATE app.exam_mark SET course_teacher_decision = 'APPROVED', status = 'MODERATION_PENDING',
                       updated_at = now(), updated_by = ?, row_version = row_version + 1
                 WHERE tenant_id = ? AND exam_mark_id = ? AND status = 'ENTERED'
                """, actor.userAccountId(), tenantId, markId);
        return mark(tenantId, markId);
    }

    @Transactional
    public Map<String, Object> externalModerate(UUID tenantId, TenantPrincipal actor, UUID markId,
            BigDecimal moderatedMarks, String decision, String comment) {
        Map<String, Object> mark = mark(tenantId, markId);
        UUID examiner = (UUID) mark.get("external_examiner_user_id");
        if (examiner == null || !examiner.equals(actor.userAccountId())) {
            throw new BusinessRuleViolationException("EXTERNAL_EXAMINER_REQUIRED", "Only the assigned external examiner may complete the second moderation tier.");
        }
        if (examiner.equals(mark.get("course_teacher_user_id"))) {
            throw new BusinessRuleViolationException("MODERATOR_INDEPENDENCE_REQUIRED", "The course teacher cannot moderate their own marks.");
        }
        if (!"APPROVED".equals(mark.get("course_teacher_decision"))) {
            throw new BusinessRuleViolationException("FIRST_TIER_PENDING", "Course-teacher approval is required before external moderation.");
        }
        if (!List.of("APPROVED", "REJECTED").contains(decision)) {
            throw new BusinessRuleViolationException("INVALID_MODERATION_DECISION", "Decision must be APPROVED or REJECTED.");
        }
        authorize(tenantId, actor, "MARKS_EXTERNAL_UPLOAD", institutionForSchedule(tenantId, (UUID) mark.get("exam_schedule_id")));
        BigDecimal max = jdbc.queryForObject("SELECT max_marks FROM app.exam_schedule WHERE tenant_id = ? AND exam_schedule_id = ?",
                BigDecimal.class, tenantId, mark.get("exam_schedule_id"));
        if ("APPROVED".equals(decision) && !Boolean.TRUE.equals(mark.get("is_absent"))
                && (moderatedMarks == null || moderatedMarks.signum() < 0 || moderatedMarks.compareTo(max) > 0)) {
            throw new BusinessRuleViolationException("INVALID_MODERATED_MARKS", "Moderated marks must be within the maximum marks.");
        }
        jdbc.update("""
                UPDATE app.exam_mark SET external_examiner_decision = ?, external_examiner_comment = ?,
                       moderated_marks = ?, status = ?, updated_at = now(), updated_by = ?,
                       row_version = row_version + 1
                 WHERE tenant_id = ? AND exam_mark_id = ? AND status = 'MODERATION_PENDING'
                """, decision, comment, moderatedMarks, "APPROVED".equals(decision) ? "APPROVED" : "REJECTED",
                actor.userAccountId(), tenantId, markId);
        return mark(tenantId, markId);
    }

    private Map<String, Object> mark(UUID tenantId, UUID markId) {
        try {
            return jdbc.queryForMap("SELECT * FROM app.exam_mark WHERE tenant_id = ? AND exam_mark_id = ?", tenantId, markId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Exam mark not found.");
        }
    }

    private UUID institutionForSchedule(UUID tenantId, UUID scheduleId) {
        UUID institutionId = jdbc.query("""
                SELECT ec.institution_id FROM app.exam_schedule es JOIN app.exam_cycle ec
                  ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
                 WHERE es.tenant_id = ? AND es.exam_schedule_id = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, scheduleId);
        if (institutionId == null) throw new ResourceNotFoundException("Exam schedule not found.");
        return institutionId;
    }

    private void authorize(UUID tenantId, TenantPrincipal actor, String action, UUID institutionId) {
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()), action,
                RbacScope.institution(institutionId), RbacTarget.NONE);
    }

    public record ExamCycleRequest(UUID institutionId, UUID academicSessionId, UUID semesterId,
            String name, String cycleType, java.time.LocalDate startDate, java.time.LocalDate endDate) {}
    public record ExamScheduleRequest(UUID examCycleId, UUID courseOfferingId, java.time.LocalDate examDate,
            java.time.LocalTime startTime, java.time.LocalTime endTime, BigDecimal maxMarks,
            BigDecimal passMarks, UUID roomId) {}
    public record MarksEntryRequest(UUID examScheduleId, UUID studentProfileId, BigDecimal marksObtained,
            boolean isAbsent, UUID externalExaminerUserId) {}
}
