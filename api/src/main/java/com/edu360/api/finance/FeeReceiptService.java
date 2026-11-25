package com.edu360.api.finance;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeeReceiptService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    @Transactional(readOnly = true)
    public Map<String, Object> getReceipt(UUID tenantId, TenantPrincipal actor, UUID paymentId) {
        Map<String, Object> receipt = jdbc.query("""
            SELECT r.receipt_label AS "receiptLabel", r.amount,
                   p.payment_id AS "paymentId", p.method,
                   p.paid_at AS "paidAt", sfa.student_profile_id AS "studentProfileId",
                   sp.full_name AS "studentName", i.institution_id AS "institutionId",
                   i.institution_name AS "institutionName",
                   COALESCE(go.provider, p.method) AS "paymentMethod"
              FROM app.receipt r
              JOIN app.payment p
                ON p.tenant_id=r.tenant_id AND p.payment_id=r.payment_id
              JOIN app.student_fee_account sfa
                ON sfa.tenant_id=p.tenant_id AND sfa.student_fee_account_id=p.student_fee_account_id
              JOIN app.student_profile sp
                ON sp.tenant_id=sfa.tenant_id AND sp.student_profile_id=sfa.student_profile_id
              JOIN app.institution i
                ON i.tenant_id=r.tenant_id AND i.institution_id=r.institution_id
              LEFT JOIN app.gateway_order go
                ON go.tenant_id=p.tenant_id AND go.payment_id=p.payment_id
             WHERE r.tenant_id=:tenantId AND r.payment_id=:paymentId
               AND r.status='ISSUED' AND p.status='PAID'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId), rs -> {
                if (!rs.next()) return null;
                return Map.of(
                        "receiptLabel", rs.getString("receiptLabel"),
                        "paymentId", rs.getObject("paymentId", UUID.class),
                        "studentProfileId", rs.getObject("studentProfileId", UUID.class),
                        "studentName", rs.getString("studentName"),
                        "institutionId", rs.getObject("institutionId", UUID.class),
                        "institutionName", rs.getString("institutionName"),
                        "paidAt", rs.getTimestamp("paidAt").toInstant().toString(),
                        "paymentMethod", rs.getString("paymentMethod"),
                        "amount", rs.getBigDecimal("amount"),
                        "currency", "INR");
            });
        if (receipt == null) throw new ResourceNotFoundException("Issued receipt not found for this payment.");

        UUID institutionId = (UUID) receipt.get("institutionId");
        UUID studentProfileId = (UUID) receipt.get("studentProfileId");
        Boolean owner = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM app.student_profile sp
                 WHERE sp.tenant_id=:tenantId AND sp.student_profile_id=:studentProfileId
                   AND sp.user_account_id=:actorId
                UNION ALL
                SELECT 1 FROM app.student_guardian_link sgl
                JOIN app.guardian_profile gp
                  ON gp.tenant_id=sgl.tenant_id AND gp.guardian_profile_id=sgl.guardian_profile_id
                 WHERE sgl.tenant_id=:tenantId AND sgl.student_profile_id=:studentProfileId
                   AND sgl.status='ACTIVE' AND sgl.is_primary=true AND gp.user_account_id=:actorId
            )
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("studentProfileId", studentProfileId).addValue("actorId", actor.userAccountId()),
                Boolean.class);
        RbacPrincipal principal = new RbacPrincipal(tenantId, actor.userAccountId());
        if (Boolean.TRUE.equals(owner)) {
            permissions.authorize(principal, "FEE_VIEW_OWN", permissions.ownScope(principal), RbacTarget.NONE);
        } else {
            permissions.authorize(principal, "LEDGER_VIEW", RbacScope.institution(institutionId), RbacTarget.NONE);
        }

        List<Map<String, Object>> allocations = jdbc.queryForList("""
            SELECT si.installment_no AS "installmentNumber", si.due_date AS "dueDate",
                   pa.allocated_amount AS amount
              FROM app.payment_allocation pa
              JOIN app.student_installment si
                ON si.tenant_id=pa.tenant_id AND si.student_installment_id=pa.student_installment_id
             WHERE pa.tenant_id=:tenantId AND pa.payment_id=:paymentId
             ORDER BY si.due_date, si.installment_no
            """, Map.of("tenantId", tenantId, "paymentId", paymentId));
        Map<String, Object> result = new java.util.HashMap<>(receipt);
        result.remove("studentProfileId");
        result.remove("institutionId");
        result.put("allocations", allocations);
        return result;
    }
}
