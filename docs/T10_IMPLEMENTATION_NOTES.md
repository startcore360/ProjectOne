# T10 implementation contract — library dues and fine policy

## Library fine policy

Migration 74 creates `app.library_fine_policy`, keyed by `(tenant_id, institution_id)`.
The configuration fields are `grace_period_days`, `calculation_mode` (`FLAT_DAILY_AMOUNT`), `daily_amount`, `day_basis` (`CALENDAR_DAYS` or `WORKING_DAYS`), `fine_cap`, and `is_placeholder_default`.

**The INR 5/day, INR 500 cap, zero-grace, calendar-day seed is a placeholder only. It is not an approved fixed business rule.** Each institution must be able to change its own policy. Partial payment and returned-lost-book workflows are out of scope; a fine is paid in full or remains outstanding.

## T9 No-Dues read contract

Migration 74 publishes `app.v_student_library_dues_flag` as a read-only, `security_invoker` view. Columns:
- `tenant_id uuid`
- `institution_id uuid`
- `student_id uuid` — borrower user identifier
- `has_library_dues boolean` — true for unreturned/overdue/lost circulation or an outstanding fine
- `outstanding_fine_balance numeric(12,2)` — unpaid fine balance

Underlying tables retain tenant and institution RLS. T9 should query this view under the student's tenant/institution context and treat no row as no library activity/dues. This is a read contract, not a write API.

## Current delivery boundary

Migrations 73 and 74, rollback scripts, schema smoke checks, and registration in `sql/run_all.sh` are committed. Backend controllers/services, frontend pages, role/action authorization wiring, SMS metering event production, and analytics datasource wiring remain to be implemented and verified before T10 can be called complete.
