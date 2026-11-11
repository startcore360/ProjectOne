# EDU 360 Sprint 2 Progress

| Action code | Chunk | Status |
|---|---:|---|
| TIMETABLE_BUILD | 1 | In progress |
| ONLINE_PERIOD_SET | 1 | In progress |
| TIMETABLE_VIEW | 1 | In progress |
| SYLLABUS_VIEW | 1 | In progress |
| SYLLABUS_MANAGE | 1 | In progress |
| ATTENDANCE_MARK | 1 | In progress |
| ATTENDANCE_NOT_HELD | 1 | In progress |
| SUBSTITUTE_ASSIGN | 1 | In progress |
| ATTENDANCE_CORRECTION_REQUEST | 1 | In progress |
| ATTENDANCE_CORRECTION_APPROVE | 1 | In progress |
| ATTENDANCE_BULK_UPLOAD | 1 | In progress |
| ATTENDANCE_BULK_UPLOAD_APPROVE | 1 | In progress |
| ATTENDANCE_RULES_SET | 1 | In progress |
| ATTENDANCE_VIEW | 1 | In progress |
| ATTENDANCE_VIEW_OWN | 1 | In progress |
| ASSIGNMENT_CREATE | 1 | In progress |
| ASSIGNMENT_CLOSE | 1 | In progress |
| STUDY_MATERIAL_UPLOAD | 1 | In progress |
| ASSIGNMENT_SUBMIT | 1 | In progress |
| ASSIGNMENT_EVALUATE | 1 | In progress |
| HOMEWORK_STATUS_MARK | 1 | In progress |
| ASSIGNMENT_VIEW_OWN | 1 | In progress |
| HOMEWORK_COMPLIANCE_VIEW | 1 | In progress |
| STAFF_RECORD_MANAGE | 1 | In progress |
| STAFF_DOCUMENT_VERIFY | 1 | In progress |
| STAFF_RECORD_VIEW | 1 | In progress |

## Chunk 1 — Foundations

**Status:** In progress. Changes are committed to `sprint-2-development`; build and database execution gates remain open.

### Part A — Sprint 1 dependency audit

| Check | Finding and evidence | Disposition |
|---|---|---|
| A1 Tenant/institution scope | `api/src/main/java/com/edu360/api/identity/TenantUserLookup.java` resolves one active role assignment and previously exposed only its institution. | Updated institution scope to include active assignments for the same selected role definition and scope type. Regression test still required. |
| A2 Subject allocation | `SubjectAllocationService.java` was querying `UserRoleAssignmentRepository` using a staff-assignment id. Schema FK `fk_subject_allocation_staff_assignment_id` points to `app.staff_assignment`; Java mapping/repository exist in `institution/StaffAssignment.java` and `StaffAssignmentRepository.java`. | Updated to validate a same-tenant staff assignment, ACTIVE status, matching institution and tenant-local effective dates. |
| A3 Academic structure PATCH | Existing structure APIs are present; full If-Match/row_version and parent-scope review not completed in this chunk. | Open validation item. |
| A4 Calendar and weekly off | Existing `CalendarEventService` and `WeeklyOffRuleService` plus tests are present. Institution/date query coverage not rerun. | Open validation item. |
| A5 Term lock | SQL `app.is_term_locked(tenant, section, date)` exists in `sql/02_rules.sql`; Java service previously had lock/unlock only. | Added shared `TermLockService.isLocked(tenantId, sectionId, date)` delegating to the SQL function. |
| A6 Class teacher, mentor, HOD | `ClassTeacherAssignmentService`, `MentorAssignmentService`, and `HodAssignmentService` exist. Their full effective-date and scope regression was not rerun. | Open validation item. |
| A7 Approval routing | `ApprovalInboxService.resolveApprovalAction` recognised institution, promotion and guardian processes; attendance process codes were absent. | Added correction and bulk-upload action resolution, the narrow Java-side HOD self-approval check against the correction's marked session, a dedicated self-approval audit event, and a fail-closed `ApprovalOutcomeHandler` extension point. |
| A8 Student login switch | `StudentLoginSwitchService` and its tests exist. Full policy regression not rerun. | Open validation item. |
| A9 Guardian links | `GuardianLinkService` and guardian-link repository/tests exist. Active-link/child lookup regression not rerun. | Open validation item. |
| A10 HTTP and database errors | `GlobalExceptionHandler` already returns field-level Bean Validation errors. Database trigger errors previously fell through to generic handling. | Added typed `ApiException`, audit failure response, and known term/correction/not-held/self-approval/partition/unique/foreign-key mappings. |

### Built

- Structured audit entry model and full `app.audit_outbox` JDBC columns: action code, entity type/id, record count, sensitivity, old/new JSON, outcome, actor, active role and correlation id. Legacy overload remains.
- Dedicated Sprint 2 audit event types and operation mapping.
- Shared `CursorPage` helper: opaque Base64 cursor, default limit 50, max 200, allow-listed sort and stable id tie-break.
- `IdempotencyService` backed by `app.idempotency_key`, including request hashing, replay response storage, reused-key conflict and in-progress conflict. Migration adds `response_body`.
- `TenantClock` reads `app.tenant_local.time_zone`, defaulting to Asia/Kolkata.
- Approval action resolution for `ATTENDANCE_CORRECTION` and `ATTENDANCE_BULK_UPLOAD`; final side effect fails closed if no handler is registered.
- SQL migration `25_sprint2_foundations.sql`, rollback `25r_sprint2_foundations_rollback.sql`, and tests `25t_tests_sprint2_foundations.sql`. Migration adds HOD exception, attendance partitions 2028–2030, six attendance settings and DEC-14 standard-role syllabus grants.
- Updated `sql/run_all.sh` to apply migration and execute its SQL checks; added a minimal isolated reference fixture so the acceptance database does not conflict with the full reference-data seed.
- Rechecked the 26 Sprint 2 action codes against `sql/05_ref_data.sql`: all 26 have action-catalog entries and at least one standard-role grant. The migration now fails closed if either catalog entries or grants are missing.

### Tests and commands

- Added `CursorPageTest.defaultLimitAndStableSortAreApplied`, `CursorPageTest.rejectsUnknownSortAndOversizedLimit`, `CursorPageTest.cursorRoundTripsAndRejectsTampering`.
- Added `TenantClockTest.usesTenantTimeZoneForBusinessDate`.
- Added `IdempotencyServiceTest.requestHashIsDeterministicForMapKeyOrder` and `IdempotencyServiceTest.requestHashChangesWhenBodyChanges`.
- Added SQL checks `S2-DB-01` through `S2-DB-09`, including HOD own-marked-session allowance, other-teacher denial, other-role denial, non-attendance denial and decided-step immutability.
- `./mvnw clean verify`: not executed; remote GitHub editing connection does not provide a local checkout/build runner.
- `npm run lint`, `npm test`, `npm run build`: not executed; no frontend files were changed in this chunk.
- `sql/run_all.sh`: not executed; PostgreSQL runtime is not available through the repository connection.

### Deviations, bounded exceptions and open questions

- DEC-14 is applied only to standard role templates. Existing tenant-local role-action copies are not modified under OD-62. The tenant ids needing rollout must be enumerated against each live tenant database; that query has not been run.
- Approval handlers are an extension point; no attendance outcome handler is registered yet. Final approval is denied and the transaction fails closed until a later attendance chunk registers one.
- Idempotency helper is implemented but is not wired to attendance upload/correction/syllabus endpoints, which are implemented in later chunks.
- Cursor helper is implemented but no new list endpoints exist in Chunk 1 to consume it.
- A3, A4, A6, A8 and A9 remain open for deeper regression; no claim is made that all Sprint 1 dependencies are fully validated.
- No story is marked Accepted. Highest status remains In progress until build and SQL gates pass.
