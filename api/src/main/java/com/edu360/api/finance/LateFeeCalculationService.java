package com.edu360.api.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies PO-T03 late fees as append-only ledger debits; repeated runs are idempotent.
 * PERCENTAGE_MONTHLY accrues once per crossed calendar-month boundary after grace.
 */
@Service
@RequiredArgsConstructor
public class LateFeeCalculationService {
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public int calculateForTenant(LocalDate asOfDate, UUID systemActorId) {
        if (asOfDate == null || systemActorId == null) {
            throw new IllegalArgumentException("As-of date and system actor are required.");
        }
        Integer inserted = jdbc.queryForObject("""
            WITH candidates AS (
                SELECT si.tenant_id,
                       si.institution_id,
                       si.student_fee_account_id,
                       si.student_installment_id,
                       si.due_date,
                       si.billed_amount,
                       fs.academic_session_id,
                       rule.grace_days,
                       rule.calculation_mode,
                       rule.amount_or_percent,
                       rule.cap_amount,
                       GREATEST(:asOfDate - si.due_date - rule.grace_days, 0)::numeric AS days_after_grace,
                       GREATEST(
                           (EXTRACT(YEAR FROM :asOfDate)::int - EXTRACT(YEAR FROM (si.due_date + rule.grace_days))::int) * 12
                           + EXTRACT(MONTH FROM :asOfDate)::int
                           - EXTRACT(MONTH FROM (si.due_date + rule.grace_days))::int,
                           0
                       )::numeric AS calendar_months_after_grace,
                       GREATEST(
                           si.billed_amount
                           - COALESCE(discounts.discount_total, 0),
                           0
                       )::numeric AS base_due,
                       COALESCE(fines.current_fines, 0)::numeric AS current_fines
                  FROM app.student_installment si
                  JOIN app.fee_assignment fa
                    ON fa.tenant_id=si.tenant_id AND fa.fee_assignment_id=si.fee_assignment_id
                  JOIN app.fee_structure fs
                    ON fs.tenant_id=fa.tenant_id AND fs.fee_structure_id=fa.fee_structure_id
                  JOIN LATERAL (
                      SELECT r.grace_days, r.calculation_mode, r.amount_or_percent, r.cap_amount
                        FROM app.late_fee_rule r
                       WHERE r.tenant_id=si.tenant_id
                         AND r.institution_id=si.institution_id
                         AND r.status='ACTIVE'
                         AND r.effective_from <= :asOfDate
                       ORDER BY r.effective_from DESC, r.created_at DESC
                       LIMIT 1
                  ) rule ON true
                  LEFT JOIN LATERAL (
                      SELECT SUM(l.amount) AS discount_total
                        FROM app.ledger_entry l
                       WHERE l.tenant_id=si.tenant_id
                         AND l.student_installment_id=si.student_installment_id
                         AND l.entry_type IN ('SCHOLARSHIP','CONCESSION')
                  ) discounts ON true
                  LEFT JOIN LATERAL (
                      SELECT SUM(l.amount) AS current_fines
                        FROM app.ledger_entry l
                       WHERE l.tenant_id=si.tenant_id
                         AND l.student_installment_id=si.student_installment_id
                         AND l.entry_type='LATE_FEE'
                  ) fines ON true
                 WHERE si.status='UNPAID'
                   AND si.due_date + rule.grace_days < :asOfDate
                   AND rule.calculation_mode <> 'NONE'
                   AND NOT app.is_period_locked(si.tenant_id, si.institution_id, :asOfDate)
                 FOR UPDATE OF si SKIP LOCKED
            ),
            targets AS (
                SELECT c.*,
                       LEAST(
                           CASE c.calculation_mode
                             WHEN 'FLAT_DAILY' THEN c.days_after_grace * c.amount_or_percent
                             WHEN 'PERCENTAGE_MONTHLY' THEN
                               c.calendar_months_after_grace * c.base_due * c.amount_or_percent / 100
                             ELSE 0
                           END,
                           COALESCE(c.cap_amount,
                               CASE c.calculation_mode
                                 WHEN 'FLAT_DAILY' THEN c.days_after_grace * c.amount_or_percent
                                 WHEN 'PERCENTAGE_MONTHLY' THEN
                                   c.calendar_months_after_grace * c.base_due * c.amount_or_percent / 100
                                 ELSE 0
                               END)
                       ) AS target_fines
                  FROM candidates c
            ),
            posted AS (
                INSERT INTO app.ledger_entry
                    (tenant_id, ledger_entry_id, institution_id, student_fee_account_id,
                     academic_session_id, financial_year, posting_date, entry_type, direction,
                     amount, currency_code, student_installment_id, narrative, created_by, updated_by)
                SELECT t.tenant_id, gen_random_uuid(), t.institution_id, t.student_fee_account_id,
                       t.academic_session_id,
                       CASE WHEN EXTRACT(MONTH FROM :asOfDate) >= 4
                            THEN EXTRACT(YEAR FROM :asOfDate)::smallint
                            ELSE (EXTRACT(YEAR FROM :asOfDate)-1)::smallint END,
                       :asOfDate, 'LATE_FEE', 'DEBIT',
                       ROUND(t.target_fines - t.current_fines, 2), 'INR',
                       t.student_installment_id,
                       'Automated PO-T03 late fee accrued through ' || :asOfDate::text,
                       :systemActorId, :systemActorId
                  FROM targets t
                 WHERE ROUND(t.target_fines - t.current_fines, 2) > 0
                ON CONFLICT DO NOTHING
                RETURNING ledger_entry_id
            )
            SELECT COUNT(*) FROM posted
            """, new MapSqlParameterSource()
                .addValue("asOfDate", asOfDate)
                .addValue("systemActorId", systemActorId), Integer.class);
        return inserted == null ? 0 : inserted;
    }
}
