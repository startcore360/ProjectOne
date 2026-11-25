package com.edu360.api.finance;

import com.edu360.api.approval.ApprovalOutcomeHandler;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConcessionApprovalOutcomeHandler implements ApprovalOutcomeHandler {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogService audit;

    @Override
    public boolean supports(ApprovalRequest request) {
        return "CONCESSION".equalsIgnoreCase(request.getProcessCode())
                && "CONCESSION".equalsIgnoreCase(request.getSubjectType());
    }

    @Override
    @Transactional
    public void apply(UUID tenantId, ApprovalRequest request, boolean approved, UUID actorUserId) {
        UUID concessionId = request.getSubjectId();
        Map<String, Object> concession = jdbc.query("""
            SELECT concession_id, institution_id, student_fee_account_id, student_installment_id,
                   fee_head_id, amount, status
              FROM app.concession
             WHERE tenant_id=:tenantId AND concession_id=:concessionId
             FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("concessionId", concessionId), rs -> {
                if (!rs.next()) return null;
                return Map.of("institutionId", rs.getObject("institution_id", UUID.class),
                        "accountId", rs.getObject("student_fee_account_id", UUID.class),
                        "installmentId", rs.getObject("student_installment_id", UUID.class),
                        "feeHeadId", rs.getObject("fee_head_id", UUID.class),
                        "amount", rs.getBigDecimal("amount"),
                        "status", rs.getString("status"));
            });
        if (concession == null || !"REQUESTED".equals(concession.get("status"))) {
            throw new BusinessRuleViolationException("CONCESSION_NOT_PENDING",
                    "The concession is missing or has already been decided.");
        }

        if (!approved) {
            jdbc.update("""
                UPDATE app.concession SET status='REJECTED', updated_at=now(), updated_by=:actorId
                 WHERE tenant_id=:tenantId AND concession_id=:concessionId AND status='REQUESTED'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("concessionId", concessionId).addValue("actorId", actorUserId));
        } else {
            Map<String, Object> installment = jdbc.query("""
                SELECT si.institution_id, si.status AS installment_status, fs.academic_session_id,
                       nd.net_due, si.due_date
                  FROM app.student_installment si
                  JOIN app.fee_assignment fa
                    ON fa.tenant_id=si.tenant_id AND fa.fee_assignment_id=si.fee_assignment_id
                  JOIN app.fee_structure fs
                    ON fs.tenant_id=fa.tenant_id AND fs.fee_structure_id=fa.fee_structure_id
                  JOIN app.v_installment_net_due nd
                    ON nd.tenant_id=si.tenant_id AND nd.student_installment_id=si.student_installment_id
                 WHERE si.tenant_id=:tenantId AND si.student_installment_id=:installmentId
                   AND si.student_fee_account_id=:accountId
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("installmentId", concession.get("installmentId"))
                        .addValue("accountId", concession.get("accountId")), rs -> {
                    if (!rs.next()) return null;
                    return Map.of("institutionId", rs.getObject("institution_id", UUID.class),
                            "status", rs.getString("installment_status"),
                            "academicSessionId", rs.getObject("academic_session_id", UUID.class),
                            "netDue", rs.getBigDecimal("net_due"));
                });
            if (installment == null || !"UNPAID".equals(installment.get("status"))) {
                throw new BusinessRuleViolationException("CONCESSION_INSTALLMENT_NOT_OPEN",
                        "The installment is no longer unpaid; the concession cannot be applied.");
            }
            BigDecimal amount = (BigDecimal) concession.get("amount");
            if (amount.compareTo((BigDecimal) installment.get("netDue")) > 0) {
                throw new BusinessRuleViolationException("CONCESSION_EXCEEDS_OUTSTANDING",
                        "The approved concession exceeds the installment's current net due.");
            }
            jdbc.update("""
                INSERT INTO app.ledger_entry
                  (tenant_id, ledger_entry_id, institution_id, student_fee_account_id,
                   academic_session_id, financial_year, posting_date, entry_type, direction,
                   amount, currency_code, fee_head_id, student_installment_id, concession_id,
                   narrative, created_by, updated_by)
                SELECT :tenantId, :ledgerId, :institutionId, :accountId,
                       :sessionId, :financialYear, CURRENT_DATE, 'CONCESSION', 'CREDIT',
                       :amount, 'INR', :feeHeadId, :installmentId, :concessionId,
                       :narrative, :actorId, :actorId
                """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId).addValue("ledgerId", UUID.randomUUID())
                    .addValue("institutionId", installment.get("institutionId"))
                    .addValue("accountId", concession.get("accountId"))
                    .addValue("sessionId", installment.get("academicSessionId"))
                    .addValue("financialYear", LocalDate.now().getMonthValue() >= 4
                            ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1)
                    .addValue("amount", amount).addValue("feeHeadId", concession.get("feeHeadId"))
                    .addValue("installmentId", concession.get("installmentId"))
                    .addValue("concessionId", concessionId)
                    .addValue("narrative", "Approved fee concession " + concessionId)
                    .addValue("actorId", actorUserId));
            jdbc.update("""
                UPDATE app.concession SET status='APPLIED', updated_at=now(), updated_by=:actorId
                 WHERE tenant_id=:tenantId AND concession_id=:concessionId AND status='REQUESTED'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("concessionId", concessionId).addValue("actorId", actorUserId));
        }

        audit.record(AuditEventType.APPROVAL, null, actorUserId.toString(), tenantId.toString(),
                "Fee concession " + concessionId + (approved ? " approved and applied." : " rejected."));
    }
}
