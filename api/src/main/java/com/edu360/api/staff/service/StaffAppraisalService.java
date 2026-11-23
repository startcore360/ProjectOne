package com.edu360.api.staff.service;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacTarget;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StaffAppraisalService {
    public static final String SCORING_PLACEHOLDER = "PROVISIONAL 20% self / 50% HOD / 30% principal; peer feedback is qualitative only.";
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    public StaffAppraisalService(JdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> cycles(RbacPrincipal actor, UUID institutionId) {
        var scope = permissions.targetScope(actor, institutionId, null);
        permissions.authorize(actor, "STAFF_APPRAISAL_VIEW", scope, RbacTarget.NONE);
        return jdbc.queryForList("""
            SELECT id,institution_id,academic_year_id,name,start_date,end_date,status,row_version
            FROM app.appraisal_cycle WHERE tenant_id=? AND institution_id=?
            ORDER BY start_date DESC,name
            """, actor.tenantId(), institutionId);
    }

    @Transactional
    public Map<String,Object> createCycle(RbacPrincipal actor, UUID institutionId, UUID academicYearId,
            String name, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        var scope = permissions.targetScope(actor, institutionId, null);
        permissions.authorize(actor, "STAFF_APPRAISAL_CYCLE_MANAGE", scope, RbacTarget.NONE);
        if (name == null || name.isBlank() || startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "Provide a name and a valid appraisal date range.");
        UUID id = UUID.randomUUID();
        UUID userId = actor.userAccountId();
        jdbc.update("""
            INSERT INTO app.appraisal_cycle(tenant_id,id,institution_id,academic_year_id,name,start_date,end_date,status,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,'DRAFT',?,?)
            """, actor.tenantId(), id, institutionId, academicYearId, name.trim(), startDate, endDate, userId, userId);
        return jdbc.queryForMap("SELECT id,institution_id,academic_year_id,name,start_date,end_date,status,row_version FROM app.appraisal_cycle WHERE tenant_id=? AND id=?",
                actor.tenantId(), id);
    }

    @Transactional
    public Map<String,Object> submitSelfEvaluation(RbacPrincipal actor, UUID cycleId, BigDecimal score, String commentsJson) {
        var cycle = cycle(actor.tenantId(), cycleId);
        UUID staffId = staffId(actor);
        authorize(actor, "STAFF_APPRAISAL_SELF_SUBMIT", (UUID)cycle.get("institution_id"), staffId);
        validateScore(score);
        if (!"OPEN".equals(cycle.get("status"))) throw new BusinessRuleViolationException("APPRAISAL_CYCLE_NOT_OPEN", "The appraisal cycle is not open.");
        UUID id = jdbc.query("""
            INSERT INTO app.appraisal_submission(tenant_id,id,cycle_id,institution_id,staff_id,self_score,status,comments_json,created_by,updated_by)
            VALUES (?,?,?,?,?,?,'SELF_SUBMITTED',COALESCE(NULLIF(?,'')::jsonb,'{}'::jsonb),?,?)
            ON CONFLICT (tenant_id,cycle_id,staff_id) DO UPDATE SET self_score=EXCLUDED.self_score,
              status='SELF_SUBMITTED',comments_json=app.appraisal_submission.comments_json || EXCLUDED.comments_json,
              updated_by=EXCLUDED.updated_by
            RETURNING id
            """, (rs, n) -> rs.getObject(1, UUID.class), actor.tenantId(), UUID.randomUUID(), cycleId,
                cycle.get("institution_id"), staffId, score, commentsJson, actor.userAccountId(), actor.userAccountId()).get(0);
        return submission(actor.tenantId(), id);
    }

    @Transactional
    public Map<String,Object> addPeerReview(RbacPrincipal actor, UUID submissionId, String feedback) {
        var row = submissionRow(actor.tenantId(), submissionId);
        UUID reviewerId = staffId(actor);
        UUID staffId = (UUID) row.get("staff_id");
        if (reviewerId.equals(staffId)) throw new BusinessRuleViolationException("SELF_PEER_REVIEW_FORBIDDEN", "You cannot submit peer feedback on your own appraisal.");
        authorize(actor, "STAFF_APPRAISAL_PEER_REVIEW", (UUID)row.get("institution_id"), staffId);
        if (feedback == null || feedback.isBlank() || feedback.length() > 5000)
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "Peer feedback must contain 1–5000 characters.");
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.appraisal_review(tenant_id,id,cycle_id,institution_id,submission_id,reviewer_staff_id,review_type,feedback,created_by,updated_by)
            VALUES (?,?,?,?,?,?,'PEER',?,?,?)
            """, actor.tenantId(), id, row.get("cycle_id"), row.get("institution_id"), submissionId, reviewerId,
                feedback.trim(), actor.userAccountId(), actor.userAccountId());
        return jdbc.queryForMap("SELECT id,submission_id,reviewer_staff_id,review_type,feedback,created_at FROM app.appraisal_review WHERE tenant_id=? AND id=?", actor.tenantId(), id);
    }

    @Transactional
    public Map<String,Object> scoreAsHod(RbacPrincipal actor, UUID submissionId, BigDecimal score, String commentsJson) {
        return setScore(actor, submissionId, score, commentsJson, "STAFF_APPRAISAL_HOD_SCORE", "hod_score", "HOD_REVIEWED");
    }

    @Transactional
    public Map<String,Object> principalReview(RbacPrincipal actor, UUID submissionId, BigDecimal score, String commentsJson, String promotionReadiness) {
        var row = submissionRow(actor.tenantId(), submissionId);
        authorize(actor, "STAFF_APPRAISAL_PRINCIPAL_REVIEW", (UUID)row.get("institution_id"), (UUID)row.get("staff_id"));
        validateScore(score);
        if (promotionReadiness == null || !List.of("NOT_ASSESSED","READY_FOR_REVIEW","NOT_READY").contains(promotionReadiness))
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "Promotion readiness must be NOT_ASSESSED, READY_FOR_REVIEW or NOT_READY.");
        jdbc.update("""
            UPDATE app.appraisal_submission SET principal_score=?,
              final_rating=CASE WHEN self_score IS NOT NULL AND hod_score IS NOT NULL
                THEN round((self_score*0.20 + hod_score*0.50 + ?*0.30)::numeric,2) ELSE NULL END,
              status='PRINCIPAL_REVIEWED',
              comments_json=comments_json || COALESCE(NULLIF(?,'')::jsonb,'{}'::jsonb) ||
                jsonb_build_object('promotionReadiness',?,'scoringRule',?),
              updated_by=? WHERE tenant_id=? AND id=?
            """, score, score, commentsJson, promotionReadiness, SCORING_PLACEHOLDER,
                actor.userAccountId(), actor.tenantId(), submissionId);
        return submission(actor.tenantId(), submissionId);
    }

    private Map<String,Object> setScore(RbacPrincipal actor, UUID id, BigDecimal score, String comments, String action, String column, String status) {
        var row = submissionRow(actor.tenantId(), id);
        authorize(actor, action, (UUID)row.get("institution_id"), (UUID)row.get("staff_id"));
        validateScore(score);
        String sql = "UPDATE app.appraisal_submission SET " + column + "=?, status=?, comments_json=comments_json || COALESCE(NULLIF(?,'')::jsonb,'{}'::jsonb), updated_by=? WHERE tenant_id=? AND id=?";
        jdbc.update(sql, score, status, comments, actor.userAccountId(), actor.tenantId(), id);
        return submission(actor.tenantId(), id);
    }

    private void authorize(RbacPrincipal actor, String action, UUID institutionId, UUID staffId) {
        if (("STAFF_APPRAISAL_HOD_SCORE".equals(action)
                || "STAFF_APPRAISAL_PRINCIPAL_REVIEW".equals(action))
                && staffId(actor).equals(staffId)) {
            throw new BusinessRuleViolationException("SELF_APPRAISAL_REVIEW_FORBIDDEN",
                    "A staff member cannot score or complete the principal review of their own appraisal.");
        }
        UUID departmentId = jdbc.query("""
            SELECT department_id FROM app.staff_assignment
            WHERE tenant_id=? AND staff_profile_id=? AND institution_id=? AND status='ACTIVE'
              AND (effective_to IS NULL OR effective_to>=current_date)
            ORDER BY effective_from DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                actor.tenantId(), staffId, institutionId);
        permissions.authorize(actor, action,
                permissions.targetScope(actor, institutionId, departmentId), RbacTarget.NONE);
    }
    private Map<String,Object> cycle(UUID tenant, UUID id) {
        try { return jdbc.queryForMap("SELECT id,institution_id,status FROM app.appraisal_cycle WHERE tenant_id=? AND id=?", tenant, id); }
        catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new ResourceNotFoundException("Appraisal cycle not found."); }
    }
    private Map<String,Object> submissionRow(UUID tenant, UUID id) {
        try { return jdbc.queryForMap("SELECT id,cycle_id,institution_id,staff_id FROM app.appraisal_submission WHERE tenant_id=? AND id=?", tenant, id); }
        catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new ResourceNotFoundException("Appraisal submission not found."); }
    }
    private Map<String,Object> submission(UUID tenant, UUID id) {
        return jdbc.queryForMap("SELECT id,cycle_id,staff_id,self_score,hod_score,principal_score,final_rating,status,comments_json,row_version FROM app.appraisal_submission WHERE tenant_id=? AND id=?", tenant, id);
    }
    private UUID staffId(RbacPrincipal actor) {
        List<UUID> ids = jdbc.query("SELECT staff_profile_id FROM app.staff_profile WHERE tenant_id=? AND user_account_id=? AND employment_status='ACTIVE'",
                (rs,n)->rs.getObject(1,UUID.class), actor.tenantId(), actor.userAccountId());
        if (ids.size()!=1) throw new ResourceNotFoundException("An active linked staff profile was not found.");
        return ids.get(0);
    }
    private static void validateScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO)<0 || score.compareTo(new BigDecimal("100"))>0)
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "Score must be between 0 and 100.");
    }
}
