# Staff / teaching side

This covers teaching and support staff below the admin layer: Subject Teachers, Class
Teachers, HOD (as a staff member rather than an approver, see `tenant-admin.md` for the
HOD approver view), and general staff self-service (leave, appraisal, notifications).

## 1. Landing, sign-in and dashboard for this audience

**✅ Built today.** Same sign-in mechanism as every tenant audience: the tenant subdomain's
`Host` header picks the Keycloak realm, `POST /api/auth/login`
(`api/src/main/java/com/edu360/api/auth/AuthController.java:58`) relays the attempt, and
the resulting token carries a `TenantPrincipal` with `userType = STAFF`
(`com.edu360.api.identity.UserType`, referenced at
`api/src/main/java/com/edu360/api/selfservice/SelfServiceController.java:98`). After sign-in
a staff member lands on `/dashboard` (`app/src/routes/Routes.tsx:296`), then uses
`/self-service` (`Routes.tsx:304`, wrapped in `TenantUserRoute`) for their own leave/
notifications/profile, plus whichever `/academic/...` routes their role's action gate
allows: `/academic/timetable`, `/academic/attendance`, `/academic/marks`,
`/academic/assignments`, `/academic/exams`, `/academic/report-cards` (`Routes.tsx:219-228`,
each wrapped in `<ActionGate anyOf={TENANT_ROUTE_ACTIONS....}>`).

🔜 **V4.0 target:** per-role dashboards (D7/D10) for each of the 15 school-level and 33
college-level roles that include teaching staff — not yet a distinct dashboard per role in
the frontend today.

## 2. What exists in code today

Staff self-service is its own controller,
`api/src/main/java/com/edu360/api/selfservice/SelfServiceController.java`, tagged
`[SELF_SERVICE_VIEW / Action #7]` (line 30). Every method extracts a `TenantPrincipal` from
`Authentication` and checks `principal.userType() == UserType.STAFF` before doing anything
staff-specific (e.g. leave balances/types/submission, lines 98, 111, 127) — a staff-only
guard enforced in the controller itself via `AccessDeniedException`, separate from the
tenant action-catalog checks used elsewhere.

| Action | Endpoint | Controller line | Notes |
|---|---|---|---|
| View own notifications | `GET /api/tenants/{tenantId}/me/notifications` | line 41 | any signed-in tenant user, not staff-only |
| Mark a notification read | `POST .../me/notifications/{notificationId}/read` | line 68 | own record only — always scoped to `principal.userAccountId()` |
| List available staff leave types | `GET /api/tenants/{tenantId}/me/leave-types` | line 91 | staff only; 403 for non-staff via `AccessDeniedException` (line 99) |
| Get own leave balances | `GET /api/tenants/{tenantId}/me/leave-balances` | line 104 | branches to `StaffLeaveService.ownBalances` for staff, `selfService.getLeaveBalances` otherwise |
| Submit a leave request | `POST /api/tenants/{tenantId}/me/leave-requests` | line 119 | `staffLeaveService.submit(tenantId, principal, request)` — "into the configured approval chain" per the method's own `@Operation` summary (line 120); staff-only, else 403 |
| Cancel own leave request | `POST .../me/leave-requests/{leaveRequestId}/cancel` | line 133 | "before its start date" per summary; own record only |
| View staff appraisal cycles/submissions | see `tenant-admin.md` table | `StaffAppraisalController.java:26,50` | create/view are institution-scoped; scoring (`hod-score`, `principal-review`) is reviewed in `tenant-admin.md` since those are approver actions, not the staff member's own |

Marking/attendance/assignment/timetable flows that a teacher actually performs day to day
live in `api/src/main/java/com/edu360/api/academic/controller/`:
`AttendanceController.java`, `AssignmentController.java`, `ExaminationController.java`,
`TimetableController.java`, `ReportCardController.java`. These five were opened and read
line-by-line for this update (previously only cited from the graph hub list). Each calls
`PermissionEvaluatorService.authorize(...)` with an action code and an institution/
department scope (mechanism explained in full in `tenant-admin.md` §2).

**✅ API + ✅ Screen** for all rows below unless noted — each has a wired frontend route in
`app/src/routes/Routes.tsx:219-228` behind an `ActionGate`.

| Endpoint | Controller:line | Service method | Action code | DB table(s) | Screen |
|---|---|---|---|---|---|
| `POST /api/tenants/{t}/attendance/sessions` | `AttendanceController.java:10` | `AttendanceService.createSession` | `ATTENDANCE_MARK` / `ATTENDANCE_NOT_HELD` (`AttendanceService.java:13,34`) | `app.attendance_session`, `app.attendance_entry` | `/academic/attendance`, `/academic/attendance/daily` (`DailyAttendanceSheetPage`, `AttendanceRegisterPage`) |
| `POST .../attendance/entries` | `AttendanceController.java:14` | `AttendanceService.recordEntries` | `ATTENDANCE_MARK` | `app.attendance_entry` | same screens |
| `GET .../attendance/register` | `AttendanceController.java:23` | `AttendanceService.register` | `ATTENDANCE_VIEW` | `app.attendance_entry`, `app.attendance_session` | `AttendanceRegisterPage` |
| `PUT .../attendance/thresholds` | `AttendanceController.java:27` | `AttendanceService.updateThreshold` | `ATTENDANCE_RULES_SET` | `app.attendance_threshold_policy` | ✅ API only, no dedicated threshold-editor screen found under `app/src/features/academic/` |
| `POST /api/tenants/{t}/academic/assignments` | `AssignmentController.java:26-29` | `AssignmentService.create` | `ASSIGNMENT_CREATE` (`AssignmentService.java:42`) | `app.assignment` | `/academic/assignments` (`AssignmentManagementPage`) |
| `POST .../assignments/{id}/submissions` (multipart) | `AssignmentController.java:39-45` | `AssignmentService.submit` | `ASSIGNMENT_SUBMIT` (student-side, `AssignmentService.java:106`) | `app.assignment_submission` | same screen |
| `POST .../assignments/submissions/{id}/grade` | `AssignmentController.java:47-51` | `AssignmentService.grade` | `ASSIGNMENT_EVALUATE` (`AssignmentService.java:168`) | `app.submission_grade` | same screen |
| `GET/POST /api/tenants/{t}/academic/timetable` + `/slots*` | `TimetableController.java:9-26` | `TimetableService` (`list/createSlot/updateSlot/deleteSlot/publishSlot`) | `TIMETABLE_VIEW` / `TIMETABLE_BUILD` / `ONLINE_PERIOD_SET` (`TimetableService.java:44,55,285`) | `app.timetable_slot`, `app.room`, `app.meeting_link` | `/academic/timetable`, `/academic/timetable/grid` (`TimetableGridView`, `TimetableGridPage`) |
| `POST .../timetable/meeting-link-access` | `TimetableController.java:26` | `TimetableService.accessOnlineMeeting` | no explicit `authorize` call seen — gated by a time-limited `accessToken`, not an action code | `app.online_session_access_log` | same screen |
| `POST /api/tenants/{t}/academic/exams/cycles` | `ExaminationController.java:23-27` | `ExaminationService.createCycle` | `EXAM_SCHEDULE_INTERNAL` (`ExaminationService.java:30`) | `app.exam_cycle` | `/academic/exams` (`ExamSchedulePage`) |
| `POST .../exams/schedules` | `ExaminationController.java:29-33` | `ExaminationService.schedule` | `EXAM_SCHEDULE_INTERNAL` | `app.exam_schedule` | same screen |
| `POST .../exams/schedules/{id}/invigilators/{staffUserId}` | `ExaminationController.java:40-45` | `ExaminationService.assignInvigilator` | `EXAM_SCHEDULE_INTERNAL` | `app.exam_invigilator` | same screen |
| `POST .../exams/marks` | `ExaminationController.java:47-51` | `ExaminationService.enterMarks` | `MARKS_ENTER` (`ExaminationService.java:92`) | `app.exam_mark` | same screen |
| `POST .../exams/marks/{id}/course-teacher-approve` | `ExaminationController.java:53-57` | `ExaminationService.courseTeacherApprove` | `MARKS_SUBMIT` (`ExaminationService.java:137`) | `app.exam_mark` | same screen — this confirms the §3 note below that Class Teacher gets `MARKS_SUBMIT`, a lesser action than a publish-level grant |
| `POST .../exams/marks/{id}/external-moderation` | `ExaminationController.java:59-64` | `ExaminationService.externalModerate` | `MARKS_EXTERNAL_UPLOAD` (`ExaminationService.java:163`) | `app.exam_mark` | ✅ API only — no external-moderation UI found under `app/src/features/academic/` |
| `GET /api/tenants/{t}/academic/report-cards/{id}` | `ReportCardController.java:18-21` | `ReportCardService.get` | `RESULT_VIEW` (`ReportCardService.java:46`) | `app.report_card` | `/academic/report-cards` (`ReportCardViewerPage`) |
| `POST .../report-cards/{id}/publish` | `ReportCardController.java:23-26` | `ReportCardService.publish` | `RESULT_PUBLISH` (`ReportCardService.java:60`) | `app.report_card` | same screen — note this is institution-scoped `authorize`, a materially bigger grant than `MARKS_SUBMIT` above, consistent with "teachers enter marks but never approve results" |

All five controllers resolve the acting principal the same way: a private
`principal(auth, tenantId)` helper requires a `TenantPrincipal` whose `tenantId()` matches
the path, else `UnauthorizedException`/`ResourceNotFoundException` — no separate per-method
role check beyond the `authorize(...)` calls in the table.

Notices a staff member reads come from the same `SelfServiceController.getNotices(...)`
(line 156-160) — published tenant notices, read-only, no role branch beyond being a
signed-in tenant principal.

## 3. What the V4.0 spec plans

- **Leave approval chains and the "sandwich rule":** the spec names a configurable
  leave-sandwich concept that Admin configures but never approves (D2/D3, per
  `V4.0_SPEC_SUMMARY.md` §2) and an approver-eligibility rule (`fn_leave_approver_not_subject`,
  D4 §3) blocking a leave subject from being named their own approver. The code's
  `staffLeaveService.submit(...)` comment ("into the configured approval chain") suggests
  the chain concept is at least partly wired, but the sandwich-rule specifically and the
  `fn_leave_approver_not_subject` DB guard live only in the spec's SQL bundle
  (`docs/EDU360_V4.0_Final/.../sql/02_rules.sql`), not the repo-root `sql/` (there is none)
  — don't assume the application layer enforces it without checking
  `StaffLeaveService`/`LeaveApproverService` directly.
- **Class Teacher holds `M` (Manage), never `F` (Full), on Exams** — a deliberately
  narrower letter than a Full grant specifically so "teachers enter marks but never
  approve results" (D3 Roll-down/Matrix notes, `V4.0_SPEC_SUMMARY.md` §2). This is a
  spec-level rule about the permission matrix; the equivalent enforcement in code would be
  whichever action codes `MarksEntryRequest`/`ExaminationService`
  (per the graph hub list) actually grant to the `SUBJECT_TEACHER`/`CLASS_TEACHER` role in
  `app.role_action` — not independently re-verified in this pass.
- **KPI appraisals (T3 agent prompt scope):** `docs/tenant-agent-prompts/
  T3-staff-hr-leaves-and-appraisals.md` describes the full staff HR module (service book,
  credential verification, leaves, delegations D-001, KPI appraisals) as part of Migrations
  59-60 — the `StaffAppraisalController` endpoints above are the real, partial slice of
  this that exists; the service-book and credential-verification parts of that module
  should be checked separately before assuming they are built.
- **71-role catalogue includes staff-adjacent roles** this file doesn't cover in depth:
  Librarian, Lab Assistant, IT Administrator, Office Staff, Transport Staff, Hostel Staff
  (D3 Role Catalogue). `IT Administrator` is explicitly scoped to accounts/security/backup
  only — no academic or finance data (`V4.0_SPEC_SUMMARY.md` §2) — a restriction worth
  remembering if an IT-admin-facing screen is ever built.

## 4. Role restrictions

### Most senior/complex role in this audience: Head of Department (as approver)

HOD is the most complex role a "staff" person can hold, because it sits on both sides: a
teaching role with its own leave/appraisal self-service, *and* an approver for
department-scoped actions (appraisal `hod-score`, subject allocation, etc.). The HOD's
restrictions are governed by the exact same `PermissionEvaluatorService` mechanism
documented in full in `tenant-admin.md` §4 — this section gives the staff-specific slice of
it rather than repeating the whole mechanism.

**What it cannot do, with the mechanism:**
- **Cannot score or approve its own appraisal submission.** If the HOD is also the subject
  of the submission being scored, `SELF_APPROVAL_FORBIDDEN` fires before any role lookup
  (`PermissionEvaluatorService.java:197-202`) — the HOD must have their own cycle scored by
  the Principal instead, never by themselves.
- **Cannot act outside its own department.** HOD gets no top-post coverage branch in
  `topPostCovers(...)` (`PermissionEvaluatorService.java:486-506`) — a `DEPARTMENT`-scoped
  assignment must match the target's exact department ID (`sameScope(...)`, lines 479-483)
  or the action is denied `NO_ACTIVE_ROLE_IN_SCOPE` outright, with no exception.
- **Cannot submit or cancel another staff member's leave request.** `SelfServiceController`
  always scopes leave submission/cancellation to `principal.userAccountId()`
  (lines 119-144) — there is no "HOD submits leave on behalf of" endpoint in this
  controller; this is a code-level restriction, not merely a UI omission.
- **Cannot see its own held action named when denied, if the action is sensitive.** Same
  rule as every other role: `isSensitiveAction(...)` suppresses the "who holds this" hint
  for locked/personal/non-Normal-sensitivity actions (lines 308-315).

### Delta for every other staff role

- **Subject Teacher / Class Teacher** — no approver powers at all in the appraisal flow;
  can view cycles/submissions it is a subject of, but `hod-score`/`principal-review`
  endpoints will deny them `ACTION_NOT_GRANTED` unless their role is specifically granted
  those action codes (it structurally should not be, per the spec's "teachers enter marks
  but never approve results" rule in §3 above). Leave self-service
  (`SelfServiceController`) behaves identically to any other staff member — own balances,
  own requests, own cancellation only.
- **Any staff role that is not `UserType.STAFF`** (e.g. a contractor or external examiner
  account, if such a `userType` exists) is blocked from the staff-only self-service
  endpoints outright with `AccessDeniedException` (`SelfServiceController.java:99, 127`) —
  this check happens before any `PermissionEvaluatorService` call, so it is a coarser,
  earlier gate than the fine-grained action check.
- **Principal/College Admin acting on a HOD's appraisal** — covered in `tenant-admin.md`
  §4, since that is the senior-role side of the same approval chain; this file's HOD
  section only covers the staff-side restrictions.

## Client Pitch Summary

*Plain-English version of the above, for a non-technical audience deciding whether to buy
EDU 360. Status labels: "Live today", "Built, screen coming soon", "On the roadmap".*

**On screen — what staff can do today:**
- Mark attendance, build timetables, set up exams
- Enter marks and manage assignments
- Apply for leave and view their own appraisal
- Read notices and manage their own profile

**Speaker notes:** This is the everyday toolkit a teacher actually uses — attendance,
timetable, exams, marks, assignments, leave — all from one account. Status: Live today.

**Feature — Attendance in seconds**
- On screen: mark daily or period-wise attendance; view attendance registers instantly; set
  attendance rules for the institution.
- Speaker notes: e.g. a teacher opens today's class, taps each student present, and submits —
  attendance for the whole class is marked in under 30 seconds, with an instant register
  available afterwards.
- Status: Live today (setting the rules themselves works; a dedicated screen for that is
  Built, screen coming soon).

**Feature — Exams and marks: entered, never self-approved**
- On screen: teachers enter marks for their own classes; a class teacher can submit marks for
  review, not publish results; only the Principal (or equivalent) can publish final results.
- Speaker notes: teachers do the real work of marking exams, but they never get to publish
  the final results themselves — that step always needs a more senior sign-off. It keeps the
  exam process trustworthy for parents and regulators alike.
- Status: Live today.

**Feature — Leave requests**
- On screen: staff apply for leave and see their own balance; requests go into an approval
  chain automatically; staff can cancel their own request before it starts.
- Speaker notes: a teacher applies for leave from their phone, sees exactly how many days
  they have left, and it's automatically routed to the right approver — no paper forms, no
  chasing people down.
- Status: Live today.

**Who can do what — Staff roles**

| Action | Head of Department | Subject / Class Teacher |
|---|---|---|
| Enter marks for own class | Yes | Yes |
| Approve/publish results | No | No |
| Score own appraisal | No | No |
| Score department staff appraisal | Yes (not their own) | No |
| Submit leave for someone else | No | No |

**Why the top role still has limits (plain terms):** even a Head of Department — the senior
staff role — cannot score their own appraisal; that always goes to the Principal instead. And
no one, at any level, can submit or cancel a leave request on someone else's behalf. These
are simple rules that prevent favouritism and keep records honest.

**Coming next / planned:**
- A formal "no overlapping leave approver and leave-taker" safety rule, and a configurable
  rule to prevent too many staff from being on leave in the same team at once. Status: On the
  roadmap.
- Per-role dashboards tailored to each teaching and support role, instead of one shared
  dashboard. Status: On the roadmap.
- A dedicated screen for external exam moderation (the underlying function already works, no
  screen yet). Status: Built, screen coming soon.
- Screens and self-service for other staff roles not covered above — Librarian, Lab
  Assistant, IT Administrator, Office Staff, Transport Staff, Hostel Staff. Status: On the
  roadmap.
