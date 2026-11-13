# EDU 360 Sprint 2 Progress

## Current source reconciliation — 13 November 2026

This section reconciles the initial action matrix with the code now present on `sprint-2-development`. “Implemented” means a code path exists; it does not mean a workflow has been formally accepted.

| Area | Current source status | Boundary / remaining work |
|---|---|---|
| Staff | Staff directory, profile edit, assignments, service history, staff-document attach and document verification UI/API are present. Staff action rows below are now “Implemented — acceptance pending”. | Verify full role/scope combinations and the end-to-end upload → scan → attach → independent verification path before acceptance. |
| Student admission | A dedicated student-creation endpoint and separate frontend form create a student profile plus enrolment. The form now loads active sessions/sections when lookup APIs are available. | The institution ID remains manually supplied because the institution directory endpoint has a narrower role grant. Manual session/section UUID entry remains a fallback. |
| Student CSV import | The existing CSV import operation is a generic user-account import. | It does not create student profiles or enrolments; student bulk admission with optional legacy student codes is not implemented end-to-end. |
| Calendar | Local and university calendar flows support typed exam-day events and holiday-conflict handling. | Exam-day calendar events are not a timetable builder, period schedule, or timetable publication workflow. |
| Support | Tenant support ticket and support-access screens/workflows are present. | Do not report broader support operations beyond the shipped screen and API paths as complete. |
| Files | Local disk storage, owning-record download policy, staff-document attachment flow, and a conditional ClamAV clamd scanner adapter are present. | ClamAV must be explicitly enabled and deployed; the default scanner keeps files PENDING. Generic attachment across all record types remains open. |
| OTP | Disabled-mode status/request/verify API endpoints are present. | No SMS provider, OTP lifecycle, guardian/applicant eligibility or Keycloak token exchange; parent/applicant mobile OTP login is not a completed workflow. |
| Academic structure | Existing create/list structure workflows include school classes, sections and academic-session lookups. | PATCH/If-Match and subject-group/member APIs remain outstanding (A3/G2). |
| Reusable controls | PersonPicker, SectionPicker, SubjectAllocationPicker, WeekGrid, StatusToggleRoster, FileDrop, WarningsPanel and DiffTable have tests. | These controls are foundations, not proof that the associated production workflows consume them. |

No story is marked Accepted solely because an endpoint, database rule, or reusable control exists. CI state and role-specific acceptance remain separate checks.

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
| STAFF_RECORD_MANAGE | 3 | Implemented — acceptance pending |
| STAFF_DOCUMENT_VERIFY | 3 | Implemented — acceptance pending |
| STAFF_RECORD_VIEW | 3 | Implemented — acceptance pending |

## Chunk 1 — Foundations

**Status:** Tested (not Accepted). The core application and database gates passed in GitHub Actions run [42797436612](https://github.com/startcore360/ProjectOne/actions/runs/42797436612): Spring Boot/Java 21 verify, PostgreSQL migration/SQL validation, frontend lint, 154 frontend tests, TypeScript and production build. The overall workflow still reported separate dependency/security configuration findings (Dependency Review requires the repository dependency graph; OWASP Dependency-Check had no NVD API key; npm audit reported seven moderate minimatch findings).

### Part A — Sprint 1 dependency audit

| Check | Finding and evidence | Disposition |
|---|---|---|
| A1 Tenant/institution scope | `api/src/main/java/com/edu360/api/identity/TenantUserLookup.java` resolves one active role assignment and previously exposed only its institution. | Updated institution scope to include active assignments for the same selected role definition and scope type. Regression test still required. |
| A2 Subject allocation | `SubjectAllocationService.java` was querying `UserRoleAssignmentRepository` using a staff-assignment id. Schema FK `fk_subject_allocation_staff_assignment_id` points to `app.staff_assignment`; Java mapping/repository exist in `institution/StaffAssignment.java` and `StaffAssignmentRepository.java`. | Updated to validate a same-tenant staff assignment, ACTIVE status, matching institution and tenant-local effective dates. |
| A3 Academic structure PATCH | `StructureController` exposes POST/GET for departments, programs, years, semesters, classes, sections, subjects and course offerings; no PATCH/PUT or If-Match handlers exist. Subject-group/member Java resources were not found. `Section` has `academicSessionId` and exactly one of `semesterId`/`classId`; college department is derived via semester → program year → program. | **Carry-forward gap by decision:** record missing PATCH/If-Match handlers and subject-group/member APIs. Do not implement in Chunk 1 unless a later Sprint 2 dependency explicitly requires them (DEC-9). |
| A4 Calendar and weekly off | Calendar events are institution/date scoped; weekly-off patterns support `ALL`, `ODD`, `EVEN`. A later Sprint 2 change adds typed `EXAM_DAY` calendar events and holiday-overlap handling. | Typed exam-day event creation and local/university calendar handling are implemented in migration 30. This is calendar event support, not a timetable builder or timetable publication workflow. DEC-13 holiday warning behaviour remains date-scoped. |
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
- Added `CalendarDayStatusServiceTest.returnsLocalAndUniversityHolidayExamAndWeeklyOffForRequestedDate`, `CalendarDayStatusServiceTest.appliesOddAndEvenOccurrenceToAlternateWeekends`, and `CalendarDayStatusServiceTest.refusesForeignTenantCalendarScope`; current exam-day status is derived from typed events added by migration 30.
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
- A3 is explicitly carried forward by decision; typed exam-day calendar events were added later in G3 and do not imply timetable delivery. A6 assignment service regression suites pass on CI, with broader effective-date and overlap edge coverage still to be confirmed. A8 behavioural login-path SQL coverage is added; future academic own-data API enforcement remains open. A9 code changes and focused tests are present but integration/build regression is not yet executed; no claim is made that all Sprint 1 dependencies are fully validated.
- No story is marked Accepted. Highest status remains In progress until build and SQL gates pass.


## Chunk 2 — Platform files and frontend shell

**Status:** In progress. The first implementation commit is on `sprint-2-development`; this correction set is being prepared on the same branch. No story is marked Accepted.

### B5 — Files

| Item | Current implementation | Remaining limitation |
|---|---|---|
| Upload | Multipart upload, 10 MiB application limit, safe-list content inspection, filename sanitization, SHA-256 and structured `FILE_UPLOADED` audit entry. A ClamAV `clamd` INSTREAM adapter is available behind `FILES_SCANNER_CLAMAV_ENABLED`; transport/protocol failure fails closed with 503. | The safe default is `PendingFileScanner` when ClamAV is disabled, so files remain PENDING until a scanner is deployed and enabled. The adapter must be enabled only when a reachable `clamd` service is configured. |
| Download | Attachment streaming, safe content type, `nosniff`, tenant-qualified file lookup, scan/status enforcement and a database-backed owning-record policy. A staff-document upload/attach/verify path is now present. | A generic file-to-record attachment workflow for all supported record types is not implemented. Newly uploaded, unattached files are not downloadable under the owning-record policy. |
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

The initial matrix above predates the B4 staff implementation that subsequently landed on this branch. The current source tree contains the staff directory/profile API, `StaffRecordsPage`, scoped route gating, staff assignments and service history, staff-document upload/attachment, and document verification. Focused frontend and backend tests are present. The three action codes are therefore marked **Implemented — acceptance pending**, not Not started and not Accepted. End-to-end role/scope acceptance and deployment scanner configuration still require verification.


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

- The next CI run identified a second collision in the V4.0 reference seed: `ROLLDOWN_OVERRIDE_SET` uses action number 280. The study-material view action has been moved to 281 in both reference data and migration 28; a fresh pipeline is required to verify the correction.


## Chunk 2 and Chunk 3 continuation — 12 November 2026

This is a progress record, not an acceptance declaration. Work is continuing on `sprint-2-development`; concurrent commits from the other contributor have been retained.

### Changes added in this continuation
- Added live WebSocket backend tests for oversized frames (1009), malformed authentication (4401), malformed JSON (4401), and ignored unknown client frames.
- Added notification notifier tests for post-persistence `NOTIFICATION_CREATED` and `UNREAD_COUNT` delivery, plus suppression of live events for deduplicated notifications.
- Added file-download regression coverage proving INFECTED, QUARANTINED, DELETED and SCAN_FAILED objects remain blocked even when pending downloads are enabled.
- Student creation now validates the section is active and the requested/default admission date falls within the academic session before provisioning a Keycloak login. School class validation also checks institution ownership and active status before applying the class login switch.
- Added StudentCreationService coverage for college-default student-code login/profile/enrolment creation and rejection of out-of-session admission before account provisioning.
- The current branch also contains additional B4 staff authorization, service, and document tests; notification controller tests; and the student creation flow added concurrently. These have been retained rather than overwritten.

### Pipeline and remaining completion gates
- GitHub Actions is triggered on each branch update. Several runs were cancelled by subsequent commits while concurrent work continued; the latest run must be allowed to complete on the consolidated branch before claiming a green gate.
- No local test execution is claimed. The current source state still requires a completed backend, PostgreSQL migration, frontend, CodeQL, dependency and secret-scan result.
- Remaining provider/architecture decisions cannot be implemented safely by assumption: a malware-scanning provider has not been selected, and D5 object storage/expiring-link provider configuration is not present. Local storage remains a documented interim deviation.
- File attachment exists for staff documents, but a generic file-to-domain-record attachment/status-transition workflow is not yet established for all owning record types. Do not infer a universal ownership contract.
- Notification event invocation remains limited to publishers actually present in the repository; absent timetable, assignment, attendance and HOD approval publishers cannot be wired without those domain workflows.
- OD-16 remains open; only SQL-defined staff fields and service-event types are used. OD-134 single-student action code follows the approved API proposal `USER_CREATE` in the current implementation; the user has directed completion without waiting for further approval.
- Stories remain In progress / Tested only after their relevant CI gates complete. No story is marked Accepted by this note.


## Sprint 1 gap repairs — active implementation log

**Scope:** working through the Sprint 1 gap-validation register, prioritising the Chunk 3b repairs and then the remaining gaps. This is an implementation log, not an acceptance statement. The existing 26-action-code status table above is retained unchanged.

### G1 — Tenant-side support

**Status: Built; CI/test validation pending.**

- Added tenant ticket creation authorization using `SUPPORT_TICKET_RAISE`.
- Added `GET /v1/tenants/{tenantId}/support-tickets`; repository query is constrained by both authenticated tenant and authenticated user.
- Added the tenant Support screen for ticket creation, own-ticket list, grant history, grant up to 24 hours, and immediate withdrawal.
- Added a permission-filtered Support navigation item and an `ActionGate` route.
- Added audit events for reads of own support tickets and tenant support grant history.
- Ticket categories remain unspecified; the UI does not invent a category catalogue. Priority values follow the SQL constraint (LOW, MEDIUM, HIGH, CRITICAL).
- Relevant commits include `4f21d6e` (ticket authorization/list endpoint) and later support UI/navigation/audit commits on this branch.

### G2 — Academic structure PATCH and subject groups

**Status: In progress; not yet implemented in this repair pass.**

- Current controller still has create/list endpoints for academic structure; PATCH/If-Match handlers and subject-group/member endpoints remain to be built.
- Subject-group membership is represented by `app.subject_group_member.enrollment_id` in the current schema. Subject-group operations will use `STRUCTURE_MANAGE`; `SUBJECT_ALLOCATE` remains for teacher allocation.
- No G2 story is marked Built or Tested.

### G3 — Exam-day calendar events

**Status: Built; SQL/CI validation pending.**

- Added `EXAM_DAY` to local and university master calendar event handling.
- Added migration `30_v41_exam_day_event_type.sql`, guarded rollback `30r_v41_exam_day_event_type_rollback.sql`, SQL checks `30t_tests_exam_day_event_type.sql`, and registered them in `sql/run_all.sh`.
- Added service tests for typed exam-day creation and local-holiday overlap; updated calendar-day status coverage so `examDay` is derived from typed events rather than title text.
- Master calendar form and local calendar form expose Exam day with a distinct warning badge.
- Product D1 §2.1 / CL-01 says local holidays on exam days are blocked with a warning. The service returns `HOLIDAY_EXAM_CONFLICT`; it does not infer exams from free-text titles.
- Local build and PostgreSQL migration tests have not been run from this environment.

### G4 — Parent/applicant one-time-code login

**Status: Disabled-mode API built; full OTP lifecycle not implemented.**

- Added `GET /v1/auth/otp/status` and `POST /v1/auth/otp/request`, `POST /v1/auth/otp/verify` (also under `/api/auth`).
- Status reports `enabled:false`; request/verify fail closed with 503 `OTP_PROVIDER_NOT_CONFIGURED`; the D5 response includes `retryable:false` and `Cache-Control: no-store`.
- Set `auth.otp.enabled=false` explicitly in base, dev, test and prod application properties.
- Added focused controller unit tests. The mobile-code option remains absent from the login screen.
- Not implemented: SMS sender adapter, OTP issuance/storage/expiry/rate-limits, guardian-only eligibility and Keycloak token exchange. Applicant login remains Phase 2. This is not a complete G4 implementation.

### Validation and CI notes

- No story is marked `Tested` in this log. This environment has GitHub repository access but no working local checkout/runtime for executing Maven, frontend or PostgreSQL commands.
- GitHub Actions runs for intermediate commits have repeatedly been cancelled when concurrent developer commits landed on the same branch. This is not a pass or a code failure. Await a non-cancelled run on the consolidated branch head before marking any affected repair Tested.
- Continue using `sprint-2-development` only. Refresh the branch before each write and preserve concurrent commits; never force-push.


### Follow-up security and enrolment notes

- Pending file downloads now require either the original uploader or an active permission for the file purpose (assignment evaluators are also recognized). Record ownership and ordinary view permission alone do not expose an unscanned file. Regression coverage denies a non-uploader with only the normal view path.
- Student creation now validates active section/class and admission date before allocating/provisioning the student login. The new tests cover college-default login, school class switch disabled, explicit school-login denial, and admission-date rejection before external provisioning.
- The current OpenAPI/import contract exposes the existing imports operation as a USER import; it does not define the CSV row schema or execution contract for student-code legacy imports. The student-code generation and tenant uniqueness guard are implemented, but a separate legacy-code student bulk-import execution path remains open until its contract is defined. Do not silently reinterpret USER imports as student enrolment imports.
- The security/architecture requirements still call for a real malware scanner and object-store-backed 15-minute download links. No provider or deployment credentials are defined in the supplied project configuration. These remain explicit deployment/integration blockers; local storage and the pending scanner are not represented as production-complete.


## Chunk 2 / Chunk 3 continuation — implementation update

- The existing self-service notification endpoints were retained (no duplicate controller was left behind). Inbox listing, unread counts, mark-read and mark-all-read now exclude notifications whose `deliver_after` time has not arrived; mark-read also hides a scheduled notification by returning not-found. Bulk read uses a tenant/recipient-scoped database update, and live unread counts use the same due-only rule. Mark-read/read-all remain intentionally unaudited per the approved decision.
- Added single-student creation at `POST /v1/tenants/{tenantId}/students` (and the legacy `/api` alias). The flow validates active institution, section and academic session, authorizes `USER_CREATE` before consuming an ID sequence, generates the tenant-unique institution student code, creates the profile and enrolment in one database transaction, and uses the approved OD-134 `USER_CREATE` action code.
- College student login defaults on and uses the student code as Keycloak username with no email. School login follows the class-level login switch; an explicit request cannot bypass a disabled switch. The creation response returns the temporary password once for secure printing/delivery. A transaction rollback schedules cleanup of the provisioned Keycloak identity to avoid orphan accounts.
- Added regression coverage for student-code-only Keycloak provisioning, missing ID format, school login switch, admission date bounds, staff-code normalization/uniqueness, staff document verification scope, WebSocket connection limit, heartbeat timeout and token reauthentication.
- The CI workflow now gives each run a unique concurrency group and always runs the secret scan even if an earlier dependency step fails.
- **Validated checkpoint:** GitHub Actions run [43235642537](https://github.com/startcore360/ProjectOne/actions/runs/43235642537), commit `536a65ac0b971b3021f38c9bbbf923ef8444a9e2`, passed Spring Boot, frontend, PostgreSQL migration, CodeQL, dependency and secret checks. Pull-request dependency review was skipped on the push event. This is an intermediate checkpoint; later commits still require a completed pipeline on the consolidated branch before final validation.

### Remaining Chunk 2 / Chunk 3 gates

- Student bulk import with optional legacy student codes and duplicate handling is not yet implemented end-to-end; the existing generic user import does not create student profiles/enrolments.
- Malware scanning provider and D5 object-storage/presigned-link provider remain unselected. Local storage and PENDING scanner behavior therefore remain documented interim deviations; no provider has been invented.
- Generic file-to-domain-record attachment and audit of subsequent scan/status transitions remain incomplete outside the existing staff-document attachment flow.
- Notification triggers can only be connected where publisher workflows exist; timetable, assignment, attendance and HOD approval publishers are still absent from this branch.
- B3/B4/B9 and Chunk 2 remain In progress until the latest consolidated CI run passes and the listed integration dependencies are resolved. No story is marked Accepted.


## Implementation continuation — 13 November 2026

This section records verified repository edits made during the current implementation pass. It is not an acceptance declaration.

### Confirmed changes

- Fixed StaffDocumentService.authorizeProfileScope(...) to authorize using the requested action code. Staff-document listing now checks STAFF_RECORD_VIEW instead of incorrectly requiring STAFF_RECORD_MANAGE; write/attach operations continue to pass STAFF_RECORD_MANAGE.
- Added a focused StaffDocumentServiceTest.viewOnlyPermissionIsUsedWhenListingStaffDocuments regression test for the tenant-scope view-only path and to assert that STAFF_RECORD_MANAGE is not requested by list.
- Added the missing sql/27r_v41_notification_read_state_rollback.sql to remove the read-state index and column introduced by migration 27.
- Registered the migration 27 rollback in the sql/run_all.sh rollback round-trip sequence.

### Commits

- 7586feb8b6e26c2e834f1654492c769946057686 — fix staff document list authorization.
- 27c435898624f06d7a144587b18aed288ff01c1d — add view-only authorization regression test.
- 52e62ee46ce693e11b434260ef1471fb94138445 — add migration 27 rollback.
- 6c940054e9246cdc3750dc882ec41bb9d5bb9f2d — include migration 27 rollback in SQL round-trip.

### Validation status

- These edits have been committed to sprint-2-development.
- The focused test has not yet been executed in this environment.
- The SQL runner and OWASP checks have not yet been executed for these commits.
- No claim is made that the complete branch is accepted or that all Sprint 2 work is finished.
- Outstanding security work, staff identity linkage, staff assignment conflict checks, student bulk enrolment, ID-format UI, notification event coverage, recipient-level database isolation, Chunk 3b G2, and full chunk acceptance remain tracked above until implemented and validated.


### Staff sensitive-field protection — implementation pass 2

- `StaffProfileResponse.from(profile)` now defaults to masking date of birth, personal email, personal mobile and national-ID last four characters.
- The staff directory list/detail paths request sensitive fields only when the active caller is also authorized for `STAFF_RECORD_MANAGE`; otherwise those fields remain null in the response.
- Privileged list/detail reads write a structured `SENSITIVE_VIEW` audit event per staff profile viewed. Audit payloads contain no personal field values.
- Added response-mapping tests for masked default responses and explicitly authorized full responses.
- Commits: `2b4657bbe7829879e5b9bc53bb066c23e3f0b8fc`, `3de41e02fdd1c7e05a90a56f1421a91ef7aa04df`, `ff640b69848be9522283b8a25f99aa4f70fc909e`.
- Validation remains pending; do not mark this story Tested or Accepted until Maven and role-specific API tests pass.


### Staff identity, teacher lookup and assignment integrity — implementation pass 3

- Added idempotent `StaffDirectoryService.ensureStaffAssignment(...)`: an institution/department staff role can create the linked `staff_profile` and scoped `staff_assignment` for a STAFF account, without duplicating an existing profile or active assignment.
- Wired `RoleAssignmentService.assignRole(...)` to invoke staff identity linkage after an institution/department role is assigned to a STAFF account. Tenant-wide roles do not create an institution assignment.
- Added authorized `GET /v1/tenants/{tenantId}/staff?role=TEACHING&institutionId=...&departmentId=...` lookup. It backfills eligible existing teaching-role assignments on demand and returns staff name, staff code, designation and department identity/name without sensitive profile fields. The lookup requires `SUBJECT_ALLOCATE` at the requested institution/department scope.
- Teaching-role codes are grounded in the repository reference data: `HEAD_OF_DEPARTMENT`, `PROFESSOR`, `ASSOCIATE_PROFESSOR`, `ASSISTANT_PROFESSOR`, `LAB_ASSISTANT`, `RESEARCH_ASSISTANT`, `CLASS_TEACHER`, and `SUBJECT_TEACHER`.
- Added overlap validation for active staff assignments at the same institution/department and date range.
- Ending an assignment now checks dependent published timetable slots, active subject allocations, current class-teacher assignments and current mentor assignments. It refuses the change with conflict identifiers so the administrator can reassign or end dependent records first.
- Added focused tests for identity idempotency/creation, teacher lookup institution and department authorization, and blocking an assignment end when a published timetable slot depends on it. Updated the controller test for the expanded lookup query.
- Commits in this batch: `cb94068d57f0cdd944b01acb049a2cbfd7f21f3f`, `0a16665ae10a444d1adece5d680b3d2dcc0bc865`, `f890da59a622f95228334ed929b48656da3f1115`, `9d1cdfc1c5432217fa89c13c914d7f722582971d`, `cc495459de7add5ff0a8bbfc4314be44dcbd192f`, `df9a15d55829bc709cef9bd7f31e167d3ad79616`, `a81447218620537fa72a301f7a79e978cf825014`, `1899255365b179303833c33e8c619b55fbcd5f58`, `b63f0a873a235c5e9ce1bab9c9c52593925106f4`, `2eb42fb5a609a29e08acfea6adb5105c20341c52`, `a1faae78adf91c4a7f8878e2912d0a1dbf064864`, `37953cde57f832c71360155a835cddd548abd9c3`, and `90dbf08d5f03c8262f6557aef91987bb7ebec108`.
- Validation pending: Maven compile/tests, migration-backed integration tests, role assignment integration tests, and CI. These stories remain In Progress, not Tested/Accepted. The manual `POST /staff` onboarding path still needs to be completed so a manually created HR profile provisions/returns login credentials through the existing user-creation flow; current automatic linkage covers staff users receiving an institution/department role through `USER_CREATE` or `ROLE_ASSIGN`.

- Follow-up hardening: staff assignment overlap now includes historical date ranges, not just rows marked ACTIVE; class-teacher and mentor conflicts are checked against both effective_from and effective_to; automatic staff assignments inherit the role assignment's effective dates; teacher-picker SQL handles nullable department/search parameters without ambiguous PostgreSQL parameter types and includes institution-wide teachers in department pickers. Commits: `bc4572d3f1feb2a474d2e02e06931747d3981bd9`, `38127f6f2803b1f8c3c144b3faa2a567689879f3`, `eca97eb831502da3e74c88587970158c7dc870cc`, `2ddbb23f652bd883687af568c1c71a678e949679`, `8306e38ff78b7e62ef7d8a1bd91524c114325d40`, `0d426c8beeb4ff7ec1bc692ce4c839a452c5f523`, and `6647d0d4ff072426fe81c995c364289ce135d43b`.

### Staff official/personal email login decision — implementation pass 4 (in progress)

- Product decision confirmed: capture official and personal email separately; official email is the primary Keycloak username, and personal email is a secondary login identifier for the same account.
- Added app.user_login_alias in sql/32_v41_staff_login_aliases.sql, with tenant-scoped email uniqueness, account ownership, verification state (false by default), tenant RLS, touch trigger and grants. Added rollback and schema contract checks (32r_..., 32t_...).
- Added StudentLoginLookup.accountForVerifiedEmailAlias(...) and routed tenant email identifiers through that lookup in AuthService.keycloakUsernameFor(...). Alias resolution only returns verified aliases; non-alias email login continues through the existing primary-email Keycloak path.
- Commits: 876467bed50be77002e212fb87c532e4a4038086, 3c5ce698e9fce90b2fa1c87befac10ed2ed85f72, b2a780b4201241c833ae13fb0ef91af5584f8cd4, 3bd359d398fdd0cb7dbce231869be0293ee46dd3, 1bbbb5b0e2cff7d133cc6c6988c5703f2a82cd3d.
- NOT COMPLETE: manual staff creation still does not provision a STAFF user account, populate staff_profile.user_account_id, or create the secondary alias. There is not yet an email ownership verification / invitation flow to mark aliases verified. The login resolver is therefore groundwork only; it does not yet make personal-email login available to staff.
- Validation has not been run for these changes. Do not mark the migration, auth flow, or Chunk 3 as Tested/Accepted until SQL migration/rollback, Maven tests, login tests and CI pass.

### Staff account provisioning and profile linkage — implementation pass 5 (in progress)

- Extended the staff-create request with a required official email (the primary Keycloak username) and optional initial role code. Personal email remains a separate staff contact field.
- Added StaffOnboardingService to call the existing UserAccountService with UserType.STAFF, persist the staff profile with the returned userAccountId, register a personal-email alias as unverified, and optionally assign the requested role through RoleAssignmentService.
- Staff creation now returns StaffOnboardingResponse, including the profile, one-time account provisioning response and an explicit flag when personal-email verification is required. The frontend collects both email addresses and displays the temporary password once after creation; it also warns that the personal alias remains disabled until verified.
- Added cleanup of a newly provisioned Keycloak identity if the enclosing account transaction rolls back.
- Hardened migration 32 so primary and secondary email identifiers cannot collide across accounts or duplicate each other for the same account. Rollback updated accordingly.
- Added StaffOnboardingService tests for account/profile linkage, unverified alias persistence and same-address rejection.
- Commits in this pass: a6a661f034c3760d65e80239db4e94683a028d26, 913a378bb7409a82894044dc5f0a9e08cc87cd95, 70a9a713c2c36a6b4fd3d4723ad413316b358dc4, 12b810c191f9bb27fe135cedefd13fcff3b6df21, e446304c5f325d2a6f107dbe5f603026af972284, abb100d4ff1d270ae67dfedcb1e539280bccfa50, fee42f9d95f85c71b284d052a2bc245c4b498976, 5e046a3e1f9ab07e2aad63f5a4ec557e6939c3f4, 328c21081057a9ccf99a4a7ea8d08ca88342b4de, 6d6628964c82f86dae71c55b72759939863b25f1, 011400b27c4cd97e329a4e07c4483642ead12f0c, 4eacf73b65fcee733e624716742157630cf768fd, and 90f8a1828fc5a0e0928832e3aeb2aea2e9ef0b9f.
- IMPORTANT LIMITATION: no email delivery or ownership-verification service currently exists in the repository. The personal email alias is therefore created with is_verified=false and cannot authenticate until a secure verification workflow is implemented. Official email is the primary login and is provisioned by the existing Keycloak flow, which currently sets emailVerified=true.
- Validation remains pending: no Maven/frontend/SQL/OWASP/CI gates have been run for this implementation pass. Keep Chunk 3 In Progress, not Tested/Accepted.
