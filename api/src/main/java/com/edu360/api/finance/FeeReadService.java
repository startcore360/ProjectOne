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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeReadService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    public FeeReadService(NamedParameterJdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFeeHeads(UUID tenantId, TenantPrincipal actor, UUID institutionId) {
        authorize(actor, tenantId, institutionId);
        String sql = """
            SELECT fee_head_id AS "feeHeadId", fee_head_code AS "code",
                   fee_head_name AS "name", category, status, row_version AS "rowVersion"
              FROM app.fee_head
             WHERE tenant_id=:tenantId
             ORDER BY fee_head_code
            """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("tenantId", tenantId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFeeHead(UUID tenantId, TenantPrincipal actor, UUID institutionId, UUID feeHeadId) {
        authorize(actor, tenantId, institutionId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT fee_head_id AS "feeHeadId", fee_head_code AS "code",
                   fee_head_name AS "name", category, status, row_version AS "rowVersion"
              FROM app.fee_head
             WHERE tenant_id=:tenantId AND fee_head_id=:feeHeadId
            """, Map.of("tenantId", tenantId, "feeHeadId", feeHeadId));
        if (rows.isEmpty()) throw new ResourceNotFoundException("Fee head not found.");
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFeeStructures(UUID tenantId, TenantPrincipal actor, UUID institutionId) {
        authorize(actor, tenantId, institutionId);
        return jdbc.queryForList("""
            SELECT fs.fee_structure_id AS "feeStructureId", fs.institution_id AS "institutionId",
                   fs.academic_session_id AS "academicSessionId", fs.program_year_id AS "programYearId",
                   fs.class_id AS "classId", fs.fee_policy_id AS "feePolicyId", fs.version_no AS "versionNo",
                   fs.status, fs.applies_from_date AS "appliesFromDate", fs.row_version AS "rowVersion",
                   COALESCE(lines.total_amount, 0) AS "totalAmount",
                   COALESCE(inst.installment_count, 0) AS "installmentCount"
              FROM app.fee_structure fs
              LEFT JOIN LATERAL (
                SELECT SUM(l.amount) AS total_amount FROM app.fee_structure_line l
                 WHERE l.tenant_id=fs.tenant_id AND l.fee_structure_id=fs.fee_structure_id
              ) lines ON true
              LEFT JOIN LATERAL (
                SELECT COUNT(*) AS installment_count FROM app.fee_structure_installment i
                 WHERE i.tenant_id=fs.tenant_id AND i.fee_structure_id=fs.fee_structure_id
              ) inst ON true
             WHERE fs.tenant_id=:tenantId
               AND (:institutionId IS NULL OR fs.institution_id=:institutionId)
             ORDER BY fs.institution_id, fs.created_at DESC, fs.version_no DESC
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("institutionId", institutionId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentLedger(UUID tenantId, TenantPrincipal actor, UUID accountId) {
        Map<String, Object> account = jdbc.query("""
            SELECT sfa.student_fee_account_id, sp.institution_id, sfa.student_profile_id
              FROM app.student_fee_account sfa
              JOIN app.student_profile sp
                ON sp.tenant_id=sfa.tenant_id AND sp.student_profile_id=sfa.student_profile_id
             WHERE sfa.tenant_id=:tenantId AND sfa.student_fee_account_id=:accountId
            """, Map.of("tenantId", tenantId, "accountId", accountId), rs -> {
                if (!rs.next()) return null;
                return Map.of(
                    "accountId", rs.getObject("student_fee_account_id", UUID.class),
                    "institutionId", rs.getObject("institution_id", UUID.class),
                    "studentProfileId", rs.getObject("student_profile_id", UUID.class));
            });
        if (account == null) throw new ResourceNotFoundException("Student fee account not found.");
        authorizeFeeAccount(actor, tenantId, (UUID) account.get("institutionId"), (UUID) account.get("studentProfileId"));

        List<Map<String, Object>> entries = jdbc.queryForList("""
            SELECT ledger_entry_id AS "ledgerEntryId", posting_date AS "postingDate",
                   entry_type AS "entryType", direction, amount, currency_code AS "currencyCode",
                   fee_head_id AS "feeHeadId", student_installment_id AS "studentInstallmentId",
                   payment_id AS "paymentId", narrative, created_at AS "createdAt"
              FROM app.ledger_entry
             WHERE tenant_id=:tenantId AND student_fee_account_id=:accountId
             ORDER BY posting_date, created_at, ledger_entry_id
            """, Map.of("tenantId", tenantId, "accountId", accountId));
        Map<String, Object> balance = jdbc.query("""
            SELECT signed_balance AS "signedBalance", credit_balance AS "creditBalance"
              FROM app.v_student_fee_balance
             WHERE tenant_id=:tenantId AND student_fee_account_id=:accountId
            """, Map.of("tenantId", tenantId, "accountId", accountId), rs -> {
                if (!rs.next()) return Map.of("signedBalance", 0, "creditBalance", 0);
                return Map.of("signedBalance", rs.getBigDecimal("signedBalance"),
                              "creditBalance", rs.getBigDecimal("creditBalance"));
            });
        return Map.of("account", account, "balance", balance, "entries", entries);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMyFeeAccounts(UUID tenantId, TenantPrincipal actor) {
        List<Map<String, Object>> accounts = jdbc.queryForList("""
            SELECT sfa.student_fee_account_id AS "accountId",
                   sp.student_profile_id AS "studentProfileId",
                   sp.institution_id AS "institutionId",
                   sp.full_name AS "studentName",
                   i.institution_name AS "institutionName"
              FROM app.student_fee_account sfa
              JOIN app.student_profile sp
                ON sp.tenant_id=sfa.tenant_id AND sp.student_profile_id=sfa.student_profile_id
              JOIN app.institution i
                ON i.tenant_id=sp.tenant_id AND i.institution_id=sp.institution_id
             WHERE sfa.tenant_id=:tenantId
               AND sfa.status='ACTIVE'
               AND (
                   sp.user_account_id=:actorId
                   OR EXISTS (
                       SELECT 1
                         FROM app.student_guardian_link sgl
                         JOIN app.guardian_profile gp
                           ON gp.tenant_id=sgl.tenant_id AND gp.guardian_profile_id=sgl.guardian_profile_id
                        WHERE sgl.tenant_id=sfa.tenant_id
                          AND sgl.student_profile_id=sp.student_profile_id
                          AND sgl.status='ACTIVE' AND sgl.is_primary=true
                          AND gp.user_account_id=:actorId
                   )
               )
             ORDER BY sp.full_name
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("actorId", actor.userAccountId()));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> account : accounts) {
            UUID institutionId = (UUID) account.get("institutionId");
            RbacPrincipal principal = new RbacPrincipal(tenantId, actor.userAccountId());
            permissions.authorize(principal, "FEE_VIEW_OWN", permissions.ownScope(principal), RbacTarget.NONE);
            Map<String, Object> safe = new java.util.HashMap<>(account);
            safe.remove("studentProfileId");
            safe.remove("institutionId");
            result.add(safe);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInvoices(UUID tenantId, TenantPrincipal actor, UUID accountId) {
        Map<String, Object> account = jdbc.query("""
            SELECT sp.institution_id, sp.student_profile_id
              FROM app.student_fee_account sfa
              JOIN app.student_profile sp
                ON sp.tenant_id=sfa.tenant_id AND sp.student_profile_id=sfa.student_profile_id
             WHERE sfa.tenant_id=:tenantId AND sfa.student_fee_account_id=:accountId
            """, Map.of("tenantId", tenantId, "accountId", accountId), rs ->
                rs.next() ? Map.of("institutionId", rs.getObject("institution_id", UUID.class),
                        "studentProfileId", rs.getObject("student_profile_id", UUID.class)) : null);
        if (account == null) throw new ResourceNotFoundException("Student fee account not found.");
        authorizeFeeAccount(actor, tenantId, (UUID) account.get("institutionId"), (UUID) account.get("studentProfileId"));
        return jdbc.queryForList("""
            SELECT invoice_id AS "invoiceId", student_profile_id AS "studentProfileId",
                   installment_no AS "installmentNumber", due_date AS "dueDate",
                   base_amount AS "baseAmount", concession_amount AS "concessionAmount",
                   scholarship_amount AS "scholarshipAmount", late_fee_amount AS "lateFeeAmount",
                   paid_amount AS "paidAmount", net_due AS "netDue",
                   outstanding_amount AS "outstandingAmount", status
              FROM app.v_fee_invoice
             WHERE tenant_id=:tenantId AND student_fee_account_id=:accountId
             ORDER BY due_date, installment_no
            """, Map.of("tenantId", tenantId, "accountId", accountId));
    }

    private void authorizeFeeAccount(TenantPrincipal actor, UUID tenantId,
            UUID institutionId, UUID studentProfileId) {
        Boolean owner = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM app.student_profile sp
                 WHERE sp.tenant_id=:tenantId
                   AND sp.student_profile_id=:studentProfileId
                   AND sp.user_account_id=:actorId
                UNION ALL
                SELECT 1 FROM app.student_guardian_link sgl
                JOIN app.guardian_profile gp
                  ON gp.tenant_id=sgl.tenant_id AND gp.guardian_profile_id=sgl.guardian_profile_id
                 WHERE sgl.tenant_id=:tenantId
                   AND sgl.student_profile_id=:studentProfileId
                   AND sgl.status='ACTIVE' AND sgl.is_primary=true
                   AND gp.user_account_id=:actorId
            )
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("studentProfileId", studentProfileId)
                    .addValue("actorId", actor.userAccountId()), Boolean.class);
        RbacPrincipal principal = new RbacPrincipal(tenantId, actor.userAccountId());
        if (Boolean.TRUE.equals(owner)) {
            permissions.authorize(principal, "FEE_VIEW_OWN", permissions.ownScope(principal), RbacTarget.NONE);
        } else {
            permissions.authorize(principal, "LEDGER_VIEW",
                    institutionId == null ? RbacScope.tenant() : RbacScope.institution(institutionId),
                    RbacTarget.NONE);
        }
    }

    private void authorize(TenantPrincipal actor, UUID tenantId, UUID institutionId) {
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                "LEDGER_VIEW", institutionId == null ? RbacScope.tenant() : RbacScope.institution(institutionId),
                RbacTarget.NONE);
    }
}
