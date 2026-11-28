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

*This section serves two readers at once. The "On screen"/"Speaker notes" parts are the
plain-English client pitch. Each feature is immediately followed by a "Developer detail"
block with full technical depth so a new engineer could pick up the feature from this
section alone, without hunting through the rest of the file. Status labels for the client:
"Live today", "Built, screen coming soon", "On the roadmap".*

**On screen — what staff can do today:**
- Mark attendance, build timetables, set up exams
- Enter marks and manage assignments
- Apply for leave and view their own appraisal
- Read notices and manage their own profile

**Speaker notes:** This is the everyday toolkit a teacher actually uses — attendance,
timetable, exams, marks, assignments, leave — all from one account. Status: Live today.

**Developer detail — how staff self-service is separated from academic actions (applies
to every feature below, read this first):** this audience's code is split into two very
different kinds of controller, and it's worth a junior developer understanding why. The
first kind is `api/src/main/java/com/edu360/api/selfservice/SelfServiceController.java` —
this is where a staff member manages things that are purely about *themselves*:
notifications, their own leave balance, submitting their own leave request. Every method
in this controller pulls the currently logged-in person's identity out of the request (a
`TenantPrincipal`, obtained from Spring's `Authentication` object) and then, for anything
staff-specific, does an extra check: `principal.userType() == UserType.STAFF` (checked
at, e.g., lines 98, 111, 127). If that's false, the method throws `AccessDeniedException`
immediately (e.g. line 99) — notice this happens *before* the normal
`PermissionEvaluatorService.authorize(...)` action-code check described in
`tenant-admin.md`'s Client Pitch Summary ever runs. This is a simpler, coarser gate: it
isn't asking "does this specific role hold this specific action," it's asking "is this
account even the kind of account (staff vs. student vs. guardian) that this whole
controller is for." Think of it as a first filter before the more detailed permission
system gets involved.

The second kind of controller covers the actual teaching work — attendance, assignments,
exams, timetable, report cards — and these live in
`api/src/main/java/com/edu360/api/academic/controller/`: `AttendanceController.java`,
`AssignmentController.java`, `ExaminationController.java`, `TimetableController.java`,
`ReportCardController.java`. These do **not** have the `UserType.STAFF` coarse gate — they
go straight to the normal, fine-grained `PermissionEvaluatorService.authorize(...)` check
(action code + institution/department scope), exactly as explained in `tenant-admin.md`'s
Client Pitch Summary. The reasoning for the difference: these actions are tied to a role
and a scope (e.g. "do you hold `ATTENDANCE_MARK` for this class"), not to "are you
generically a staff member" — a teacher's permission to mark attendance for one specific
class is a more precise question than "are you staff," so it goes through the more precise
system. All five of these academic controllers resolve who's calling them the same way: a
private `principal(auth, tenantId)` helper method requires a `TenantPrincipal` whose own
`tenantId()` matches the tenant ID in the URL path, otherwise it throws
`UnauthorizedException`/`ResourceNotFoundException` — this is what stops a teacher logged
into School A's subdomain from ever successfully calling an endpoint with School B's
tenant ID in the URL, even before any action-code check runs.

**Feature — Attendance in seconds**
- On screen: mark daily or period-wise attendance; view attendance registers instantly;
  set attendance rules for the institution.
- Speaker notes: e.g. a teacher opens today's class, taps each student present, and submits —
  attendance for the whole class is marked in under 30 seconds, with an instant register
  available afterwards.
- Status: Live today (setting the rules themselves works; a dedicated screen for that is
  Built, screen coming soon).
- **Developer detail:** Everything here lives in `AttendanceController.java`, which hands
  off to `AttendanceService.java` for the real logic. A teacher's attendance flow is
  actually two calls, not one, which is worth understanding because it maps onto a
  real-world need: first a "session" is created for the class period being marked — `POST
  /api/tenants/{t}/attendance/sessions` (`AttendanceController.java:10`) calls
  `AttendanceService.createSession`, checking either `ATTENDANCE_MARK` (a normal class
  happened) or `ATTENDANCE_NOT_HELD` (the class didn't happen at all, e.g. the teacher was
  absent or it was a holiday) — (`AttendanceService.java:13,34`) — and writes a new row
  into `app.attendance_session` (representing "this class, this period, this day exists as
  something attendance could be recorded against"), plus, for a normal session, starter
  rows in `app.attendance_entry` (one row per student, to be filled in next). Then the
  actual present/absent marks are recorded: `POST .../attendance/entries`
  (`AttendanceController.java:14`) calls `AttendanceService.recordEntries`, re-checking
  `ATTENDANCE_MARK`, and updates those same `app.attendance_entry` rows with each student's
  actual status. Splitting "create the session" from "fill in the entries" into two calls
  means the system can represent "this class happened, but nobody has marked attendance
  yet" as a distinct, real state — useful for catching a teacher who forgot, rather than
  that information simply not existing anywhere.

  Viewing the register: `GET .../attendance/register` (`AttendanceController.java:23`)
  calls `AttendanceService.register`, checking the read-only `ATTENDANCE_VIEW` action
  code, and reads back from both `app.attendance_entry` and `app.attendance_session`
  (it needs the session table too, to know which days/periods even had a session created,
  not just which entries exist).

  Setting the rules: `PUT .../attendance/thresholds` (`AttendanceController.java:27`) calls
  `AttendanceService.updateThreshold`, checking `ATTENDANCE_RULES_SET`, writing to
  `app.attendance_threshold_policy` (e.g. the minimum attendance percentage a student needs
  to avoid being flagged). This endpoint works today, but there is no dedicated
  threshold-editor screen found under `app/src/features/academic/` in this pass — so an
  admin wanting to change this today would need the API called directly (e.g. via a support
  request or an internal tool), not through a polished screen yet.

**Feature — Exams and marks: entered, never self-approved**
- On screen: teachers enter marks for their own classes; a class teacher can submit marks for
  review, not publish results; only the Principal (or equivalent) can publish final results.
- Speaker notes: teachers do the real work of marking exams, but they never get to publish
  the final results themselves — that step always needs a more senior sign-off. It keeps the
  exam process trustworthy for parents and regulators alike.
- Status: Live today.
- **Developer detail:** The exam workflow in `ExaminationController.java` /
  `ExaminationService.java` is a good illustration of "the same data, moving through
  several hands, each hand getting a different action code" — a pattern you'll want to
  recognize elsewhere in this codebase. First, a cycle and schedule are set up: `POST
  /api/tenants/{t}/academic/exams/cycles` (`ExaminationController.java:23-27`) →
  `ExaminationService.createCycle`, checking `EXAM_SCHEDULE_INTERNAL`
  (`ExaminationService.java:30`), writing `app.exam_cycle`; `POST .../exams/schedules`
  (lines 29-33) → `ExaminationService.schedule`, same action code, writing
  `app.exam_schedule`; `POST .../exams/schedules/{id}/invigilators/{staffUserId}` (lines
  40-45) → `ExaminationService.assignInvigilator`, same action code again, writing
  `app.exam_invigilator` (who is supervising which exam sitting).

  Then the actual marks workflow, which is where the "teachers enter, never approve" rule
  becomes visible as three *different* action codes for three different steps on the same
  underlying `app.exam_mark` table: `POST .../exams/marks` (lines 47-51) →
  `ExaminationService.enterMarks`, checking `MARKS_ENTER`
  (`ExaminationService.java:92`) — this is the teacher physically typing in a student's
  score. `POST .../exams/marks/{id}/course-teacher-approve` (lines 53-57) →
  `ExaminationService.courseTeacherApprove`, checking `MARKS_SUBMIT`
  (`ExaminationService.java:137`) — note this is a deliberately *lesser* action code than
  a publish-level grant; "submit" is not the same permission as "publish," even though both
  touch the same table. `POST .../exams/marks/{id}/external-moderation` (lines 59-64) →
  `ExaminationService.externalModerate`, checking `MARKS_EXTERNAL_UPLOAD`
  (`ExaminationService.java:163`) — this endpoint works today (✅ API only — no
  external-moderation UI was found under `app/src/features/academic/` in this pass, so an
  external moderator's upload today would have to go through the raw API, not a built
  screen).

  Finally, viewing and publishing results — a genuinely different controller,
  `ReportCardController.java`, because by this point we've moved from "marks for one exam"
  to "a student's compiled report card." `GET /api/tenants/{t}/academic/report-cards/{id}`
  (lines 18-21) → `ReportCardService.get`, checking the read-only `RESULT_VIEW`
  (`ReportCardService.java:46`), reading `app.report_card`. `POST
  .../report-cards/{id}/publish` (lines 23-26) → `ReportCardService.publish`, checking
  `RESULT_PUBLISH` (`ReportCardService.java:60`) — and this is the key detail to notice:
  this check is institution-scoped, meaning it requires a role assignment covering the
  whole institution (via top-post coverage, explained in `tenant-admin.md`), a materially
  bigger grant than the class/department-level `MARKS_SUBMIT` a Class Teacher holds. In
  other words, the system doesn't rely on a special "teachers can't publish" rule written
  specifically for this feature — it relies on the simple structural fact that publishing
  requires a bigger grant than any ordinary teacher role is given, which is the same
  general scope-matching mechanism used everywhere else in this codebase. (Also see
  `tenant-admin.md`'s Client Pitch Summary for a confirmed gap in this same publish flow:
  it does not yet check that the publisher is the *correct kind* of role for the
  institution's specific mode — school vs. college-under-university vs. standalone
  college.)

**Feature — Leave requests**
- On screen: staff apply for leave and see their own balance; requests go into an approval
  chain automatically; staff can cancel their own request before it starts.
- Speaker notes: a teacher applies for leave from their phone, sees exactly how many days
  they have left, and it's automatically routed to the right approver — no paper forms, no
  chasing people down.
- Status: Live today.
- **Developer detail:** This feature lives entirely in
  `SelfServiceController.java`, backed by `StaffLeaveService` for the staff-specific logic.
  `GET /api/tenants/{tenantId}/me/leave-types` (line 91) lists which kinds of leave (sick,
  casual, earned, etc.) this staff member can even apply for — staff-only, otherwise a 403
  via `AccessDeniedException` (line 99). `GET .../me/leave-balances` (line 104) checks
  `principal.userType()`: if it's staff, it calls `StaffLeaveService.ownBalances`; for any
  other user type it falls through to a more generic `selfService.getLeaveBalances` method
  — a good example of the controller branching on user type to call a different service
  method, rather than one method trying to handle every kind of user internally.

  Submitting: `POST .../me/leave-requests` (line 119) calls
  `staffLeaveService.submit(tenantId, principal, request)`. The method's own `@Operation`
  summary describes this as routing the request "into the configured approval chain" (line
  120) — meaning the system doesn't hard-code one fixed approver; it looks up whatever
  approval routing has been configured for this tenant/leave-type combination and creates
  the appropriate pending-approval record, the same general `ApprovalRequest` mechanism
  referenced in `tenant-admin.md`'s fee-structure feature. This endpoint is staff-only;
  anyone else gets a 403.

  Cancelling: `POST .../me/leave-requests/{leaveRequestId}/cancel` (line 133) — described
  in the code's own summary as only working "before its start date." Crucially, notice this
  is always scoped to the calling user's own account (`principal.userAccountId()`) — there
  is no endpoint anywhere in this controller, for submission or cancellation, that accepts
  someone else's user ID as the target. This means a Head of Department, no matter how
  senior, literally cannot submit or cancel leave *for* another staff member through this
  API; if that capability is ever needed, it would require a new, separate endpoint to be
  built, because the current one is structurally locked to "your own record only."

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

**Developer detail — why even the HOD has hard walls:** the Head of Department is the most
complex role a "staff" person can hold in this codebase, because it sits on both sides of
the fence — a teaching role with its own self-service, and an approver for
department-scoped actions elsewhere (see `tenant-admin.md` §4 for the full approver-side
mechanism). Two restrictions are worth spelling out precisely, because they come from two
different parts of the code: first, `SELF_APPROVAL_FORBIDDEN`
(`PermissionEvaluatorService.java:197-202`) fires if the HOD is scoring their *own*
appraisal submission, before any role check even runs — there is no special case anywhere
in that class for "but this HOD is senior, so let them." Second, `topPostCovers(...)`
(`PermissionEvaluatorService.java:486-506`) simply never names `HOD` in any of its
coverage branches — meaning a HOD's `DEPARTMENT`-scoped assignment must match the exact
department ID of whatever they're trying to act on (`sameScope(...)`, lines 479-483), with
no fallback or "nearby department" exception; acting outside their own department is denied
`NO_ACTIVE_ROLE_IN_SCOPE` outright. On top of both of those, the leave-submission
restriction described in the Leave feature above isn't even part of
`PermissionEvaluatorService` at all — it's a simpler, earlier structural fact: every
leave-related method in `SelfServiceController` is hard-coded to operate only on
`principal.userAccountId()` (the caller's own account), so there is no code path, for any
role including HOD, that lets one person submit or cancel leave on another's behalf. Each
of these three restrictions prevents a different kind of problem: self-approval prevents a
HOD rubber-stamping their own performance review; exact department scoping prevents a HOD
reaching into a department they don't actually manage; and the hard-coded "own account
only" leave scoping prevents anyone, however senior, quietly submitting or withdrawing
someone else's leave without that person's knowledge.

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
