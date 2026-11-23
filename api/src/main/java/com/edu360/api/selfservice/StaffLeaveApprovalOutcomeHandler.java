package com.edu360.api.selfservice;

import com.edu360.api.approval.ApprovalOutcomeHandler;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StaffLeaveApprovalOutcomeHandler implements ApprovalOutcomeHandler {
    private final JdbcTemplate jdbc;

    public StaffLeaveApprovalOutcomeHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(ApprovalRequest request) {
        return request != null && "STAFF_LEAVE_REQUEST".equalsIgnoreCase(request.getSubjectType())
                && "STAFF_LEAVE".equalsIgnoreCase(request.getProcessCode());
    }

    @Override
    @Transactional
    public void apply(UUID tenantId, ApprovalRequest approval, boolean approved, UUID actorId) {
        Map<String, Object> leave;
        try {
            leave = jdbc.queryForMap("""
                    SELECT leave_request_id,institution_id,for_staff_profile_id,leave_type_id,
                           start_date,end_date,days_counted,loss_of_pay_days,loss_of_pay_confirmed_at,
                           designated_delegate_staff_profile_id,status
                      FROM app.leave_request
                     WHERE tenant_id=? AND leave_request_id=? FOR UPDATE
                    """, tenantId, approval.getSubjectId());
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessRuleViolationException("LEAVE_REQUEST_MISSING", "The leave request linked to this approval no longer exists.");
        }
        if (!"PENDING".equals(leave.get("status"))) {
            throw new BusinessRuleViolationException("LEAVE_REQUEST_NOT_PENDING", "The linked leave request is not pending.");
        }
        if (!approved) {
            jdbc.update("UPDATE app.leave_request SET status='REJECTED',updated_by=? WHERE tenant_id=? AND leave_request_id=?",
                    actorId, tenantId, approval.getSubjectId());
            return;
        }

        BigDecimal days = (BigDecimal) leave.get("days_counted");
        BigDecimal existingLop = (BigDecimal) leave.get("loss_of_pay_days");
        boolean confirmed = leave.get("loss_of_pay_confirmed_at") != null;
        Map<String, Object> type = jdbc.queryForMap("""
                SELECT tracks_balance,is_loss_of_pay FROM app.leave_type
                 WHERE tenant_id=? AND leave_type_id=?
                """, tenantId, leave.get("leave_type_id"));
        if (Boolean.TRUE.equals(type.get("is_loss_of_pay"))) {
            if (!confirmed) throw new BusinessRuleViolationException("LOSS_OF_PAY_CONFIRMATION_REQUIRED",
                    "HR must confirm the loss-of-pay leave before final approval.");
        } else if (Boolean.TRUE.equals(type.get("tracks_balance"))) {
            Map<String, Object> balance = null;
            try {
                balance = jdbc.queryForMap("""
                        SELECT leave_balance_id,credited_days,used_days FROM app.leave_balance
                         WHERE tenant_id=? AND staff_profile_id=? AND leave_type_id=? AND leave_year=?
                         FOR UPDATE
                        """, tenantId, leave.get("for_staff_profile_id"), leave.get("leave_type_id"),
                        ((java.sql.Date) leave.get("start_date")).toLocalDate().getYear());
            } catch (EmptyResultDataAccessException ignored) {
                // A missing balance is zero available days; HR may confirm the resulting loss of pay.
            }
            BigDecimal available = balance == null ? BigDecimal.ZERO
                    : ((BigDecimal) balance.get("credited_days")).subtract((BigDecimal) balance.get("used_days"));
            BigDecimal debit = days.min(available.max(BigDecimal.ZERO));
            BigDecimal shortage = days.subtract(debit).max(BigDecimal.ZERO);
            if (shortage.signum() > 0 && !confirmed) {
                throw new BusinessRuleViolationException("INSUFFICIENT_LEAVE_BALANCE",
                        "Insufficient leave balance. HR must confirm the loss-of-pay portion before approval.");
            }
            if (debit.signum() > 0) {
                jdbc.update("""
                        UPDATE app.leave_balance SET used_days=used_days+?,updated_by=?
                         WHERE tenant_id=? AND leave_balance_id=? AND used_days+?<=credited_days
                        """, debit, actorId, tenantId, balance.get("leave_balance_id"), debit);
            }
            existingLop = shortage;
        }
        jdbc.update("""
                UPDATE app.leave_request SET status='APPROVED',loss_of_pay_days=?,updated_by=?
                 WHERE tenant_id=? AND leave_request_id=?
                """, existingLop, actorId, tenantId, approval.getSubjectId());

        Object delegate = leave.get("designated_delegate_staff_profile_id");
        if (delegate != null) {
            jdbc.update("""
                    INSERT INTO app.leave_delegation
                      (tenant_id,leave_delegation_id,institution_id,leave_request_id,
                       delegator_staff_profile_id,delegate_staff_profile_id,start_date,end_date,
                       reason,status,created_by,updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)
                    """, tenantId, UUID.randomUUID(), leave.get("institution_id"), approval.getSubjectId(),
                    leave.get("for_staff_profile_id"), delegate, leave.get("start_date"), leave.get("end_date"),
                    "Approved leave cover", actorId, actorId);
        }
    }
}
