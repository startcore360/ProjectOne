package com.edu360.api.finance;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfflinePaymentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService audit;

    @Transactional
    public Map<String, Object> record(UUID tenantId, TenantPrincipal actor, OfflinePaymentRequest request) {
        validateRequest(request);
        List<UUID> installmentIds = request.installmentIds().stream().sorted().toList();

        Map<String, Object> existing = jdbc.query("""
            SELECT payment_id, institution_id, student_fee_account_id, method, amount, status,
                   cheque_number, cheque_date, cheque_bank
              FROM app.payment
             WHERE tenant_id=:tenantId AND request_key=:requestKey
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("requestKey", request.requestKey()), rs -> {
                if (!rs.next()) return null;
                Map<String, Object> row = new HashMap<>();
                row.put("paymentId", rs.getObject("payment_id", UUID.class));
                row.put("institutionId", rs.getObject("institution_id", UUID.class));
                row.put("accountId", rs.getObject("student_fee_account_id", UUID.class));
                row.put("method", rs.getString("method"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("status", rs.getString("status"));
                row.put("chequeNumber", rs.getString("cheque_number"));
                row.put("chequeDate", rs.getDate("cheque_date") == null ? null : rs.getDate("cheque_date").toLocalDate());
                row.put("chequeBank", rs.getString("cheque_bank"));
                return row;
            });
        if (existing != null) {
            UUID existingInstitutionId = (UUID) existing.get("institutionId");
            permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                    "PAYMENT_OFFLINE_RECORD", RbacScope.institution(existingInstitutionId), RbacTarget.NONE);
            return replayOrReject(tenantId, request, installmentIds, existingInstitutionId, existing);
        }

        String accountStatus = jdbc.query("""
            SELECT status FROM app.student_fee_account
             WHERE tenant_id=:tenantId AND student_fee_account_id=:accountId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("accountId", request.studentFeeAccountId()),
                rs -> rs.next() ? rs.getString(1) : null);
        if (!"ACTIVE".equals(accountStatus)) {
            throw new BusinessRuleViolationException("STUDENT_FEE_ACCOUNT_NOT_ACTIVE",
                    "Only an active student fee account can receive an offline payment.");
        }

        List<Installment> installments = jdbc.query("""
            SELECT si.student_installment_id, si.institution_id, si.student_fee_account_id,
                   fs.academic_session_id, nd.net_due, si.status
              FROM app.student_installment si
              JOIN app.fee_assignment fa
                ON fa.tenant_id=si.tenant_id AND fa.fee_assignment_id=si.fee_assignment_id
              JOIN app.fee_structure fs
                ON fs.tenant_id=fa.tenant_id AND fs.fee_structure_id=fa.fee_structure_id
              JOIN app.v_installment_net_due nd
                ON nd.tenant_id=si.tenant_id AND nd.student_installment_id=si.student_installment_id
             WHERE si.tenant_id=:tenantId
               AND si.student_fee_account_id=:accountId
               AND si.student_installment_id IN (:installmentIds)
             ORDER BY si.due_date, si.installment_no
             FOR UPDATE OF si
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("accountId", request.studentFeeAccountId())
                    .addValue("installmentIds", installmentIds),
                (rs, rowNum) -> new Installment(
                        rs.getObject("student_installment_id", UUID.class),
                        rs.getObject("institution_id", UUID.class),
                        rs.getObject("student_fee_account_id", UUID.class),
                        rs.getObject("academic_session_id", UUID.class),
                        rs.getBigDecimal("net_due"),
                        rs.getString("status")));
        if (installments.size() != installmentIds.size()) {
            throw new BusinessRuleViolationException("PAYMENT_INSTALLMENT_NOT_FOUND",
                    "Every selected installment must belong to the supplied student fee account.");
        }
        if (installments.stream().anyMatch(i -> !"UNPAID".equals(i.status()) || i.netDue().signum() <= 0)) {
            throw new BusinessRuleViolationException("PAYMENT_INSTALLMENT_NOT_PAYABLE",
                    "Only unpaid installments with a positive net due can be paid.");
        }
        UUID institutionId = installments.get(0).institutionId();
        if (installments.stream().anyMatch(i -> !institutionId.equals(i.institutionId()))) {
            throw new BusinessRuleViolationException("PAYMENT_CROSS_INSTITUTION_NOT_ALLOWED",
                    "A single offline payment cannot combine installments from different institutions.");
        }
        BigDecimal total = installments.stream().map(Installment::netDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.amount().compareTo(total) != 0) {
            throw new BusinessRuleViolationException("PARTIAL_PAYMENT_NOT_ALLOWED",
                    "Offline payment must equal the exact total net due of the selected full installments.");
        }

        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                "PAYMENT_OFFLINE_RECORD", RbacScope.institution(institutionId), RbacTarget.NONE);

        UUID paymentId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        short financialYear = (short) (today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1);
        jdbc.update("""
            INSERT INTO app.payment
              (tenant_id, payment_id, institution_id, student_fee_account_id, method,
               quoted_amount, amount, status, request_key, confirmed_via, marked_by_user_id,
               cheque_number, cheque_date, cheque_bank, allocated_amount, credit_added_amount,
               amount_mismatch, paid_at, created_by, updated_by)
            VALUES
              (:tenantId, :paymentId, :institutionId, :accountId, :method,
               :amount, :amount, 'PAID', :requestKey, 'OFFLINE_ENTRY', :actorId,
               :chequeNumber, :chequeDate, :chequeBank, :amount, 0,
               false, now(), :actorId, :actorId)
            ON CONFLICT (tenant_id, request_key) DO NOTHING
            """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("paymentId", paymentId)
                .addValue("institutionId", institutionId).addValue("accountId", request.studentFeeAccountId())
                .addValue("method", request.method()).addValue("amount", total)
                .addValue("requestKey", request.requestKey()).addValue("actorId", actor.userAccountId())
                .addValue("chequeNumber", request.chequeNumber()).addValue("chequeDate", request.chequeDate())
                .addValue("chequeBank", request.chequeBank()));

        UUID persistedPaymentId = jdbc.query("""
            SELECT payment_id FROM app.payment
             WHERE tenant_id=:tenantId AND request_key=:requestKey
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("requestKey", request.requestKey()),
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (persistedPaymentId == null) throw new IllegalStateException("Payment idempotency row could not be resolved.");
        if (!persistedPaymentId.equals(paymentId)) {
            Map<String, Object> raced = jdbc.query("""
                SELECT payment_id, institution_id, student_fee_account_id, method, amount, status,
                       cheque_number, cheque_date, cheque_bank
                  FROM app.payment WHERE tenant_id=:tenantId AND request_key=:requestKey
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("requestKey", request.requestKey()), rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new HashMap<>();
                    row.put("paymentId", rs.getObject("payment_id", UUID.class));
                    row.put("institutionId", rs.getObject("institution_id", UUID.class));
                    row.put("accountId", rs.getObject("student_fee_account_id", UUID.class));
                    row.put("method", rs.getString("method"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("status", rs.getString("status"));
                    row.put("chequeNumber", rs.getString("cheque_number"));
                    row.put("chequeDate", rs.getDate("cheque_date") == null ? null : rs.getDate("cheque_date").toLocalDate());
                    row.put("chequeBank", rs.getString("cheque_bank"));
                    return row;
                });
            return replayOrReject(tenantId, request, installmentIds, institutionId, raced);
        }

        for (Installment installment : installments) {
            jdbc.update("""
                INSERT INTO app.payment_intent_item
                  (tenant_id, payment_intent_item_id, institution_id, payment_id,
                   student_installment_id, quoted_net_due, created_by, updated_by)
                VALUES
                  (:tenantId, :itemId, :institutionId, :paymentId,
                   :installmentId, :netDue, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("itemId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("paymentId", paymentId).addValue("installmentId", installment.installmentId())
                    .addValue("netDue", installment.netDue()).addValue("actorId", actor.userAccountId()));

            jdbc.update("""
                INSERT INTO app.payment_allocation
                  (tenant_id, payment_allocation_id, institution_id, payment_id,
                   student_installment_id, allocated_amount, created_by, updated_by)
                VALUES
                  (:tenantId, :allocationId, :institutionId, :paymentId,
                   :installmentId, :amount, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("allocationId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("paymentId", paymentId).addValue("installmentId", installment.installmentId())
                    .addValue("amount", installment.netDue()).addValue("actorId", actor.userAccountId()));
        }

        jdbc.update("""
            UPDATE app.student_installment
               SET status='PAID', settled_by='PAYMENT', paid_at=now(), updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND student_installment_id IN (:installmentIds) AND status='UNPAID'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("installmentIds", installmentIds).addValue("actorId", actor.userAccountId()));

        Map<UUID, BigDecimal> bySession = new HashMap<>();
        installments.forEach(i -> bySession.merge(i.academicSessionId(), i.netDue(), BigDecimal::add));
        for (Map.Entry<UUID, BigDecimal> entry : bySession.entrySet()) {
            jdbc.update("""
                INSERT INTO app.ledger_entry
                  (tenant_id, ledger_entry_id, institution_id, student_fee_account_id,
                   academic_session_id, financial_year, posting_date, entry_type, direction,
                   amount, currency_code, payment_id, narrative, created_by, updated_by)
                VALUES
                  (:tenantId, :ledgerId, :institutionId, :accountId,
                   :sessionId, :financialYear, :postingDate, 'PAYMENT', 'CREDIT',
                   :amount, 'INR', :paymentId, :narrative, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("ledgerId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("accountId", request.studentFeeAccountId()).addValue("sessionId", entry.getKey())
                    .addValue("financialYear", financialYear).addValue("postingDate", today)
                    .addValue("amount", entry.getValue()).addValue("paymentId", paymentId)
                    .addValue("narrative", "Offline " + request.method() + " payment " + paymentId)
                    .addValue("actorId", actor.userAccountId()));
        }

        String prefix = jdbc.query("""
            SELECT prefix FROM app.receipt_sequence
             WHERE tenant_id=:tenantId AND institution_id=:institutionId AND financial_year=:financialYear
             FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("institutionId", institutionId).addValue("financialYear", financialYear),
                rs -> rs.next() ? rs.getString(1) : null);
        if (prefix == null) {
            throw new BusinessRuleViolationException("RECEIPT_SEQUENCE_MISSING",
                    "Receipt sequence is not configured for this institution and financial year.");
        }
        Integer receiptNumber = jdbc.queryForObject("SELECT app.next_receipt_number(:tenantId,:institutionId,:financialYear)",
                new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("institutionId", institutionId).addValue("financialYear", financialYear),
                Integer.class);
        UUID receiptId = UUID.randomUUID();
        String receiptLabel = prefix + "-" + financialYear + "-" + String.format(Locale.ROOT, "%06d", receiptNumber);
        jdbc.update("""
            INSERT INTO app.receipt
              (tenant_id, receipt_id, institution_id, payment_id, financial_year,
               receipt_number, receipt_label, amount, status, created_by, updated_by)
            VALUES
              (:tenantId, :receiptId, :institutionId, :paymentId, :financialYear,
               :receiptNumber, :receiptLabel, :amount, 'ISSUED', :actorId, :actorId)
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("receiptId", receiptId)
                .addValue("institutionId", institutionId).addValue("paymentId", paymentId)
                .addValue("financialYear", financialYear).addValue("receiptNumber", receiptNumber)
                .addValue("receiptLabel", receiptLabel).addValue("amount", total)
                .addValue("actorId", actor.userAccountId()));

        audit.record(AuditEventType.CREATE, null, actor.userAccountId().toString(), tenantId.toString(),
                "Offline " + request.method() + " payment " + paymentId + " recorded; receipt " + receiptLabel + ".");
        return Map.of("paymentId", paymentId, "receiptId", receiptId, "receiptLabel", receiptLabel,
                "amount", total, "status", "PAID");
    }

    private Map<String, Object> replayOrReject(UUID tenantId, OfflinePaymentRequest request,
            List<UUID> installmentIds, UUID institutionId, Map<String, Object> existing) {
        boolean same = "PAID".equals(existing.get("status"))
                && institutionId.equals(existing.get("institutionId"))
                && request.studentFeeAccountId().equals(existing.get("accountId"))
                && request.method().equals(existing.get("method"))
                && request.amount().compareTo((BigDecimal) existing.get("amount")) == 0
                && java.util.Objects.equals(request.chequeNumber(), existing.get("chequeNumber"))
                && java.util.Objects.equals(request.chequeDate(), existing.get("chequeDate"))
                && java.util.Objects.equals(request.chequeBank(), existing.get("chequeBank"));
        List<UUID> existingItems = jdbc.query("""
            SELECT student_installment_id FROM app.payment_intent_item
             WHERE tenant_id=:tenantId AND payment_id=:paymentId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("paymentId", existing.get("paymentId")),
                (rs, rowNum) -> rs.getObject(1, UUID.class)).stream().sorted().toList();
        if (!same || !installmentIds.equals(existingItems)) {
            throw new BusinessRuleViolationException("IDEMPOTENCY_KEY_REUSED",
                    "The request key has already been used for a different offline payment.");
        }
        Map<String, Object> receipt = jdbc.query("""
            SELECT receipt_id, receipt_label, amount
              FROM app.receipt
             WHERE tenant_id=:tenantId AND payment_id=:paymentId AND status='ISSUED'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("paymentId", existing.get("paymentId")), rs -> {
                if (!rs.next()) return null;
                return Map.of("receiptId", rs.getObject("receipt_id", UUID.class),
                        "receiptLabel", rs.getString("receipt_label"), "amount", rs.getBigDecimal("amount"));
            });
        if (receipt == null) throw new IllegalStateException("Paid offline payment has no issued receipt.");
        return Map.of("paymentId", existing.get("paymentId"), "receiptId", receipt.get("receiptId"),
                "receiptLabel", receipt.get("receiptLabel"), "amount", receipt.get("amount"), "status", "PAID");
    }

    private static void validateRequest(OfflinePaymentRequest request) {
        if (request == null || request.studentFeeAccountId() == null || request.installmentIds() == null
                || request.installmentIds().isEmpty() || request.amount() == null
                || request.amount().signum() <= 0 || request.requestKey() == null
                || request.requestKey().length() < 8 || request.requestKey().length() > 100
                || new HashSet<>(request.installmentIds()).size() != request.installmentIds().size()) {
            throw new BusinessRuleViolationException("OFFLINE_PAYMENT_REQUEST_INVALID",
                    "Fee account, distinct installments, positive amount and an 8–100 character request key are required.");
        }
        if (!"CASH".equals(request.method()) && !"CHEQUE".equals(request.method())) {
            throw new BusinessRuleViolationException("OFFLINE_PAYMENT_METHOD_INVALID",
                    "Offline payments support CASH or CHEQUE only.");
        }
        if ("CHEQUE".equals(request.method())
                && (request.chequeNumber() == null || request.chequeNumber().isBlank()
                    || request.chequeDate() == null || request.chequeBank() == null || request.chequeBank().isBlank())) {
            throw new BusinessRuleViolationException("CHEQUE_DETAILS_REQUIRED",
                    "Cheque number, date and bank are required for cheque payments.");
        }
        if ("CASH".equals(request.method())
                && (request.chequeNumber() != null || request.chequeDate() != null || request.chequeBank() != null)) {
            throw new BusinessRuleViolationException("CASH_CHEQUE_FIELDS_NOT_ALLOWED",
                    "Cheque details must be omitted for cash payments.");
        }
    }

    private record Installment(UUID installmentId, UUID institutionId, UUID accountId,
            UUID academicSessionId, BigDecimal netDue, String status) {}

    public record OfflinePaymentRequest(UUID studentFeeAccountId, List<UUID> installmentIds,
            String method, BigDecimal amount, String requestKey, String chequeNumber,
            LocalDate chequeDate, String chequeBank) {}
}
