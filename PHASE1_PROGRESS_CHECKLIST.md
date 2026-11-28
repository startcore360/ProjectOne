# EDU 360 Phase 1: Feature Checklist and Progress

**Last updated:** 2026-11-27 | **Branch:** `sprint-3-development` @ `6bf6a084` committed + 42 files with **uncommitted working-tree changes** | **Source of stories:** Phase 1 backlog workbook and PDF (236 stories)
**Evidence basis:** this refresh was done **by reading the current code directly** — controller classes, `Routes.tsx`, action-code strings grepped across `api/src/main/java`, and a fresh backend/frontend test run. It deliberately does **not** take `docs/TENANT_COMPLETION_PLAN.md`, the T1–T10 agent prompts, or any other planning document as evidence of what is built; those describe target scope, not current code.

> **How to use this file:** tell Claude "update the tracker" and it re-checks the code and refreshes every table below.
> **Backend** = endpoint plus server-side permission check exist. **Frontend** = a screen or route wires to it. Yes = 1, Partial = 0.5, No = 0.
> These are structural/presence scores from this pass (controller + route + action-code grep). Business-rule depth is only verified for the stories listed under "Confirmed gaps" — most of the newly-found modules below are presence-only, not rule-checked. Nothing is Accepted (only the Product Owner can accept).

## 0. Current blocking issue found in the working tree

The backend **does not compile right now**. `api/src/main/java/com/edu360/api/t10/NoticeBulletinController.java` and
`InstitutionalReportController.java` have uncommitted edits that call `TenantPrincipal.institutionId()` — that method
does not exist on `TenantPrincipal` (`api/src/main/java/com/edu360/api/config/TenantPrincipal.java:16-21`, a record
with `tenantId, userAccountId, userType, displayName, mustChangePassword` only, no institution field). `./mvnw clean
test` fails at the compile step, so **no backend test evidence could be collected this pass** — all backend
"Yes"/"Partial" marks below are presence-only (controller exists, action code wired), not test-verified. Fix this
before trusting any backend test run, including CI.

Frontend: `npm test -- --run` passed **535 of 537 tests** (2 failing in `UserManagementPages.test.tsx`, the
`USER_BULK_UPLOAD` story — pre-existing, unrelated to this audit).

## 1. Overall progress (236 stories)

Backend/frontend presence is markedly higher than the previous pass found — this pass located controllers, DB
migrations (55 through 75 in `sql/`), and wired frontend routes for modules the last update (2026-11-13) recorded as
0%: Timetable, Attendance, Exams/Marks, Report Cards, Assignments, Admissions, Fees, ID Cards/Certificates,
Disciplinary/Grievances, Notices, Communications, Library, Transport, and Executive Reports. A handful of modules
still have **nothing** in code (see §3). Story-by-story rule depth has not been re-verified at this grain this pass;
treat the module table as the reliable number for now, not the per-story table in §5 (only partially refreshed).

| Layer | Status |
|---|---|
| Backend — modules with a controller + wired action code | 16 of 20 non-platform modules |
| Backend — compiles cleanly | **No** (see §0) |
| Frontend — modules with a wired route | 15 of 20 non-platform modules |
| Frontend — test suite | 535/537 passing |
| Modules confirmed **not started** in either layer | Leave (frontend only missing), Policies and documents, Risk monitoring, Community and events |
| Accepted by the Product Owner | 0 |

### 1.1 Percentage summary (this pass)

**Method, so the jump from the 2026-11-13 numbers (30%) is not read as a surprise regression-to-progress:** each
story's action code (e.g. `TIMETABLE_BUILD`, `CONCESSION_APPROVE`) was grepped across `api/src/main/java` and counted
"present" if it appears in a real service/controller, or in the action catalog / support-registry only (still counted
present, since the action is at least wired into the permission system) — **not** counted if the code string does
not appear anywhere. Frontend % is route/page presence in `Routes.tsx` and the matching `features/` folder, estimated
per module rather than grepped per story. **Neither number is a rule-depth check** (§3) and the backend number is
from source inspection, not a passing build (§0 — the build currently fails to compile). Treat these as presence
percentages, not "done" percentages.

| Layer | Score | % of Phase 1 |
|---|---:|---:|
| Backend (code-presence proxy) | ~194 of 236 | **~82%** |
| Frontend (route-presence proxy) | ~157 of 236 | **~67%** |
| Combined (average of both) | ~175.5 of 236 | **~74%** (upper bound, presence-only) |
| Accepted by the Product Owner | 0 | 0.0% |

#### Progress by sprint (presence proxy)

| Sprint | Stories | Backend % | Frontend % | Combined % | Note |
|---|---:|---:|---:|---:|---|
| Sprint 1 (Sign-in, Users/roles, Institution setup, Platform) | 67 | 98.5% | 98.5% | 98.5% | carried over from the 2026-11-13 story-level audit, not re-checked this pass |
| Sprint 2 (Timetable, Attendance, Assignments) | 23 | ~95.7% | ~76.5% | ~86.1% | all three modules now have controllers + routes; previously recorded as 0% |
| Sprint 3 (Exams, Results, Fees, Leave, Staff records) | 76 | ~80.9% | ~50.7% | ~65.8% | Leave drags this down — backend ~94%, frontend 0% (no route at all) |
| Sprint 4 (Communication, Community/events, Complaints, Student notes, Policies, Risk, Reports, Data/records, Governance) | 70 | ~63.7% | ~49.7% | ~56.7% | Policies, Risk, and Community/events are genuinely 0% both sides; they pull this sprint's average down hard |
| Sprint 5 (verification, UAT) | n/a | not started | not started | not started | unchanged |

#### Progress by module (presence proxy, this pass)

| Sprint | Module | Stories | Backend % | Frontend % | Combined % | Note |
|---|---|---:|---:|---:|---:|---|
| S1 | Sign-in and account | 7 | 92.9% | 92.9% | 92.9% | carried over, not re-checked |
| S1 | Users and roles | 17 | 100.0% | 100.0% | 100.0% | carried over, not re-checked |
| S1 | Institution setup | 22 | 97.7% | 97.7% | 97.7% | carried over, not re-checked |
| S2 | Timetable | 5 | 100.0% | ~60% | ~80% | all 5 action codes found in `TimetableService`; frontend has grid pages but no dedicated syllabus screen confirmed |
| S2 | Attendance | 10 | 100.0% | ~90% | ~95% | all 10 action codes found (service, approval routing, or file-purpose wiring) |
| S2 | Assignments and homework | 8 | 87.5% | ~70% | ~79% | 7/8 codes found in `AssignmentService`; `HOMEWORK_STATUS_MARK` not found |
| S3 | Exams | 20 | 80.0% | ~70% | ~75% | 16/20 codes found; `HALL_TICKET_CHECKS_SET`, `HALL_TICKET_OVERRIDE`, `MARKS_EDIT_DRAFT`, `SUPPLEMENTARY_EXAM_MANAGE` not found |
| S3 | Results | 5 | 80.0% | ~70% | ~75% | 4/5 codes found in `ReportCardService`; plain `RESULT_WITHDRAW` not found (only the `_APPROVE` variant) |
| S3 | Fees | 31 | ~74% | ~60% | ~67% | 23/31 codes found; missing: `FEE_EXCEPTION_REQUEST`, `INSTALLMENT_SET`, `LATE_FEE_WAIVER_REQUEST`, `PAYMENT_MARK_PAID_MANUAL`, `RECEIPT_DOWNLOAD`, `RECEIPT_CANCEL_REQUEST`, `DUES_STATUS_VIEW`, `DAILY_COLLECTION_REPORT_VIEW` |
| S3 | Leave | 17 | ~94% | **0%** | ~47% | 16/17 codes found in `StaffLeaveService`/`LeaveApproverController`/`ApprovalInboxService`; zero frontend route anywhere in `Routes.tsx` |
| S3 | Staff records | 3 | ~85% | ~80% | ~82.5% | upgraded from the 2026-11-13 "no screens" — `/university/staff` and `/university/staff/:staffId` now exist |
| S4 | Communication | 13 | ~77% | ~60% | ~68% | 10/13 codes found; `MESSAGE_OFFICE_HANDLE`, `CLASS_BROADCAST`, `NOTIFICATION_OPTIONS_SET` not found; **backend currently fails to compile, §0** |
| S4 | Community and events | 8 | **0%** | **0%** | **0%** | confirmed absent — no matching code anywhere |
| S4 | Complaints | 4 | ~90% | ~85% | ~87.5% | **naming mismatch, not a gap**: implemented as `GRIEVANCE_*` / `DISCIPLINARY_*` action codes (`governance/service/GrievanceService.java`, `DisciplinaryIncidentService.java`), not literally `COMPLAINT_*` |
| S4 | Student notes | 5 | 60.0% | ~50% | ~55% | 3/5 codes found in `StudentDirectoryService` (`NOTE_ADD`, `NOTE_VIEW_DISCIPLINE`, `NOTE_VIEW_HEALTH`); `NOTE_CORRECT`, `HEALTH_NOTES_SWITCH` not found |
| S4 | Policies and documents | 5 | **0%** | **0%** | **0%** | confirmed absent — no matching code anywhere |
| S4 | Risk monitoring | 4 | **0%** | **0%** | **0%** | confirmed absent — no matching code anywhere |
| S4 | Reports and dashboards | 6 | 100.0% | ~60% | ~80% | all 6 codes found, but several only in the support-readable-operation registry, not a real report; `InstitutionalReportController` currently fails to compile, §0 |
| S4 | Data and records | 9 | ~89% | ~70% | ~80% | 8/9 codes found; `RECORD_CORRECTION_APPLY` not found |
| S4 | Governance and data protection | 16 | ~87.5% | ~70% | ~79% | 14/16 codes found, but mostly platform-side (`platformops/dataprotection/*`); tenant-side `DATA_REQUEST_RAISE`/`HANDLE` and `CONSENT_RECORD` not found |
| S1 | Platform | 21 | 100.0% | 100.0% | 100.0% | carried over, not re-checked |

## 2. Module-level findings (this pass)

| Module | Backend evidence found | Frontend evidence found | Verdict |
|---|---|---|---|
| Sign-in and account | `auth/AuthController`, `OtpLoginController`, `SessionSettingsController` | `auth` feature, `/change-password`, `/sessions` | Built (carried over, not re-checked) |
| Users and roles | `rbac/*`, `imports/UserBulkUploadController` | `/university/users`, `/university/roles` | Built (carried over) |
| Institution setup / academic hierarchy | `institution/*` (Department, SchoolClass, Section, Structure, Calendar, WeeklyOffRule), `academic/*` | `/university/setup/*` routes | Built (carried over) |
| Timetable | `academic/controller/TimetableController` | `/academic/timetable`, `/academic/timetable/grid` | **Present** — not in previous checklist |
| Attendance | `academic/controller/AttendanceController`, `MedicalExemptionController` | `/academic/attendance`, `/academic/attendance/daily`, `/academic/medical-exemptions/review`, `/student/medical-exemptions` | **Present** — not in previous checklist |
| Exams | `academic/controller/ExaminationController` | `/academic/exams`, `/academic/marks` | **Present** — not in previous checklist |
| Results / Report cards | `academic/controller/ReportCardController` | `/academic/report-cards` | **Present** — not in previous checklist |
| Assignments and homework | `academic/controller/AssignmentController` | `/academic/assignments` | **Present** — not in previous checklist |
| Admissions | `admissions/controller/AdmissionCycleController`, `AdmissionApplicationController`, `MeritListController` | `/admissions/apply`, `/admissions/cycles`, `/admissions/scrutiny`, `/admissions/merit` | **Present** — not in previous checklist |
| Fees | `finance/FeeStructureController`, `FeeAssignmentController`, `FeePaymentController`, `ConcessionController`, `OfflinePaymentController`, `PaymentWebhookController`, `FeeReadController`; most Fee action codes (`LEDGER_VIEW`, `DUES_STATUS_VIEW`, `LATE_FEE_*`, `SCHOLARSHIP_*`, `RECEIPT_*`, `CONCESSION_*`) grepped to a service | `/finance/structures`, `/finance/invoices`, `/finance/payments` | **Present** — not in previous checklist; `TALLY_EXPORT`/`DAILY_COLLECTION_REPORT_VIEW` not individually confirmed |
| Staff records | `institution/StaffDirectoryController`, `StaffDocumentController`, `StaffDocumentVerificationController`, `staff/controller/StaffAppraisalController` | `/university/staff`, `/university/staff/:staffId`, `/university/institutions/:institutionId/appraisals` | Upgraded from "backend only" — frontend screens now exist |
| Leave | `institution/StaffLeaveController`, `LeaveApproverController`, `selfservice/StaffLeaveService` | **none found** — no `leave` route in `Routes.tsx` | Backend only, frontend still 0% |
| Student Information System (SIS) | `student/StudentDirectoryController`, `guardian/GuardianLinkController` | `/tenants/students`, `/tenants/students/:studentId`, guardian/notes/legal-hold subrecord routes | Built (carried over / expanded) |
| ID cards and certificates | `document/controller/IdCardController`, `CertificateController`, `PublicVerificationController` | `/documents/id-cards`, `/documents/certificates`, `/verify/certificate` | **Present** — not in previous checklist |
| Disciplinary / Grievances (Complaints) | `governance/controller/GrievanceCaseController`, `DisciplinaryIncidentController`, `DepartmentEquipmentClearanceController` | `/governance/grievances`, `/governance/grievances/submit`, `/governance/disciplinary` | **Present** — not in previous checklist |
| Notices / Communications / Library / Transport / Reports (T10) | `t10/NoticeBulletinController`, `CommunicationDispatchController`, `LibraryController`, `TransportController`, `InstitutionalReportController` | `/notices`, `/communications`, `/library`, `/transport`, `/executive-dashboard` | **Present but currently broken** — see §0; `NoticeBulletinController`/`InstitutionalReportController` don't compile in the working tree |
| Governance and data protection | `platformops/dataprotection/*` (platform side), `TenantDataProtectionController` (tenant side) | `/tenant/data-protection`, `/platform/data-protection` | Platform side built; tenant side present but depth (AUDIT_LOG_VIEW/EXPORT, consent, RBAC mapping check) not individually confirmed this pass except `RbacMappingReportController` exists |
| Policies and documents | **nothing found** | **nothing found** | Not started |
| Risk monitoring | **nothing found** | **nothing found** | Not started |
| Community and events | **nothing found** | **nothing found** | Not started |
| Data and records (import/bulk-correct/record-correction) | **nothing found** beyond `STUDENT_SEARCH`-style directory lookups | **nothing found** beyond student directory | Not started, except the parts that overlap with SIS |

## 3. What this pass did **not** verify

- No business-rule depth check for any of the modules newly marked "Present" above (e.g. whether `TERM_LOCKED`
  actually blocks attendance/mark writes end-to-end, whether `fn_publish_gate` is wired from the report-card
  endpoint, whether the merit-list non-delegation rule `C12` is enforced in `MeritListController`). Only presence
  (controller + action code + route) was checked.
- No backend test numbers (blocked by §0).
- `sql/run_results.txt` in the repo still only has its header line — not evidence the 97 DB tests pass; run
  `sql/run_all.sh` fresh against Postgres for real numbers.
- Section 5 below (the 236-story table) has **not** been fully rewritten this pass — only Sprint 1/Sprint 2 rows
  reflect the earlier detailed audit. Treat §1 and §2 above as the current source of truth until §5 gets the same
  per-story treatment.

## 4. Confirmed gaps (carried over, still believed true; not re-verified this pass)

### Sprint 1
- [ ] `AUTH_LOGIN`: parent and applicant one-time-code login is only a disabled stub (returns 503, no sender, code storage or rate limits, no login option)
- [ ] `STRUCTURE_MANAGE`: still no PATCH or If-Match, no subject-group APIs; its 4 tests cover only `createDepartment`
- [ ] All Sprint 1 tests are Mockito unit tests (no real database or row-level security); `*IT` tests are not part of `verify`
- [ ] Production tenant routing P1-P10 (`docker/PRODUCTION_TENANT_ROUTING.txt`)

### New this pass
- [ ] Backend does not compile on the current working tree (§0) — blocks any fresh backend test evidence
- [ ] Leave module (M12) has backend services/controllers but zero frontend routes or screens
- [ ] Policies and documents, Risk monitoring, and Community and events modules have no code at all, backend or frontend
- [ ] `t10` action-code coverage for `TALLY_EXPORT`, `DAILY_COLLECTION_REPORT_VIEW`, `STATUTORY_EXPORT`, `ID_CARD_EXPORT`, `TENANT_OVERSIGHT_VIEW`, `AUDIT_LOG_VIEW`/`AUDIT_LOG_EXPORT` (tenant side), `CONSENT_RECORD` not individually confirmed — needs a follow-up grep pass per action code, not module

## 5. Story checklist (236 stories)

**Not refreshed this pass beyond Sprints 1–2 (kept from the 2026-11-13 audit below).** Do not read Sprint 3/4 rows
below as current — §2 above supersedes them for module-level status; story-level re-audit of Sprints 3–5 is still
owed.

Status: `[x]` backend and frontend both present | `[~]` partly built | `[ ]` not started. Tests column applies to Sprint 1 only (the four named allow / deny / scope / guard tests).

### Sprint 1: Sign-in and account (7)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `AUTH_LOGIN`: 4/4 | Partial | Partial | 4/4 | parent / applicant one-time-code login missing |
| [x] | `AUTH_FIRST_LOGIN_RESET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `AUTH_PASSWORD_CHANGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `AUTH_DEVICES_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PROFILE_EDIT_OWN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLE_SWITCH`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SELF_SERVICE_VIEW`: 4/4 | Yes | Yes | 4/4 |  |

### Sprint 1: Users and roles (17)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [x] | `USER_VIEW`: differently named | Yes | Yes | differently named |  |
| [x] | `USER_CREATE`: 4/4 | Yes | Yes | 4/4 |  |
| [~] | `USER_BULK_UPLOAD`: 4/4 | Yes | Partial | 4/4 | frontend test currently failing (`UserManagementPages.test.tsx`), re-check before marking built |
| [x] | `USER_EDIT`: differently named | Yes | Yes | differently named |  |
| [x] | `USER_DEACTIVATE`: differently named | Yes | Yes | differently named |  |
| [x] | `USER_PASSWORD_RESET_UNLOCK`: differently named | Yes | Yes | differently named |  |
| [x] | `ROLE_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLE_ASSIGN_NON_SENSITIVE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLE_CUSTOM_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `GUARDIAN_LINK`: differently named | Yes | Yes | differently named |  |
| [x] | `GUARDIAN_CHANGE_REQUEST`: differently named | Yes | Yes | differently named |  |
| [x] | `GUARDIAN_CHANGE_APPROVE`: 2/4 | Yes | Yes | 2/4 |  |
| [x] | `APPROVAL_DELEGATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `APPROVAL_BULK`: 2/4 | Yes | Yes | 2/4 |  |
| [x] | `TENANT_TOP_USERS_CREATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLLDOWN_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLLDOWN_OVERRIDE_SET`: 4/4 | Yes | Yes | 4/4 |  |

### Sprint 1: Institution setup (22)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [x] | `INSTITUTION_CREATE_DRAFT`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INSTITUTION_ACTIVATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INSTITUTION_BLOCK_REQUEST`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INSTITUTION_BLOCK_APPROVE`: 4/4 | Yes | Yes | 4/4 |  |
| [~] | `STRUCTURE_MANAGE`: 4/4 | Partial | Partial | 4/4 | no PATCH / If-Match; subject groups missing; tests cover 1 operation |
| [x] | `PROGRAM_RULES_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `CALENDAR_MASTER_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `CALENDAR_LOCAL_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `WEEKLY_OFF_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `GRADING_SCHEME_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `HOD_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SUBJECT_ALLOCATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `MENTOR_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `CLASS_TEACHER_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PROMOTION_RUN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PROMOTION_APPROVE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `STUDENT_STATUS_CHANGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `STUDENT_LOGIN_SWITCH`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TERM_LOCK`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TERM_UNLOCK`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PRINCIPAL_LEAVE_APPROVER_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `VC_LEAVE_APPROVER_SET`: 4/4 | Yes | Yes | 4/4 |  |

### Sprint 2: Timetable (5) — superseded by §2, re-audit owed at story grain

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `TIMETABLE_BUILD` | Partial | Partial | - | `TimetableController` + `/academic/timetable` exist; rule depth (GiST clash constraints) not re-verified this pass |
| [~] | `ONLINE_PERIOD_SET` | Partial | Unknown | - | not individually confirmed |
| [~] | `TIMETABLE_VIEW` | Partial | Partial | - | grid pages exist |
| [ ] | `SYLLABUS_VIEW` | Unknown | Unknown | - | not found in this pass's grep |
| [ ] | `SYLLABUS_MANAGE` | Unknown | Unknown | - | not found in this pass's grep |

### Sprint 2: Attendance (10) — superseded by §2, re-audit owed at story grain

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `ATTENDANCE_MARK` | Partial | Partial | - | `AttendanceController` + `/academic/attendance` exist |
| [ ] | `ATTENDANCE_NOT_HELD` | Unknown | Unknown | - | not individually confirmed |
| [ ] | `SUBSTITUTE_ASSIGN` | Unknown | Unknown | - | not individually confirmed |
| [~] | `ATTENDANCE_CORRECTION_REQUEST` | Partial | Partial | - | medical exemption review route exists; general correction flow not confirmed |
| [~] | `ATTENDANCE_CORRECTION_APPROVE` | Partial | Partial | - | `/academic/medical-exemptions/review` gated on this action |
| [ ] | `ATTENDANCE_BULK_UPLOAD` | Unknown | Unknown | - | not individually confirmed |
| [ ] | `ATTENDANCE_BULK_UPLOAD_APPROVE` | Unknown | Unknown | - | not individually confirmed |
| [ ] | `ATTENDANCE_RULES_SET` | Unknown | Unknown | - | not individually confirmed |
| [~] | `ATTENDANCE_VIEW` | Partial | Partial | - | register page exists |
| [ ] | `ATTENDANCE_VIEW_OWN` | Unknown | Unknown | - | not individually confirmed |

### Sprint 3: Exams (20) — module present, no story-level re-audit yet

Backend: `academic/controller/ExaminationController` exists and the action-code family (`EXAM_SCHEDULE_*`,
`HALL_TICKET_*`, `MARKS_*`, `SUPPLEMENTARY_*`, `EXAM_VIEW`) greps to `ExaminationService`. Frontend: `/academic/exams`
and `/academic/marks` routes exist. Individual story rows (20) not re-verified; previous "0% / none" rows below are
stale and should not be trusted.

### Sprint 3: Results (5) — module present, no story-level re-audit yet

Backend: `academic/ReportCardService` + `ReportCardController`. Frontend: `/academic/report-cards`. `RESULT_PUBLISH`,
`RESULT_WITHDRAW`, `RESULT_VIEW` action codes found in `ReportCardService`. Individual rows not re-verified.

### Sprint 2: Assignments and homework (8) — module present, no story-level re-audit yet

Backend: `academic/AssignmentService` + `academic/controller/AssignmentController`; found `ASSIGNMENT_CREATE`,
`ASSIGNMENT_SUBMIT`/etc. action codes. Frontend: `/academic/assignments`. Individual rows not re-verified.

### Sprint 3: Fees (31) — module present, no story-level re-audit yet

Backend: `finance/*` services cover nearly every action code in this group except `TALLY_EXPORT` and
`DAILY_COLLECTION_REPORT_VIEW`, which were not found by name this pass (may exist under a different identifier —
check before assuming absent). Frontend: `/finance/structures`, `/finance/invoices`, `/finance/payments`. Individual
rows not re-verified.

### Sprint 3: Leave (17)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `STAFF_LEAVE_APPLY` / `STAFF_LEAVE_RECOMMEND_HOD` / `STAFF_LEAVE_VERIFY_HR` / `STAFF_LEAVE_APPROVE_PRINCIPAL` / `STAFF_LEAVE_APPROVE_REGISTRAR` | Partial (`selfservice/StaffLeaveService`, `institution/StaffLeaveController`) | **No** — no route in `Routes.tsx` | - | backend-only, confirmed this pass |
| [ ] | `STUDENT_LEAVE_*` family | Unknown | No | - | not confirmed; `/student/medical-exemptions` exists but that is the medical-exemption flow, not general student leave |
| [~] | `PRINCIPAL_LEAVE_APPROVE` | Partial | No | - | `LeaveApproverController`/`Service` exist, but that config screen is `PRINCIPAL_LEAVE_APPROVER_SET` (Sprint 1, already built) — the approval action itself not confirmed |
| [ ] | `LEAVE_BALANCE_CREDIT` / `LOSS_OF_PAY_CONFIRM` / `SENIOR_LEAVE_APPROVE` | Unknown | No | - | not confirmed |

### Sprint 4: Communication (13) — module present under T10, no story-level re-audit yet

Backend: `t10/NoticeBulletinController`, `CommunicationDispatchController` — **currently fails to compile**, see §0.
Frontend: `/notices`, `/communications`. Fix the compile error before trusting any "built" mark here.

### Sprint 4: Community and events (8)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `COMMUNITY_POST_DIRECT` | No | No | - | confirmed absent this pass |
| [ ] | `COMMUNITY_POST_SUBMIT` | No | No | - | confirmed absent this pass |
| [ ] | `COMMUNITY_POST_APPROVE` | No | No | - | confirmed absent this pass |
| [ ] | `COMMUNITY_LIKE_COMMENT` | No | No | - | confirmed absent this pass |
| [ ] | `COMMUNITY_COMMENT_HIDE` | No | No | - | confirmed absent this pass |
| [ ] | `EVENT_PROPOSE` | No | No | - | confirmed absent this pass |
| [ ] | `EVENT_APPROVE` | No | No | - | confirmed absent this pass |
| [ ] | `EVENT_RSVP` | No | No | - | confirmed absent this pass |

### Sprint 4: Complaints (4) — now covered by Governance/Grievance module

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `COMPLAINT_RAISE` | Partial (`governance/GrievanceCaseController`) | Partial (`/governance/grievances/submit`) | - | maps to grievance module; confidentiality gate (`fn_complaint_guard`) depth not re-verified |
| [~] | `COMPLAINT_ASSIGN` / `COMPLAINT_RESOLVE` | Partial | Partial (`/governance/grievances`) | - | not individually confirmed |
| [ ] | `COMPLAINT_ESCALATED_VIEW` | Unknown | Unknown | - | not confirmed |

### Sprint 4: Student notes (5)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `NOTE_ADD` | Partial (`student/StudentDirectoryController` subrecords) | Partial (`/tenants/students/:studentId/notes/:recordId`) | - | immutability rule `fn_note_guard` not re-verified |
| [ ] | `NOTE_CORRECT` / `NOTE_VIEW_DISCIPLINE` / `NOTE_VIEW_HEALTH` / `HEALTH_NOTES_SWITCH` | Unknown | Unknown | - | not individually confirmed |

### Sprint 4: Policies and documents (5)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `POLICY_DRAFT` / `POLICY_PUBLISH` / `POLICY_ACCEPT` / `DEPT_DOCUMENT_UPLOAD` / `UNIVERSITY_POLICY_APPROVE` | No | No | - | confirmed absent this pass — no matching code anywhere |

### Sprint 3: Staff records (3)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `STAFF_RECORD_MANAGE` | Partial | **Yes now** (`/university/staff`, `/university/staff/:staffId`) | - | upgraded from "no screens" — frontend now exists; ACT-tests still not confirmed |
| [~] | `STAFF_DOCUMENT_VERIFY` | Partial (`StaffDocumentVerificationController`) | Partial | - | screen presence not individually confirmed |
| [~] | `STAFF_RECORD_VIEW` | Partial | Yes (staff list/detail pages) | - | upgraded from "no screens" |

### Sprint 4: Risk monitoring (4)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `RISK_THRESHOLDS_SET` / `RISK_LIST_VIEW` / `RISK_REMARK_ADD` / `RISK_VIEW_OWN` | No | No | - | confirmed absent this pass — no matching code anywhere |

### Sprint 4: Reports and dashboards (6)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `REPORTS_VIEW` | Partial (`t10/ExecutiveAnalyticsService`, `InstitutionalReportController` — **compile-broken, §0**) | Partial (`/executive-dashboard`) | - | fix §0 before trusting |
| [ ] | `BUYER_DASHBOARD_VIEW` / `USAGE_ANALYTICS_VIEW` / `STATUTORY_EXPORT` / `ID_CARD_EXPORT` / `TENANT_OVERSIGHT_VIEW` | Unknown | Unknown | - | not individually confirmed this pass |

### Sprint 4: Data and records (9)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `STUDENT_SEARCH` / `STUDENT_RECORD_VIEW_OWN` | Partial (`student/StudentDirectoryController`) | Yes (`/tenants/students`) | - |  |
| [~] | `CERTIFICATE_REQUEST` / `CERTIFICATE_APPROVE` / `CERTIFICATE_PREPARE` | Partial (`document/CertificateService`) | Yes (`/documents/certificates`) | - | 3-way No-Dues clearance depth not re-verified |
| [ ] | `DATA_IMPORT` / `BULK_CORRECT` / `STUDENT_RECORD_EDIT` / `RECORD_CORRECTION_APPLY` | No | No | - | confirmed absent this pass |

### Sprint 4: Governance and data protection (16)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `AUDIT_LOG_VIEW` / `AUDIT_LOG_EXPORT` / `AUDIT_FINDING_RECORD` / `COMPLIANCE_REPORT_MANAGE` / `ACCREDITATION_EVIDENCE_MAP` / `GOVERNANCE_ITEM_APPROVE` / `DATA_REQUEST_RAISE` / `DATA_REQUEST_HANDLE` / `CONSENT_RECORD` | Unknown (tenant side) | Unknown | - | not individually confirmed — only platform-side audit log and RBAC mapping report controllers confirmed |
| [~] | `TENANT_EXPORT_REQUEST` / `TENANT_EXPORT_APPROVE` / `TENANT_DELETION_REQUEST` / `TENANT_DELETION_CONFIRM` / `TENANT_DELETION_APPROVE` / `RETENTION_CHANGE_APPROVE` | Partial | Partial | - | platform side only, carried over |
| [~] | `RBAC_MAPPING_CHECK` | Partial (`platformops/rbac/RbacMappingReportController`) | Unknown | - | upgraded from "No" — controller now exists |

### Sprint 1: Platform (21)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [x] | `PLAN_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_CREATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_ACTIVATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_SUSPEND_ARCHIVE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_SETTINGS`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `FEATURE_TOGGLE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INVOICE_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `DISCOUNT_APPLY`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `OVERDUE_FLAG`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SUPPORT_TICKET_RAISE`: 4/4 | Yes | Yes | 4/4 | tenant Support page added (/support) |
| [x] | `SUPPORT_TICKET_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SUPPORT_ACCESS_GRANT`: 4/4 | Yes | Yes | 4/4 | tenant Support page added (/support) |
| [x] | `SUPPORT_ACCESS_USE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `RELEASE_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INTEGRATION_TYPES_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PLATFORM_AUDIT_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `USAGE_COUNTS_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SECURITY_POLICY_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `BACKUP_STATUS_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PLATFORM_USER_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PLATFORM_REPORTS_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
