# Parent / guardian side

This covers the parent/guardian account: linking to a child, viewing a child's data, and
the guardian-change approval flow that protects who may be linked to a student.

## 1. Landing, sign-in and dashboard for this audience

**✅ Built today.** Same tenant sign-in mechanism as every other tenant audience (`Host`
header → Keycloak realm → `POST /api/auth/login`,
`api/src/main/java/com/edu360/api/auth/AuthController.java:58` → `TenantPrincipal`). A
guardian's own self-service mobile-number constraints are specifically called out in
recent commit history (`0fb9831b Cover guardian self-service mobile verification
constraint` — test-only, no behavior change; `078f9f24 Prevent unsupported guardian mobile
edits in self-service` — the real frontend change) and confirmed server-side in
`ProfileService.java` — see §3 below for the full line-by-line confirmation. This is an
active, recently-touched area of the code, not a stale corner.

After sign-in a guardian lands on the shared `/dashboard` and `/self-service` routes
(`app/src/routes/Routes.tsx:296, 304`) like staff and students, since the frontend does not
yet split a guardian-specific landing dashboard.

🔜 **V4.0 target:** D7 describes a guardian-specific dashboard view distinct from a
student's; today the frontend routes are the generic tenant-user ones.

## 2. What exists in code today

`api/src/main/java/com/edu360/api/guardian/GuardianLinkController.java` is the guardian
audience's main controller, tagged with its three action codes directly in the class
annotation: `[GUARDIAN_LINK, GUARDIAN_CHANGE_REQUEST, GUARDIAN_CHANGE_APPROVE]` (line 30).

| Action | Endpoint | Controller line | Who / what it does |
|---|---|---|---|
| List this guardian's active linked children | `GET /api/tenants/{tenantId}/guardian/children` | line 40 | scoped to the calling guardian's own principal (`guardianLinkService.listActiveChildren(tenantId, principal)`, line 43) |
| List guardian links for a specific student | `GET /api/tenants/{tenantId}/students/{studentId}/guardian-links` | line 51 | a read, used by staff/admin to see who is linked to a student |
| Link a guardian to a student | `POST /api/tenants/{tenantId}/students/{studentId}/guardian-links` | line 66 | `[GUARDIAN_LINK / Action #10]` (line 65); returns `201 CREATED` |
| Request a guardian change or block | `POST /api/tenants/{tenantId}/guardian-links/{id}/change-requests` | line 83 | `[GUARDIAN_CHANGE_REQUEST / Action #11]` (line 82) — raises a change request, does not itself change anything |
| Approve or reject a guardian change | `POST /api/tenants/{tenantId}/guardian-links/{id}/change-approvals` | line 100 | `[GUARDIAN_CHANGE_APPROVE / Action #12]` (line 99) — a separate action code from the request, meaning requesting and approving are structurally different operations, consistent with "preparer ≠ approver" |

Every method in this controller calls `extractPrincipal(authentication, tenantId)`
(lines 112-120), which throws `UnauthorizedException` unless the authenticated principal is
a `TenantPrincipal` **and** its `tenantId()` matches the path's `tenantId` exactly — a
guardian account from one tenant can never even reach another tenant's guardian-link data,
regardless of what role it holds, because this check happens before any
`PermissionEvaluatorService` call.

A guardian's own self-service (notifications, notices) runs through the same
`SelfServiceController` documented in `student.md` §2 — the endpoints are identical in
shape, scoped to `principal.userAccountId()`.

## 3. What the V4.0 spec plans

- **Maximum 3 non-blocked guardians per student** — `fn_guardian_limit`, a DB-level cap (D4
  §3, `02_rules.sql`, `V4.0_SPEC_SUMMARY.md` §3). Whether `GuardianLinkController`'s
  `linkGuardian(...)` enforces this at the application layer (returning a clean error
  before hitting the DB constraint) was not independently re-verified in this pass — worth
  checking `GuardianLinkService` directly if this limit is ever user-facing.
- **Guardian-change approval is a real approval chain, not a rubber stamp** — the separate
  action codes for request (`#11`) vs. approve (`#12`) are the code-level version of the
  spec's general "preparer ≠ approver" rule (D2/D3) and "rejection requires a remark and
  ends the request — no send-back, no auto-advance" rule (`V4.0_SPEC_SUMMARY.md` §2). The
  controller's `decideChange(...)` (line 100) takes a `GuardianChangeDecisionRequest` body
  that should carry the decision and any remark — confirm the DTO's exact shape in
  `com.edu360.api.guardian.dto.GuardianChangeDecisionRequest` before citing its fields.
- **Parent is in the never-roll-down list** (`V4.0_SPEC_SUMMARY.md` §2) — like Student,
  nothing ever rolls down to a parent, and a parent never acts as a stand-in for anyone
  else.
- **Parent's "Personal" actions** named in the catalog (`PermissionEvaluatorService.
  PERSONAL_ACTIONS`, lines 67-73) overlap with the student's own list for anything about
  "their child" — e.g. `PLACEMENT_VIEW_CHILD`, `TRANSPORT_VIEW_OWN` — note some of these
  action names read as if shared between student-self and parent-of-student views; this is
  the spec's own naming, not a bug this document is flagging, but worth being precise about
  which principal each one actually attaches to when building a guardian screen.
- **Guardian self-service mobile-number editing — now confirmed, both server and client
  side.** This is enforced in the backend, not just hidden in the UI:
  `api/src/main/java/com/edu360/api/selfservice/ProfileService.java:189-212`
  (`updateTenantProfile`) checks `account.getUserType() == UserType.GUARDIAN` before
  accepting a changed `mobile` value; if so, it **refuses the change outright** —
  `throw new BusinessRuleViolationException("PHONE_VERIFICATION_UNAVAILABLE", ...)` citing
  "Rule WF-30" — and records an audit entry
  (`PARENT_MOBILE_CHANGE_REFUSED_AUDIT`, line 186-187: *"Parent mobile change refused: no
  SMS provider is connected, so no code was sent or checked and no alert was sent; mobile
  unchanged."*) via `AuditEventType.DENIED_ATTEMPT`. This is **not** "view but edit after
  OTP" — no OTP flow exists yet (`ProfileUpdateRequest.otpCode`, line 37, is accepted in
  the DTO shape but not read/validated anywhere in `ProfileService`), so today it is a flat
  refusal, full stop, pending SMS provider integration. On the frontend, `commit 078f9f24`
  (`app/src/features/profile/components/ProfileDetailsTab.tsx`) hides the mobile field
  entirely for `profile.userType === "GUARDIAN"` and shows a locked read-only row with a
  lock icon and the hint "Mobile updates for guardian accounts require phone verification,
  which is not available yet. Contact your school office to change this number." — so the
  UI correctly never offers an edit the server is guaranteed to reject.
  `commit 0fb9831b` only added a test (`ProfilePage.test.tsx`) covering this; it made no
  behavioral change. **✅ API + ✅ Screen** — both sides confirmed line-by-line.

## 4. Role restrictions

Like the student side, there is effectively one guardian role, so this section explains
its restrictions directly.

**What a guardian cannot do, and why:**
- **Cannot see or act on a tenant from outside their own tenant.** `extractPrincipal(...)`
  (`GuardianLinkController.java:112-120`) rejects any mismatch between the path's
  `tenantId` and the authenticated principal's `tenantId()` before any business logic
  runs — the strictest possible gate, applied uniformly to every method in the controller.
- **Cannot approve their own guardian-change request.** Even though the controller itself
  does not show a self-approval check inline, the request (`#11`) and approve (`#12`)
  actions are separate action codes routed through `PermissionEvaluatorService.authorize`
  inside `GuardianLinkService` (not re-opened line-by-line in this pass); per the class-wide
  `SELF_APPROVAL_FORBIDDEN` rule (`PermissionEvaluatorService.java:197-202`), a guardian who
  raised a change request cannot also be the one who decides it if the target of the
  approval resolves to their own account — confirm this specific call site in
  `GuardianLinkService` if a precise guarantee is needed for a compliance answer.
- **Cannot list every guardian link in the tenant.** The only "list" endpoints are scoped
  either to the calling guardian's own children (line 40) or to one named student (line
  51) — there is no "list all guardian links" endpoint a guardian principal could call to
  browse the tenant.
- **Cannot edit a mobile number at all today** — confirmed in §3: the server
  (`ProfileService.updateTenantProfile`) refuses any guardian mobile change with
  `PHONE_VERIFICATION_UNAVAILABLE` regardless of what the client sends, because no SMS
  provider is connected yet; this is a flat block, not a "verified vs. unverified" nuance.
  Any future screen that lets a guardian silently change a mobile number would be a
  regression against Rule WF-30 as it stands today.
- **What happens on a denied attempt:** the same `403 UnauthorizedException` /
  `GlobalExceptionHandler` pattern as every other audience. A guardian denied access to a
  student's guardian-links list (for example, trying to view a child it is not linked to)
  gets the generic `{timestamp, status, error}` shape, with no sensitive-action role hint
  per the same anti-disclosure rule described in `tenant-admin.md` §4.

No other distinct guardian-side role exists to give a delta for in this build; any
broader "who can see a student's guardians" question belongs to the staff/admin side
(`StudentDirectoryController`, `tenant-admin.md` §2) rather than to the guardian principal
itself.

## Client Pitch Summary

*Plain-English version of the above, for a non-technical audience deciding whether to buy
EDU 360. Status labels: "Live today", "Built, screen coming soon", "On the roadmap".*

**On screen — what a parent/guardian can do today:**
- View their linked child's information
- Request to be linked to a child's record
- Request or approve a guardian change
- View notices and notifications

**Speaker notes:** Parents get their own account tied directly to their child — not a shared
login, not a workaround. Status: Live today.

**Feature — Guardian changes: a protected process**
- On screen: linking or changing a guardian goes through a request-then-approval step; the
  person who requests a change cannot also approve it; a maximum number of guardians can be
  linked per student (planned).
- Speaker notes: if a family's guardianship needs to change — say, after a custody change —
  that request has to be separately approved by someone else. This protects children from an
  unauthorised person simply attaching themselves to a student record.
- Status: Live today (request-then-approval); the maximum-guardians limit is On the roadmap.

**Feature — A known, deliberate limitation: mobile number changes**
- On screen: guardians currently cannot change their own mobile number online; this is
  intentional, pending phone verification being switched on; the screen explains this clearly
  rather than hiding the limit.
- Speaker notes: we want to be transparent here — a parent currently can't change their
  mobile number themselves in the app, because we haven't yet connected phone verification.
  Rather than silently allowing an unverified change, which would be a security risk, the
  system clearly tells the parent to contact the school office instead. It's a deliberate,
  safe choice, not a bug.
- Status: Live today (as a safe, explicit block); full phone-verified self-editing is On the
  roadmap.

**Who can do what — Parent/Guardian**

| Action | Parent/Guardian |
|---|---|
| View own linked child's data | Yes |
| View another family's child | No |
| Request a guardian change | Yes |
| Approve their own guardian change request | No |
| Change mobile number online | Not yet (phone verification pending) |

**Why the top role still has limits (plain terms):** like students, there's one guardian
role, and it's tightly scoped to just their own linked children. Even requesting a guardian
change doesn't let them approve it themselves — someone else always has to sign off. And a
parent from one school can never even reach another school's family records, no matter what.

**Coming next / planned:**
- A guardian-specific dashboard, separate from the shared general dashboard used today.
  Status: On the roadmap.
- Online self-editing of a mobile number once phone verification is connected. Status: On
  the roadmap.
- An enforced maximum of 3 active guardians per student. Status: On the roadmap.
