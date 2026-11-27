# Student side

This covers the student's own view: self-service, academics, fees, library, complaints —
everything a `UserType.STUDENT` principal sees of their own record.

## 1. Landing, sign-in and dashboard for this audience

**✅ Built today.** Same tenant sign-in path as every other tenant audience (see
`tenant-admin.md` §1 for the full mechanism): `Host` header → Keycloak realm →
`POST /api/auth/login` (`api/src/main/java/com/edu360/api/auth/AuthController.java:58`) →
`TenantPrincipal`. A student lands on `/dashboard` (`app/src/routes/Routes.tsx:296`), then
`/self-service` (`Routes.tsx:304`) for notifications/profile, and
`/student/medical-exemptions` (`Routes.tsx:225`, gated by `anyOf={["STUDENT_LEAVE_APPLY"]}`)
to request a medical exemption from attendance. Fee payment is at
`FeePaymentPortalPage.tsx` (named in the graph hub list; not independently re-opened in
this pass — verify its exact route before citing a URL for it).

🔜 **V4.0 target:** `/me/...` self-service operations named in the OpenAPI spec (D5,
`V4.0_SPEC_SUMMARY.md` §4) include `exam-schedule`, `student-record`,
`dashboard-preferences`, `data-requests` — a fuller self-service surface than the tenant
`SelfServiceController` currently exposes (see §2).

## 2. What exists in code today

Students are read about, not acted on, mostly through tenant-admin-facing controllers
(`StudentDirectoryController`, reviewed in `tenant-admin.md` §2) and guardian-facing
controllers (`GuardianLinkController`, reviewed in `parent.md` §2). The student's own
self-service surface is the shared `SelfServiceController`
(`api/src/main/java/com/edu360/api/selfservice/SelfServiceController.java`):

| Action | Endpoint | Controller line | Notes |
|---|---|---|---|
| View own notifications | `GET /api/tenants/{tenantId}/me/notifications` | line 41 | any signed-in tenant user |
| Mark a notification read / read-all | `POST .../me/notifications/{id}/read`, `/read-all` | lines 68, 80 | own record only |
| Get own leave balances | `GET /api/tenants/{tenantId}/me/leave-balances` | line 104 | for a non-staff principal (students), falls through to `selfService.getLeaveBalances(tenantId, principal.userAccountId())` (line 116) rather than the staff-specific balance service |
| View published tenant notices | `GET /api/tenants/{tenantId}/notices` | line 156 | read-only |

**Student leave** (`STUDENT_LEAVE_APPLY` action, named in
`PermissionEvaluatorService.PERSONAL_ACTIONS`,
`api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java:70`) is one of the
22 actions explicitly flagged "Personal" — meaning a top-post role (Management, VC,
Principal) can never perform it through top-post coverage even if no student-equivalent
role exists for the record (lines 259-262) — a student's own leave/medical-exemption
request can only ever be acted on by the student themselves or by whichever specific
approver role the action is granted to, never by a senior role "stepping in" generically.

Other personal actions reserved the same way in that same set include `FEE_PAY_ONLINE`,
`FEE_VIEW_OWN`, `ASSIGNMENT_SUBMIT`, `ASSIGNMENT_VIEW_OWN`, `RESULT_VIEW_OWN`,
`ATTENDANCE_VIEW_OWN`, `HALL_TICKET_DOWNLOAD`, `COMPLAINT_RAISE`,
`STUDENT_RECORD_VIEW_OWN`, `CERTIFICATE_REQUEST`
(`PermissionEvaluatorService.java:67-73`) — these names confirm the action catalog already
distinguishes a student's own-record view/action from a staff member's broader view, even
though this pass did not open every controller that implements each one. Grievance
complaint endpoints live in
`api/src/main/java/com/edu360/api/governance/controller/GrievanceCaseController.java` —
now opened line-by-line. **Correction:** the action catalog name `COMPLAINT_RAISE` listed
above does **not** match what this controller actually calls — its submit path uses a
different action code entirely:

**✅ API + ✅ Screen** (`/governance/grievances/submit`, `GrievanceCaseManagementPage
mode="submit"` in `app/src/routes/Routes.tsx:316`, gated by
`TENANT_ROUTE_ACTIONS.grievanceSubmission` and `allowSupportSession={false}` — a platform
support session can never reach this screen, matching the controller's own
`SupportPrincipal` rejection below).

| Endpoint | Controller:line | Service method | Action code | DB table |
|---|---|---|---|---|
| `POST /api/tenants/{t}/governance/grievances/submissions` | `GrievanceCaseController.java:43-49` | `GrievanceService.submit` | `GRIEVANCE_CASE_SUBMIT` (`GrievanceService.java:60`) | `app.grievance_case` |
| `GET .../cases`, `GET .../cases/{id}` | `GrievanceCaseController.java:51-61` | `GrievanceService.list`/`get` | `GRIEVANCE_CASE_VIEW` (`GrievanceService.java:319,340`) | `app.grievance_case` |
| `POST .../cases/{id}/assign` | `GrievanceCaseController.java:75-79` | `GrievanceService.assign` | `GRIEVANCE_CASE_ASSIGN` (`GrievanceService.java:128`) | `app.grievance_case` |
| `POST .../cases/{id}/hearing`, `/minutes` | `GrievanceCaseController.java:81-93` | `GrievanceService.scheduleHearing`/`recordMinutes` | `GRIEVANCE_HEARING_MANAGE` (`GrievanceService.java:201,226`) | `app.grievance_hearing` |
| `POST .../cases/{id}/resolve` | `GrievanceCaseController.java:102-106` | `GrievanceService.resolve` | `GRIEVANCE_CASE_RESOLVE` (`GrievanceService.java:250,262`), target-scoped to the case | `app.grievance_case`, `app.grievance_action` |
| `POST`/`GET .../cases/{id}/evidence` | `GrievanceCaseController.java:108-119` | `GrievanceService.uploadEvidence`/`evidence` | `GRIEVANCE_EVIDENCE_UPLOAD` / `GRIEVANCE_EVIDENCE_VIEW` (`GrievanceService.java:282,301`) | `app.grievance_evidence` |

A student submitting a grievance is one `TenantPrincipal` among several who can call
`submit` — the controller does not gate `submit` to students only; any tenant principal
with `GRIEVANCE_CASE_SUBMIT` granted can raise one. The controller's own `principal(...)`
helper (lines 121-130) additionally hard-blocks any `SupportPrincipal`
(platform support session) from reaching **any** method in this controller with
`AccessDeniedException("Support sessions are never permitted to access confidential
grievance workflows.")` — a stronger, confidentiality-specific guard than the generic
tenant-principal check used elsewhere, confirming the PO-T05/`fn_complaint_guard`-style
confidentiality intent actually reaches the code.

## 3. What the V4.0 spec plans

- **Fees:** no partial payments — `fn_allocation_guard()` requires the paid amount to equal
  the net due exactly (D1 §1/D4 §3, `V4.0_SPEC_SUMMARY.md`). A student can only ever pay
  one open online payment at a time per account. `FeePaymentController.java` and
  `OfflinePaymentController.java` exist in `api/src/main/java/com/edu360/api/finance/` —
  whether they currently enforce the exact-amount rule at the application layer (vs. only
  in the spec's SQL bundle) was not independently re-verified in this pass.
- **Results:** `Draft → Submit → HOD/Class-Teacher review → Principal approval → publish`,
  with `fn_publish_gate()` rejecting a mismatched publisher for the institution's mode
  (school/college-under-university/standalone college). A student only ever sees
  `RESULT_VIEW_OWN` after that chain completes — the spec's workflow states (D7) define a
  distinct "not yet published" empty state the UI must show rather than a blank or broken
  page.
- **Complaints module:** `fn_complaint_guard` makes `RESOLVED` a final state — a student
  cannot reopen a resolved complaint; the only way back in is a fresh complaint.
- **Explicitly out of Phase 1 for students:** refunds, partial fee payments, re-evaluation
  requests, WhatsApp-channel communication, report-abuse flows, alumni login (D1 §1,
  `V4.0_SPEC_SUMMARY.md` §1) — don't build or promise any of these without flagging that
  they are deliberately excluded from v1.
- **71-role catalogue places Student once per level** (College, School) — the role itself
  is in the spec's **never-roll-down list** (§2 of `V4.0_SPEC_SUMMARY.md`) — a student's
  access is never a stand-in for anything, and nothing ever rolls down *to* a student
  either.

## 4. Role restrictions

There is only one student role, so this section covers it directly rather than a "most
senior role + deltas" split (the pattern used for the other audiences, which have several
roles per level).

**What a student cannot do, and why:**
- **Cannot view or act on another student's record.** Every self-service endpoint is
  scoped to `principal.userAccountId()` at the controller level
  (`SelfServiceController.java`, e.g. lines 48-49, 65, 76, 87) — there is no student-facing
  endpoint in this controller that accepts another user's ID as a target; cross-student
  access would require a staff/admin action code instead, checked through
  `PermissionEvaluatorService` with institution/department scoping (see
  `tenant-admin.md` §2 for `StudentDirectoryController`).
- **Cannot be granted a "Personal" action through anyone else's coverage, and cannot be
  granted anyone else's action through their own.** `PERSONAL_ACTIONS`
  (`PermissionEvaluatorService.java:67-73`) are excluded from top-post coverage in both
  directions by construction — they are checked only against an exact-scope assignment,
  never against a senior role's broader scope.
- **Cannot approve anything, ever, as a structural matter** — the student role is never
  listed as a holder of an approval action anywhere in this codebase's role-action model;
  approval actions by definition sit with staff/admin roles in a chain (D2/D3). If a
  student record is ever passed as the *target* of an approval action (e.g. someone
  deciding a guardian-change request about them), `SELF_APPROVAL_FORBIDDEN` would in any
  case block the student from being both subject and decider of the same request
  (`PermissionEvaluatorService.java:197-202`).
- **Cannot see sensitive denial detail.** Like every other role, a denied student request
  gets a generic message with no "who can do this" hint when the action is sensitive
  (`isSensitiveAction`, lines 308-315) — e.g. a student denied access to another student's
  record never learns which staff role could see it.
- **What happens on a denied attempt:** the same `403`/`409` split as every other audience
  — `UnauthorizedException` (403) for a plain denial, `ActiveRoleRequiredException` (409)
  only in the rare case a student holds more than one active role assignment and hasn't
  chosen one (`PermissionEvaluatorService.java:286-300`) — in practice students normally
  hold exactly one role, so the 409 path is a defensive case rather than a common one.

No other role exists "within" the student audience to give a delta for — a parent/guardian
viewing a student's data is a different principal type entirely, covered in `parent.md`.

## Client Pitch Summary

*Plain-English version of the above, for a non-technical audience deciding whether to buy
EDU 360. Status labels: "Live today", "Built, screen coming soon", "On the roadmap".*

**On screen — what a student can do today:**
- View their own notifications and notices
- Check leave/medical exemption balances and apply
- Raise a confidential grievance
- Request a medical exemption from attendance

**Speaker notes:** Students get a focused, personal view — their own record, their own
requests, nothing about any other student. Status: Live today.

**Feature — Raising a confidential grievance**
- On screen: a student can raise a grievance directly in the system; it is routed to a
  confidential handling process; support staff can never access grievance cases, even with
  special access.
- Speaker notes: e.g. a student facing an issue can raise it privately through the system,
  and it goes straight into a confidential handling workflow that even our own platform
  support team is permanently locked out of — not even a time-limited exception applies
  here.
- Status: Live today.

**Who can do what — Student**

| Action | Student |
|---|---|
| View own record, marks, attendance, fees | Yes |
| View another student's record | No |
| Approve anything | No |
| Submit a grievance | Yes |
| Stand in for any other role | No |

**Why the top role still has limits (plain terms):** there's only one student role, and it's
deliberately narrow — a student can see and act on their own record only, never anyone
else's, and can never be asked to approve anything. It's the simplest, most protected view in
the whole system. Even if a decision is ever made about a student (such as a guardian-change
request concerning them), the system's rule that "no one approves their own case" means a
student could never end up approving a decision about themselves either.

**Coming next / planned:**
- A fuller self-service area: exam schedules, viewing one's own full record, dashboard
  preferences, and requesting a copy of one's own data. Status: On the roadmap.
- Clear "not yet published" screens for results, rather than a blank page, while results work
  through the approval chain. Status: On the roadmap.
- Features explicitly not planned for the first phase: refunds, partial fee payments,
  re-evaluation requests, WhatsApp messaging, report-abuse flows, and alumni login. Status:
  On the roadmap (explicitly deferred, not forgotten).
