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
| STAFF_RECORD_MANAGE | 3 | Not started |
| STAFF_DOCUMENT_VERIFY | 3 | Not started |
| STAFF_RECORD_VIEW | 3 | Not started |

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

**Status:** In progress. The first implementation commit is on `sprint-2-development`; this correction set is being prepared on the same branch. No story is marked Accepted.

### B5 — Files

| Item | Current implementation | Remaining limitation |
|---|---|---|
| Upload | Multipart upload, 10 MiB application limit, safe-list content inspection, filename sanitization, SHA-256, PENDING scanner contract and structured `FILE_UPLOADED` audit entry. Audit values contain file identifiers/metadata only, never filename or bytes. | No real malware scanner; default scanner leaves files PENDING. |
| Download | Attachment streaming, safe content type, `nosniff`, tenant-qualified file lookup, scan/status enforcement and a database-backed owning-record policy. | The attach-to-record endpoint is not implemented in this chunk. Existing domain rows can be authorized; a newly uploaded, unattached file is not downloadable. |
| Record policy | Study material: active study-material record, uploader/assigned teacher, or student with a current IN_PROGRESS enrollment in its section/subject group; guardian must have an ACTIVE link to that enrolled student. Assignment submission: own student record, ACTIVE linked guardian, or assignment author. Syllabus: published record. Staff document: uploader or staff member. Attendance source: uploader's attendance import job. | The approved STUDY_MATERIAL_VIEW action and student/guardian ownership policy are implemented in migration 28 and the file download service. File-to-record attachment remains open. |
| Local storage | WHAT/HOW/WHY comments restored. Storage keys are normalized, checked for lexical containment and resolved-path/symlink containment before reads. | Local disk remains an interim deviation from D5 object storage and 15-minute expiring links. |
| Type validation | File signatures are inspected. A valid safe file may declare `application/octet-stream`; server-detected MIME is returned. Executable-renamed files and unsafe HTML/SVG/scripts are rejected. | OOXML inspection checks ZIP central-directory names only; archive entries are never inflated. |
| Size limits | General multipart ceiling is 10 MiB with an 11 MiB request allowance. `ProfileService` retains its separate 2 MiB JPEG/PNG check. | Profile upload remains subject to the global multipart parser ceiling before its endpoint-level 2 MiB check. |

**DEC-7:** `files.allow-pending-download=true` in dev, `false` in prod, and false by default. Pending files are served only when the owning-record policy and action authorization both pass. INFECTED, QUARANTINED, DELETED and SCAN_FAILED remain blocked.

### B6 — In-app notifications

- `UserNotification` maps the existing `app.notification` columns, including entity and dedupe fields.
- `InAppNotifier` writes with `REQUIRES_NEW`, catches delivery exceptions and deduplicates by tenant/key.
- Tests cover dedupe and notification persistence failure not escaping to the caller transaction.
- **Event wiring remains open:** this branch has no timetable publication, assignment publication, absence-alert or HOD self-approval publisher/service to invoke the notifier from. SMS/email remain out of scope.

### B7 — Frontend shell

- Tenant permissions are loaded after login and session hydration/page refresh, reloaded after active-role switch, and checked again at AppLayout bootstrap when tenant/user context does not match.
- `AppLayout` filters tenant academic menu entries using server-issued `canAny()` grants for Timetable, Attendance, Assignments, Study material, Syllabus and Staff. Entries without a shipped page are visibly disabled as “Coming soon”; no placeholder routes are registered.
- `ActionGate` is now used around existing routes for institution, user, role, academic-structure, term-lock and self-service screens. Denials are neutral in-page states; backend endpoint authorization remains authoritative.
- Guardian dashboard uses `GuardianChildSwitcher`, which loads only ACTIVE linked children and presents selected-child identifiers/status as read-only.
- The duplicate common `DateField` is removed. The shared design-system `DateField` is exported and used by existing academic-session and weekly-off setup forms.
- The eight remaining generic controls (`PersonPicker`, `SectionPicker`, `SubjectAllocationPicker`, `WeekGrid`, `StatusToggleRoster`, `FileDrop`, `WarningsPanel`, `DiffTable`) have focused component tests and a common export barrel. They are not yet consumed by production feature pages because the matching timetable/attendance/assignment pages and their supporting list APIs are not present in this chunk; do not report them as integrated into those workflows.

### Chunk 2 tests

- Backend: `FileContentInspectorTest` — PDF magic bytes, generic browser MIME, S2-27 renamed executable, unsafe HTML and NUL-containing text.
- Backend: `FileServiceTest` — S2-27 12 MiB rejection, audit record on upload, and S2-29 denial when the classmate does not match the owning-record policy.
- Backend: `FileObjectControllerTest` — upload metadata response, attachment disposition and nosniff headers.
- Backend: `LocalFileStorageTest` — read/write and path traversal containment.
- Backend: `InAppNotifierTest` — dedupe and persistence failure isolation.
- Frontend: tenant permission slice, login/hydration permission bootstrap, ActionGate denied/allowed state, guardian ACTIVE-child selection, DateField and the eight generic reusable controls.
- CI run `43029231137` on commit `ce2afd38d3d05e31fa2e52da9960becc1de0a058`: Spring Boot `clean verify` passed (808 tests, 0 failures/errors); PostgreSQL migration validation passed; frontend lint passed; frontend unit suite passed (168 tests across 22 files); frontend type-check and production build passed. The symlink containment test is included in the successful backend run. OWASP Maven Dependency-Check and CodeQL analysis were still in progress at the time of this note; do not infer their outcome. Do not claim local execution; validation ran in GitHub Actions.

### Open items before Chunk 2 can be marked Tested

1. Execute all four required gates on the corrected branch: backend verify, PostgreSQL SQL validation, frontend lint/tests/build. Review the dependency-audit report separately and remediate or formally disposition the CVE findings.
2. `STUDY_MATERIAL_VIEW` is now in the reference catalogue and migration 28, with enrolled-student/active-guardian policy and download authorization. The migration and policy require the follow-up CI run to pass before this item can be marked Tested.
3. Implement file attachment to an owning domain record and audit every subsequent scan/status transition.
4. Wire `InAppNotifier` when the relevant timetable, assignment, attendance and HOD approval publishers exist.
5. Consume the remaining generic controls from the real feature pages when those pages and supporting APIs are delivered; do not fabricate page workflows in this chunk.


### Status correction — staff stories

The three staff action codes above belong to Chunk 3 (B4), not Chunk 1. They are **Not started** until the B4 implementation and a green CI run cover them. No story is marked Accepted. This correction changes reporting only; it does not claim implementation or test execution.


### Latest branch validation — progress correction commit

GitHub Actions run [43171144257](https://github.com/startcore360/ProjectOne/actions/runs/43171144257) for commit `dd01ef966a9a1e18a9b0a4869f9799e5f8586bf2` completed with these job results:

| Job | Result | Evidence |
|---|---|---|
| Spring Boot quality gates | **Passed** | Run 43171144257, job 131024577601 — Verify backend and package upload succeeded. |
| PostgreSQL migration validation | **Passed** | Run 43171144257, job 131024577670 — `sql/run_all.sh` succeeded against PostgreSQL 16. |
| Frontend quality gates | **Passed** | Run 43171144257, job 131024577790 — `npm ci`, lint, full unit suite, type-check/build and artifact upload succeeded. |
| CodeQL static analysis | **Passed** | Run 43171144257, job 131024577454 — initialize, autobuild and analyze succeeded. |
| Dependency and secret checks | **Failed** | Run 43171144257, job 131024577752 — npm audit passed; OWASP Maven Dependency-Check failed on high/critical findings. Secret scan was skipped after the Maven audit failure. |
| Pull request dependency review | **Skipped** | Run 43171144257, job 131024579427 — this was a push run, not a pull request. |

The OWASP Maven findings are a **pre-existing baseline failure**: the same dependency-check step and CVE families failed on the pre-sync target commit in run [43029920181](https://github.com/startcore360/ProjectOne/actions/runs/43029920181), job 130554023543. The baseline run's Spring Boot, PostgreSQL and frontend jobs passed; CodeQL passed. The dependency failure is not attributed to the progress correction or source-sync commit. The OWASP gate remains open pending the C2-5 remediation; do not treat it as passed. Secret scanning did not execute in either failed dependency job. Dependency Review is not executed on push events.


### B5 study-material view access — implementation update

- Added migration `28_v41_study_material_view.sql`: the approved `STUDY_MATERIAL_VIEW` action is registered and mapped to Assignments; grants are assigned to college/school students, college/school parents, and assigned teaching roles.
- File downloads now authorize `STUDY_MATERIAL_VIEW`, not the upload action. The database-backed ownership policy requires an active study-material row and current `IN_PROGRESS` enrollment in the matching section or subject group; guardian access additionally requires an `ACTIVE` student-guardian link. All joins are tenant/institution-qualified and policy errors fail closed.
- SQL checks `S2-B5-01/02` and a backend authorization regression test were added. CI verification is pending; this item is not marked Tested until the pipeline completes.

- CI follow-up found an action-number collision: `ROLLDOWN_VIEW` owns action number 279 in the V4.0 reference seed. `STUDY_MATERIAL_VIEW` is now assigned 280 in both the reference catalogue and migration 28; the next SQL pipeline run will verify the corrected seed order.
