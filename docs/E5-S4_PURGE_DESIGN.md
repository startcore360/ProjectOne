# E5-S4 — Tenant purge design (reference)

**Status:** Accepted as a design reference only. **Not approved for build.**
**Implementation:** Parked until OD-06 (retention durations / what may be destroyed) is decided.
**Scope:** SHARED tenants only. ISOLATED tenants are refused and recorded as `blocked: E10-S9`.
**Recorded:** 2026-11-17, from the A5 (Data Protection) design review.

Nothing in this note authorises code, SQL, migrations, grants, functions, trigger changes or a purge run.

## 1. Decisions

1. Do **not** create an `edu_purge` role. Reuse the existing D4 role `edu_retention`
   (`sql/01_schema.sql`, `LOGIN NOSUPERUSER NOBYPASSRLS`).
2. `edu_retention` gets `SELECT` and `DELETE` on `app.*` only. No `INSERT`, `UPDATE`, `TRUNCATE`, DDL,
   ownership, or any `platform.*` access. RLS and `FORCE ROW LEVEL SECURITY` stay in force.
3. **`edu_retention` is shared** with the existing student-note retention job. It is not a purge-only credential.
4. `app.institution_scope = 'ALL'` is set only on the purge job's own connection, transaction-locally.
5. Credentials come from the secrets store, never from properties, `.env` files or source control.
6. No `purge_db_connection_ref` column (ISOLATED purge is deferred to E10-S9).
7. No retention period is invented. OD-06 stays open.

## 2. Tables whose triggers block DELETE

`sql/03_bindings.sql` declares DELETE-blocking triggers on 23 `app.*` tables, in three groups:

| Group | Tables | Purge treatment |
|---|---|---|
| Operational append-only | `staff_service_record`, `approval_batch`, `approval_step`, `term_lock_event`, `risk_remark`, `message`, `message_attachment`, `notification_delivery_attempt`, `complaint_action`, `policy_acceptance` | Narrow trigger exception: allow `DELETE` when `current_user = 'edu_retention'`; every other role unchanged |
| State-locked | `attendance_correction`, `grade_band`, `marks_entry`, `fee_structure_line`, `fee_structure_installment` | Same narrow exception. A whole-tenant purge makes draft/final state locks irrelevant; ordinary behaviour unchanged |
| Retention guard | `student_note` | Separate purge path (section 4); never make the normal guard permissive |
| Retained pending OD-06 | `receipt`, `ledger_entry`, `financial_period_lock_event`, `payment_gateway_event`, `credit_application`, `audit_outbox`, `data_request_event` | Not deleted; guards unchanged |

## 3. The retention closure (why "keep 7 tables" keeps much more)

The seven retained tables have `ON DELETE RESTRICT` foreign keys to their parents, recursively. Keeping them
forces keeping **39 tables**: the 7 plus these 32 ancestors:

`academic_session`, `approval_request`, `concession`, `data_request`, `fee_head`, `financial_period_lock`,
`gateway_order`, `gateway_transaction`, `institution`, `late_fee_waiver`, `payment`, `scholarship_award`,
`student_fee_account`, `student_installment`, `tenant_local`, `user_account`, `fee_assignment`,
`scholarship_award_list`, `student_enrollment`, `student_profile`, `fee_structure`, `file_object`,
`scholarship_scheme`, `section`, `fee_policy`, `program_year`, `school_class`, `semester`, `program`,
`department`, `staff_assignment`, `staff_profile`.

This retains **student and staff identity, user accounts, and their academic and financial links**. Guardian
tables (`guardian_profile`, `student_guardian_link`) are not in the closure.

`message_thread` is also retained, separately, because its schema comment says it is never deleted.

That leaves 122 candidate tables, deleted children-before-parents in eight waves (wave 1 = leaf records such as
`student_note`, `approval_step`, the state-locked children and message attachments; wave 8 = `subject`,
`grading_scheme_version`). The full wave list must be regenerated from `sql/01_schema.sql` at build time.

**Open schema issue:** `result_publication` and `result_withdrawal_request` reference each other with
`ON DELETE RESTRICT`. The purge must handle this explicitly or fail loudly and safely; no cycle-breaking change
is approved. Self-referencing tables (`student_note`, `marks_submission`, `timetable_slot`) need row-level order.

## 4. Student notes: chosen approach (b) — SECURITY DEFINER check

A caller-set context value is not trusted, because `edu_retention` is shared. Instead a narrow
`SECURITY DEFINER` function checks the real approval:

- Owner: a new role **`edu_retention_fn_owner`** — `NOLOGIN NOSUPERUSER NOBYPASSRLS`, not a table owner, no
  membership with `edu_retention`. **This is an additional database role and needs explicit review.**
- Owner grants only: `USAGE` on `app` and `platform`; `SELECT` on `platform.deletion_request` and
  `app.legal_hold`; `SELECT, DELETE` on `app.student_note`.
- `EXECUTE` granted only to `edu_retention`. Fixed `search_path`, schema-qualified names.
- The function takes a tenant id and a deletion request id and deletes that tenant's notes only when the request
  exists, belongs to that tenant, is approved, has reached `retention_ends_at`, and no unreleased legal hold exists.
  It never updates `delete_after` or note content. The ordinary retention path is unchanged.

## 5. Legal holds

Any unreleased row in `app.legal_hold` for the tenant (any `subject_kind`) blocks the **whole** purge before any
destructive step: no rows, files, certificate or realm deleted. The hold check and hold placement must be
serialised (tenant-level lock or equivalent); a preflight `SELECT` alone is not enough.

## 6. Order and outcome

1. Refuse ISOLATED → record `blocked: E10-S9`.
2. Verify the request is DPO-approved and `retention_ends_at` has passed.
3. Legal-hold check (stop the whole purge if any).
4. Open the `edu_retention` connection; set tenant id and `institution_scope = 'ALL'` transaction-locally.
5. Delete the permitted rows (waves), then notes through the definer function.
6. Count rows left in the retained tables.
7. If any remain → `RETENTION_PENDING` with per-table counts; stop. No `deleted_at`, no certificate, no file or
   realm deletion. (`RETENTION_PENDING` is not yet in `ck_deletion_request_status`; a migration is needed.)
8. Only when nothing remains: files → certificate → Keycloak realm (last) → `DELETED`. Each step retry-safe.

## 7. OD-06 options (decision pending)

| Option | Effect |
|---|---|
| A. Keep the 39-table closure as `RETENTION_PENDING` | Finance kept for tax, but student/staff personal data kept for years; no erasure certificate |
| B. Full purge after the export is delivered to the tenant | Clean erasure and certificate; the school keeps its own financial copy. Needs legal sign-off — export delivery alone is not legal authority |
| C. Keep finance, pseudonymise identities | Needs updates to rows designed never to change; complex |

**Current preference: B, subject to legal sign-off.** Record the decision in `docs/GOVERNANCE_DECISION_LOG.md`.

## 8. Tests required at build time

`edu_app` still blocked on every changed trigger; `edu_retention` limited to one tenant and no `platform.*`;
ordinary roles keep append-only and state-lock protection; legal hold blocks the purge; a held note cannot be
deleted and an eligible note is deleted without update; retained rows prevent `DELETED`; ISOLATED refused without
opening its database; retry after partial external work does not duplicate a certificate. Add `PurgeJobIT`
against real Postgres — H2 tests are not evidence for RLS, triggers or role boundaries.
