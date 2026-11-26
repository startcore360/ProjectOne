package com.edu360.api.governance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.tenant.TenantContext;

@Service
public class NoDuesVerificationService {
    private final JdbcTemplate jdbc;

    public NoDuesVerificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DuesItem(String source, String code, String description, long itemCount,
            BigDecimal outstandingAmount, boolean blocking) { }

    public record ClearanceResult(UUID studentId, UUID institutionId, boolean clear,
            String status, List<DuesItem> items) {
        public ClearanceResult {
            items = List.copyOf(items);
        }
    }

    @Transactional(readOnly = true)
    public ClearanceResult verifyClearance(UUID studentId) {
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("A tenant context is required for No-Dues verification.");
        }
        Map<String, Object> student = jdbc.query("""
                SELECT student_profile_id, institution_id, user_account_id
                  FROM app.student_profile
                 WHERE tenant_id=? AND student_profile_id=?
                """, rs -> {
            if (!rs.next()) return null;
            return Map.of(
                    "studentId", rs.getObject("student_profile_id", UUID.class),
                    "institutionId", rs.getObject("institution_id", UUID.class),
                    "userAccountId", rs.getObject("user_account_id", UUID.class) == null
                            ? "" : rs.getObject("user_account_id", UUID.class).toString());
        }, tenantId, studentId);
        if (student == null) throw new ResourceNotFoundException("Student not found.");
        UUID institutionId = (UUID) student.get("institutionId");
        String userIdText = (String) student.get("userAccountId");
        UUID userId = userIdText.isBlank() ? null : UUID.fromString(userIdText);
        List<DuesItem> items = new ArrayList<>();

        if (exists("app.v_fee_invoice")) {
            List<Map<String, Object>> fees = jdbc.queryForList("""
                    SELECT count(*)::bigint AS item_count,
                           COALESCE(sum(outstanding_amount),0)::numeric(14,2) AS amount
                      FROM app.v_fee_invoice
                     WHERE tenant_id=? AND student_profile_id=? AND outstanding_amount>0
                    """, tenantId, studentId);
            long count = ((Number) fees.getFirst().get("item_count")).longValue();
            BigDecimal amount = (BigDecimal) fees.getFirst().get("amount");
            if (count > 0) items.add(new DuesItem("FEES", "OUTSTANDING_FEE_INVOICE",
                    "Outstanding fee installments", count, amount, true));
        } else {
            items.add(new DuesItem("FEES", "INTEGRATION_UNAVAILABLE",
                    "The T8 fee invoice read model is unavailable; clearance cannot be confirmed.",
                    0, BigDecimal.ZERO, true));
        }

        if (!exists("app.book_circulation") || !exists("app.library_fine")) {
            items.add(new DuesItem("LIBRARY", "INTEGRATION_UNAVAILABLE",
                    "The T10 circulation/fine records are unavailable; clearance cannot be confirmed.",
                    0, BigDecimal.ZERO, true));
        } else if (userId == null) {
            items.add(new DuesItem("LIBRARY", "LIBRARY_ACCOUNT_UNLINKED",
                    "The student has no linked library borrower account; clearance cannot be confirmed.",
                    0, BigDecimal.ZERO, true));
        } else {
            Long unreturned = jdbc.queryForObject("""
                    SELECT count(*) FROM app.book_circulation
                     WHERE tenant_id=? AND institution_id=? AND borrower_user_id=?
                       AND returned_at IS NULL AND status IN ('ISSUED','OVERDUE','LOST')
                    """, Long.class, tenantId, institutionId, userId);
            BigDecimal fines = jdbc.queryForObject("""
                    SELECT COALESCE(sum(GREATEST(amount-amount_paid,0)),0)::numeric(14,2)
                      FROM app.library_fine
                     WHERE tenant_id=? AND institution_id=? AND status='OUTSTANDING'
                       AND amount>amount_paid
                       AND circulation_id IN (
                         SELECT id FROM app.book_circulation
                          WHERE tenant_id=? AND institution_id=? AND borrower_user_id=?
                       )
                    """, BigDecimal.class, tenantId, institutionId, tenantId, institutionId, userId);
            if (unreturned != null && unreturned > 0) {
                items.add(new DuesItem("LIBRARY", "UNRETURNED_BOOKS",
                        "Books or copies have not been returned", unreturned, BigDecimal.ZERO, true));
            }
            if (fines != null && fines.signum() > 0) {
                items.add(new DuesItem("LIBRARY", "OUTSTANDING_LIBRARY_FINE",
                        "Outstanding library fines", 1, fines, true));
            }
        }

        if (!exists("app.department_equipment_clearance")) {
            items.add(new DuesItem("DEPARTMENT", "INTEGRATION_UNAVAILABLE",
                    "Departmental equipment clearance is not configured; clearance cannot be confirmed.",
                    0, BigDecimal.ZERO, true));
        } else {
            Map<String, Object> departmentCheck = jdbc.queryForMap(
                    "SELECT * FROM app.fn_no_dues_department_clearance(?,?,?)",
                    tenantId, institutionId, studentId);
            long pendingDepartments = ((Number) departmentCheck.get("pending_departments")).longValue();
            long outstandingItems = ((Number) departmentCheck.get("outstanding_items")).longValue();
            if (pendingDepartments > 0) {
                items.add(new DuesItem("DEPARTMENT", "EQUIPMENT_NOT_CLEARED",
                        "One or more active departments have missing clearance or outstanding equipment.",
                        pendingDepartments, BigDecimal.valueOf(outstandingItems), true));
            }
        }

        boolean clear = items.stream().noneMatch(DuesItem::blocking);
        return new ClearanceResult(studentId, institutionId, clear,
                clear ? "CLEAR" : "NO_DUES_PENDING", items);
    }

    private boolean exists(String qualifiedName) {
        Boolean result = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, qualifiedName);
        return Boolean.TRUE.equals(result);
    }
}
