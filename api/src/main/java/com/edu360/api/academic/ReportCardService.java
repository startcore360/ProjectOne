package com.edu360.api.academic;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportCardService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    public ReportCardService(JdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, TenantPrincipal actor, UUID reportCardId) {
        Map<String, Object> card;
        try {
            card = jdbc.queryForMap("""
                    SELECT rc.report_card_id, rc.institution_id, rc.student_profile_id, sp.full_name,
                           sp.student_code, rc.academic_session_id, rc.semester_id, rc.sgpa, rc.cgpa,
                           rc.total_credits, rc.grade_letter, rc.verification_hash, rc.status, rc.published_at
                      FROM app.report_card rc JOIN app.student_profile sp
                        ON sp.tenant_id = rc.tenant_id AND sp.student_profile_id = rc.student_profile_id
                     WHERE rc.tenant_id = ? AND rc.report_card_id = ?
                    """, tenantId, reportCardId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Report card not found.");
        }
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()), "RESULT_VIEW",
                RbacScope.institution((UUID) card.get("institution_id")), RbacTarget.NONE);
        return card;
    }

    @Transactional
    public Map<String, Object> publish(UUID tenantId, TenantPrincipal actor, UUID reportCardId) {
        Map<String, Object> card;
        try {
            card = jdbc.queryForMap("SELECT institution_id, status FROM app.report_card WHERE tenant_id = ? AND report_card_id = ?",
                    tenantId, reportCardId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Report card not found.");
        }
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()), "RESULT_PUBLISH",
                RbacScope.institution((UUID) card.get("institution_id")), RbacTarget.NONE);
        if ("PUBLISHED".equals(card.get("status"))) return get(tenantId, actor, reportCardId);
        try {
            jdbc.update("""
                    UPDATE app.report_card SET status = 'PUBLISHED', updated_at = now(),
                           updated_by = ?, row_version = row_version + 1
                     WHERE tenant_id = ? AND report_card_id = ?
                    """, actor.userAccountId(), tenantId, reportCardId);
        } catch (DataAccessException ex) {
            String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
            if (message != null && message.contains("PUBLISH_GATE_BLOCKED_FEE_BALANCE_UNAVAILABLE")) {
                throw new BusinessRuleViolationException("PUBLISH_GATE_BLOCKED_FEE_BALANCE_UNAVAILABLE",
                        "Publication is blocked until the fee-balance integration is available.");
            }
            if (message != null && message.contains("PUBLISH_GATE_BLOCKED_FEE_DUES")) {
                throw new BusinessRuleViolationException("PUBLISH_GATE_BLOCKED_FEE_DUES",
                        "Publication is blocked because outstanding fee dues remain.");
            }
            if (message != null && message.contains("PUBLISH_GATE_BLOCKED_MODERATION")) {
                throw new BusinessRuleViolationException("PUBLISH_GATE_BLOCKED_MODERATION",
                        "Publication is blocked until every examination mark is approved.");
            }
            if (message != null && message.contains("TERM_LOCKED")) {
                throw new BusinessRuleViolationException("TERM_LOCKED", "The academic term is locked.");
            }
            throw ex;
        }
        return get(tenantId, actor, reportCardId);
    }
}
