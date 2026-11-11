# EDU 360 Sprint 2 Progress

| Action code | Chunk | Status |
|---|---:|---|
| TIMETABLE_BUILD | 1 | Tested |
| ONLINE_PERIOD_SET | 1 | Tested |
| TIMETABLE_VIEW | 1 | Tested |
| SYLLABUS_VIEW | 1 | Tested |
| SYLLABUS_MANAGE | 1 | Tested |
| ATTENDANCE_MARK | 1 | Tested |
| ATTENDANCE_NOT_HELD | 1 | Tested |
| SUBSTITUTE_ASSIGN | 1 | Tested |
| ATTENDANCE_CORRECTION_REQUEST | 1 | Tested |
| ATTENDANCE_CORRECTION_APPROVE | 1 | Tested |
| ATTENDANCE_BULK_UPLOAD | 1 | Tested |
| ATTENDANCE_BULK_UPLOAD_APPROVE | 1 | Tested |
| ATTENDANCE_RULES_SET | 1 | Tested |
| ATTENDANCE_VIEW | 1 | Tested |
| ATTENDANCE_VIEW_OWN | 1 | Tested |
| ASSIGNMENT_CREATE | 1 | Tested |
| ASSIGNMENT_CLOSE | 1 | Tested |
| STUDY_MATERIAL_UPLOAD | 1 | Tested |
| ASSIGNMENT_SUBMIT | 1 | Tested |
| ASSIGNMENT_EVALUATE | 1 | Tested |
| HOMEWORK_STATUS_MARK | 1 | Tested |
| ASSIGNMENT_VIEW_OWN | 1 | Tested |
| HOMEWORK_COMPLIANCE_VIEW | 1 | Tested |
| STAFF_RECORD_MANAGE | 1 | Tested |
| STAFF_DOCUMENT_VERIFY | 1 | Tested |
| STAFF_RECORD_VIEW | 1 | Tested |

## Chunk 1 — Foundations

**Status:** Tested (not Accepted). The core application and database gates passed in GitHub Actions run [42797436612](https://github.com/startcore360/ProjectOne/actions/runs/42797436612): Spring Boot/Java 21 verify, PostgreSQL migration/SQL validation, frontend lint, 154 frontend tests, TypeScript and production build. The overall workflow still reported separate dependency/security configuration findings (Dependency Review requires the repository dependency graph; OWASP Dependency-Check had no NVD API key; npm audit reported seven moderate minimatch findings).

### Part A — Sprint 1 dependency audit

| Check | Finding and evidence | Disposition |
|---|---|---|
| A1 Tenant/institution scope | `api/src/main/java/com/edu360/api/identity/TenantUserLookup.java` resolves one active role assignment and previously exposed only its institution. | Updated institution scope to include active assignments for the same selected role definition and scope type. Regression test still required. |
| A2 Subject allocation | `SubjectAllocationService.java` was querying `UserRoleAssignmentRepository` using a staff-assignment id. Schema FK `fk_subject_allocation_staff_assignment_id` points to `app.staff_assignment`; Java mapping/repository exist in `institution/StaffAssignment.java` and `StaffAssignmentRepository.java`. | Updated to validate a same-tenant staff assignment, ACTIVE status, matching institution and tenant-local effective dates. |
| A3 Academic structure PATCH | `StructureController` exposes POST/GET for departments, programs, years, semesters, classes, sections, subjects and course offerings; no PATCH/PUT or If-Match handlers exist. Subject-group/member Java resources were not found. `Section` has `academicSessionId` and exactly one of `semesterId`/`classId`; college department is derived via semester → program year → program. | **Carry-forward gap by decision:** record missing PATCH/If-Match handlers and subject-group/member APIs. Do not implement in Chunk 1 unless a later Sprint 2 dependency explicitly requires them (DEC-9). |
| A4 Calendar and weekly off | Calendar events are institution/date scoped; weekly-off patterns support `ALL`, `ODD`, `EVEN`. The committed event-type constraint has no exam-day event type or exam calendar relation. | Added date-scoped holiday, weekly-off and academic-session lookup; single-day session ranges remain valid. **Exam-day support deferred by decision:** requirements and schema representation remain open; add no exam-day schema or title inference. DEC-13 holiday warning behaviour is unaffected. |
| A5 Term lock | SQL `app.is_term_locked(tenant, section, date)` exists in `sql/02_rules.sql`; Java service previously had lock/unlock only. | Added shared `TermLockService.isLocked(tenantId, sectionId, date)` delegating to the SQL function. |
| A6 Class teacher, mentor, HOD | `ClassTeacherAssignmentService`, `MentorAssignmentService`, and `HodAssignmentService` exist. | Retain the approved specification rules and complete regression validation for active status, effective dates, institution/department scope, overlap and unauthorized assignment paths. **Not yet validated.** |
| A7 Approval routing | `ApprovalInboxService.resolveApprovalAction` recognised institution, promotion and guardian processes; attendance process codes were absent. | Added correction and bulk-upload action resolution, the narrow Java-side HOD self-approval check against the correction's marked session (APPROVED only), a dedicated self-approval audit event, and a fail-closed `ApprovalOutcomeHandler` extension point. Self-rejection/forwarding remains blocked. |
| A8 Student login switch | `StudentLoginSwitchService` toggles `school_class.student_login_enabled`. | Migration 26 requires a current IN_PROGRESS enrollment for both pathways; college semester sections remain eligible, while school class sections additionally require `student_login_enabled=true` and active class/section. The SQL acceptance test currently checks function definition, not all four behavioural cases (school enabled/disabled, college enrolled, no current enrollment); those behavioural tests remain open. Own-data academic API enforcement remains open until those endpoints exist. |
| A9 Guardian links | No ACTIVE-child lookup existed; guardian profile creation generated a random account UUID despite the composite FK to `app.user_account`; Java guardian count included BLOCKED rows unlike the database guard. | Added account lookup by tenant/mobile/GUARDIAN type, removed random account IDs, excluded BLOCKED links from guardian cap, and added tenant/account-scoped ACTIVE-child lookup plus `/guardian/children`. Regression tests added; SQL/runtime validation remains open. |
| A10 HTTP and database errors | `GlobalExceptionHandler` already returns field-level Bean Validation errors. Database trigger errors previously fell through to generic handling. | Added typed `ApiException`, 503 `AUDIT_UNAVAILABLE`, D5 business-rule status mapping (409/413/400/422), and known term/correction/not-held/self-approval/partition/unique/foreign-key database mappings. Added focused `GlobalExceptionHandlerTest` coverage. |

### Built

- Structured audit entry model and full `app.audit_outbox` JDBC columns: action code, entity type/id, record count, sensitivity, old/new JSON, outcome, actor, active role and correlation id. Legacy overload remains.
- Dedicated Sprint 2 audit event types and operation mapping.
- Shared `CursorPage` helper: opaque Base64 cursor, default limit 50, max 200, allow-listed sort and stable id tie-break.
- `IdempotencyService` backed by `app.idempotency_key`, including request hashing, replay response storage, reused-key conflict and in-progress conflict. Migration adds `response_body`.
- `TenantClock` reads `app.tenant_local.time_zone`, defaulting to Asia/Kolkata.
- Approval action resolution for `ATTENDANCE_CORRECTION` and `ATTENDANCE_BULK_UPLOAD`; final side effect fails closed if no handler is registered.
- SQL migration `25_sprint2_foundations.sql`, rollback `25r_sprint2_foundations_rollback.sql`, and tests `25t_tests_sprint2_foundations.sql`. Migration adds HOD exception, attendance partitions 2028–2030, six attendance settings and DEC-14 standard-role syllabus grants.
- SQL migration `26_v41_student_login_switch_guard.sql`, rollback `26r_v41_student_login_switch_guard_rollback.sql`, and test `26t_tests_student_login_switch_guard.sql`; `sql/run_all.sh` now applies, tests and rolls back the A8 guard.
- Updated `sql/run_all.sh` to apply migration and execute its SQL checks; added a minimal isolated reference fixture so the acceptance database does not conflict with the full reference-data seed.
- Rechecked the 26 Sprint 2 action codes against `sql/05_ref_data.sql`: all 26 have action-catalog entries and at least one standard-role grant. The migration now fails closed if either catalog entries or grants are missing.

### Tests and commands

- Added `CursorPageTest.defaultLimitAndStableSortAreApplied`, `CursorPageTest.rejectsUnknownSortAndOversizedLimit`, `CursorPageTest.cursorRoundTripsAndRejectsTampering`.
- Added `TenantClockTest.usesTenantTimeZoneForBusinessDate`.
- Added `GuardianLinkControllerTest.scope_guardianSeesOwnActiveChildren`, `GuardianLinkControllerTest.deny_nonGuardianCannotListChildren`, `GuardianLinkControllerTest.guard_missingGuardianAccount`, and `GuardianLinkControllerTest.guard_blockedLinksDoNotConsumeGuardianLimit`.
- Added `CalendarDayStatusServiceTest.returnsLocalAndUniversityHolidayExamAndWeeklyOffForRequestedDate`, `CalendarDayStatusServiceTest.appliesOddAndEvenOccurrenceToAlternateWeekends`, and `CalendarDayStatusServiceTest.refusesForeignTenantCalendarScope` (exam-day explicitly false because the schema has no exam-day representation).
- Added `IdempotencyServiceTest.requestHashIsDeterministicForMapKeyOrder` and `IdempotencyServiceTest.requestHashChangesWhenBodyChanges`.
- Added SQL checks `S2-DB-01` through `S2-DB-10`, including HOD own-marked-session allowance, other-teacher denial, other-role denial, non-attendance denial, decided-step immutability and the student login-switch function contract.
- `./mvnw clean verify` (CI equivalent `mvn --batch-mode --no-transfer-progress -f api/pom.xml verify`): last observed CI run failed compilation because `CalendarDayStatusService` referenced undefined `examDay`; corrected in this revision. Awaiting rerun.
- `npm run lint`, `npm test`, `npm run build`: not executed; no frontend files were changed in this chunk.
- `sql/run_all.sh`: last observed CI rollback failed because `attendance_correction`'s FK depended on the 2030 partition; rollback now temporarily drops and restores the FK around partition drops. Awaiting rerun.

### Deviations, bounded exceptions and open questions

- DEC-14 is applied only to standard role templates. Existing tenant-local role-action copies are not modified under OD-62. The tenant ids needing rollout must be enumerated against each live tenant database; that query has not been run.
- Approval handlers are an extension point; no attendance outcome handler is registered yet. Final approval is denied and the transaction fails closed until a later attendance chunk registers one.
- Idempotency helper is implemented but is not wired to attendance upload/correction/syllabus endpoints, which are implemented in later chunks.
- Cursor helper is implemented but no new list endpoints exist in Chunk 1 to consume it.
- A3 is explicitly carried forward by decision; A4 exam-day is deferred pending requirements. A6 assignment service regression suites pass on CI, with broader effective-date and overlap edge coverage still to be confirmed. A8 behavioural login-path SQL coverage is added; future academic own-data API enforcement remains open. A9 code changes and focused tests are present but integration/build regression is not yet executed; no claim is made that all Sprint 1 dependencies are fully validated.
- No story is marked Accepted. Highest status remains In progress until build and SQL gates pass.


## Chunk 2 — Platform files and frontend shell

**Status:** In progress. Implementation has been prepared against the live `sprint-2-development` branch; CI validation is pending the branch update.

| Work item | Status | Notes |
|---|---|---|
| B5 File upload endpoint | In progress | Purpose-based action authorization, 10 MiB limit, safe-list content inspection, filename sanitization, SHA-256, PENDING scanner contract. |
| B5 File download | In progress | Streaming response, attachment disposition, nosniff, tenant isolation, status checks and environment-gated PENDING downloads. Owning-record policy remains fail-closed until domain record associations and role/scope policies are registered. |
| B5 Local storage containment | In progress | Storage keys are normalized and resolved-path containment is checked before reads; traversal tests added. |
| B6 In-app notification persistence | In progress | Entity maps app.notification audit, entity and dedupe columns; independent transaction prevents notification failure from marking the caller transaction rollback-only. |
| B6 Notification event wiring | In progress | Timetable, assignment, absence and HOD self-approval publishers are not yet present in the branch; notifier is a reusable service, not yet called by those domain events. |
| B7 Tenant permissions | In progress | Redux slice/service reloads on tenant login and active-role switch; useTenantAccess exposes can/canAny. |
| B7 Action gate and navigation | In progress | Neutral ActionGate and action-filtered existing tenant navigation added. Future academic pages are not registered as placeholders. |
| B7 Reusable components | In progress | PersonPicker, SectionPicker, SubjectAllocationPicker, DateField, WeekGrid, StatusToggleRoster, FileDrop, WarningsPanel and DiffTable added. |
| B7 Parent child switcher | In progress | Loads the existing ACTIVE-child endpoint and filters non-active links. Child-specific read-only pages are not yet available. |

### Chunk 2 decisions and bounded deviations

- **DEC-7 retained as a bounded development deviation from V4.0 TE-11.** `files.allow-pending-download=true` in `application-dev.properties`; `false` in `application-prod.properties`; global default is false. INFECTED, QUARANTINED, DELETED and SCAN_FAILED are never downloadable.
- The default `FileAccessPolicy` denies downloads until a domain module registers an owning-record authorization policy. This avoids treating knowledge of a file UUID or uploader identity as record authorization. Consequently, PENDING download is configured for development but is not exposed for an unassociated file.
- No malware scanner is configured. `PendingFileScanner` leaves files PENDING. A scanner implementation returning INFECTED causes QUARANTINED status.
- Local filesystem streaming is used instead of the D5 15-minute object-store link; this remains a deviation until object storage is integrated.
- Notification persistence is implemented, but no timetable, assignment, attendance or HOD approval event publisher is available in this branch to wire it into yet.
- The tenant shell only exposes existing routes; future-chunk pages are not represented by placeholder routes. The child switcher is reusable and read-only, but there are no child-specific academic pages to attach it to yet.

### Chunk 2 tests added

- Backend: `FileContentInspectorTest` covers PDF magic bytes, renamed executable rejection (S2-27), HTML and NUL-containing text rejection; `LocalFileStorageTest` covers read/write and path traversal containment.
- Frontend: `tenantPermissionsSlice.test.ts`, `ActionGate.test.tsx`, and `GuardianChildSwitcher.test.tsx`.
- These tests are written but have not yet been executed locally; the branch CI run will be the execution gate. No story is marked Accepted.
