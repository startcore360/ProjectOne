# Tenant / institution admin side

This covers every tenant-level role that is not a teacher or a student: Management,
University Admin, Vice Chancellor/Registrar, Principal/College Admin/School Admin, Head of
Department, and the admissions/fees/academic-structure roles that report to them.

## 1. Landing, sign-in and dashboard for this audience

**✅ Built today.** The admin signs in from the tenant's own subdomain (e.g.
`http://abc.edu360.localhost:5173`). The browser's `Host` header picks the Keycloak realm
— the same `POST /api/auth/login` endpoint as every other audience
(`api/src/main/java/com/edu360/api/auth/AuthController.java:58`). On success the token's
`iss` claim resolves to a `TenantPrincipal`
(`api/src/main/java/com/edu360/api/config/TenantPrincipal.java`) and `TenantContext` is set
for the rest of the request, which is what lets Postgres RLS show this tenant's `app.*`
rows at all (per this repo's `CLAUDE.md` multi-tenancy section).

The admin's routes live mostly under `/university/...` and `/tenants/...` in
`app/src/routes/Routes.tsx:192-236`, gated by `UniversityAdminRoute`
(`Routes.tsx:168`). That gate is **fail-closed by design**: per this repo's `CLAUDE.md`,
it always redirects away until the browser holds an authoritative tenant permission
context — so `/university/*` is effectively unreachable until that context is actually
wired for the signed-in user, not merely because the backend check failed. Each route is
additionally wrapped in `<ActionGate anyOf={...}>` naming the action code(s) that must be
held, e.g. `/university/institutions` requires `TENANT_ROUTE_ACTIONS.institutions`
(`Routes.tsx:195`), `/university/staff` requires `TENANT_ROUTE_ACTIONS.staffRecords`
(`Routes.tsx:205`), `/university/roles` requires `TENANT_ROUTE_ACTIONS.roles`
(`Routes.tsx:208`).

🔜 **V4.0 target:** D7/D10 specify full role-specific landing dashboards (e.g. a VC's
KPI cockpit, a Principal's go-live checklist) with defined loading/empty/error/denied
states for each screen — the current admin dashboard is a single generic
`/dashboard` route (`Routes.tsx:296`) shared across roles, not yet the per-role dashboard
set the spec describes.

## 2. What exists in code today

Every tenant-side create/update/delete flow calls
`permissionEvaluatorService.authorize(principal, "ACTION_CODE", scope, target)`
(`api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java`), never an
annotation. The mechanism (verified by reading the class in full):

1. The action code must exist in `ref.action_catalog`; unknown codes are always denied
   (line 171-174).
2. A caller with no active role chosen, when they hold more than one, gets `409
   ACTIVE_ROLE_REQUIRED` (lines 224-225) rather than a merged or guessed permission set.
3. The active assignment's scope (`TENANT`/`INSTITUTION`/`DEPARTMENT`) must **exactly**
   match the action's scope — a tenant-level Management assignment does not automatically
   grant an institution-scoped action, *except* through "top-post coverage"
   (`topPostCovers(...)`, lines 486-506): Management/University Admin cover the whole
   tenant; Vice Chancellor covers the tenant or its own institution; Principal (college or
   school) covers only its own institution, and never a tenant-wide scope. Top-post
   coverage never applies to `PERSONAL_ACTIONS` (student/parent/applicant actions, line
   259-262) or `LOCKED_ACTIONS` (line 263-266).
4. Approval actions against the caller's own record are denied before any role lookup —
   `SELF_APPROVAL_FORBIDDEN` (lines 197-202) — this is the code enforcement of "nobody
   approves their own request" (D2/D3, Addendum rule 11).
5. The role definition itself must be `ACTIVE` in `app.role_definition`
   (`ROLE_NOT_ACTIVE`, lines 235-240) — a `DRAFT`/`INACTIVE`/`RETIRED`/`ARCHIVED` role
   grants nothing even if `app.role_action` still lists it.
6. Finally, `app.role_action` (via `RoleActionRepository`,
   `api/src/main/java/com/edu360/api/rbac/RoleActionRepository.java`) must actually contain
   the action for that role — this is the runtime source of truth, not the static matrix
   described in the spec documents.

Representative flows:

| Action | Endpoint / controller | DB tables (per `02_rules.sql`/`03_bindings.sql` conventions) | Permission check |
|---|---|---|---|
| Create/manage staff appraisal cycle | `POST .../institutions/{institutionId}/appraisals/cycles` (`api/src/main/java/com/edu360/api/staff/controller/StaffAppraisalController.java:33`) | `app.appraisal_cycle` | `authorize` with an appraisal action code, institution scope |
| View appraisal submissions | `GET .../appraisals/cycles/{cycleId}/submissions` (`StaffAppraisalController.java:50`) | `app.appraisal_submission` | read-scoped `authorize` |
| HOD score on a submission | `POST .../submissions/{submissionId}/hod-score` (`StaffAppraisalController.java:72`) | `app.appraisal_submission` | department-scoped `authorize`; self-approval guard applies if HOD is scoring their own submission |
| Principal review | `POST .../submissions/{submissionId}/principal-review` (`StaffAppraisalController.java:79`) | `app.appraisal_submission` | institution-scoped `authorize` |
| List/search students | `GET /api/tenants/{tenantId}/students` (`api/src/main/java/com/edu360/api/student/StudentDirectoryController.java:20`) | `app.student_profile`, `app.student_enrollment` | `STUDENT_SEARCH` action, gates the `/tenants/students` route too (`Routes.tsx:231`) |
| Add a student note | `POST .../students/{studentId}/notes` (`StudentDirectoryController.java:33`) | `app.student_note`, guarded by `fn_note_guard` (no UPDATE ever, per D4) | institution/department-scoped `authorize` |
| Create a tenant (not this role — see `platform.md`) | — | — | tenant-admins never call tenant-lifecycle endpoints; those are platform-only |

Role/permission administration itself (who can grant which role which action) lives under
`/university/roles` (`Routes.tsx:208`) and `/university/missing-roles`
(`Routes.tsx:209`) — the "missing roles" screen exists specifically to show the gap
between the 71-role spec catalogue and what has actually been created in this tenant,
which is itself evidence that most tenants are not yet running anywhere near the full
71-role target. Role roll-down administration (V4-PD-01) is under
`api/src/main/java/com/edu360/api/rolldown/RolldownController.java` — now opened and
confirmed line-by-line:

**✅ API + ✅ Screen** (screen is embedded, not a standalone route — see below).

| Endpoint | Controller:line | Service method | Action code |
|---|---|---|---|
| `GET /api/tenants/{t}/rolldown-map` (+ `/v1` and `/rolldown/map` aliases) | `RolldownController.java:30-45` | `RolldownService.getRolldownMap` | `ROLLDOWN_VIEW` (`RolldownService.java:50`) |
| `GET /api/tenants/{t}/rolldown-overrides` | `RolldownController.java:47-61` | `RolldownService.getOverrides` | `ROLLDOWN_VIEW` (`RolldownService.java:92`) |
| `POST /api/tenants/{t}/rolldown-overrides` | `RolldownController.java:63-79` | `RolldownService.createOverride` | `ROLLDOWN_OVERRIDE_SET` (`RolldownService.java:109`) |
| `POST .../rolldown-overrides/{id}/revoke` (or `DELETE .../rolldown-overrides/{id}`) | `RolldownController.java:81-111` | `RolldownService.revokeOverride` | `ROLLDOWN_OVERRIDE_SET` (`RolldownService.java:199`) |

Confirms `V4.0_SPEC_SUMMARY.md` §3's two action codes exactly. The controller's own
`@Tag`/`@Operation` annotations cite Actions #23/#24, matching. On the frontend,
`features/institution-admin/api/rolldownService.ts` and
`features/institution-admin/components/roles/RolldownOverrideModal.tsx` wire this into
`InstitutionAdminPages.tsx` as a modal, not a standalone `/university/...` route in
`Routes.tsx` — so it is reachable only from inside that admin page's UI, not from a direct
URL.

## 3. What the V4.0 spec plans

- **71 standard roles total**, of which Tenant (3: Management, Data Protection Contact,
  Governing Body Approver), University (13), and College/School (33+15, including this
  audience's Principal/HOD/Admin/Accountant/Librarian/IT-Admin roles) are the ones relevant
  here (D3 Role Catalogue, `V4.0_SPEC_SUMMARY.md` §2). Only a fraction of these 61
  tenant-and-below roles have working `app.role_action` rows wired in any given tenant
  today — the "missing roles" screen above is the UI's own acknowledgment of this.
- **Role roll-down (V4-PD-01, V4-RD-01..09):** if a role has no active holder (e.g. a
  standalone school with no Registrar), authority automatically falls to a defined
  stand-in, with "Acting for …" audit badges. Only **non-approval** rights can ever be
  withheld by a tenant override; approvals can never be withheld, because an unapprovable
  chain would block forever. A **never-roll-down list** (platform roles, Student, Parent,
  Applicant, Governing Body Approver, teaching roles) is enforced by a DB trigger
  (`fn_rolldown_override_guard`, `10_v40_rolldown.sql`), not just a UI rule. A
  **particularly non-obvious rule:** the Governing Body Approver loses roll-down authority
  entirely once the tenant has an actual Management role assigned (decision V4-PD-01,
  2026-10-30) — a classic "the stand-in disappears once the real post is filled" rule.
- **60 approval chains, 48 approval processes** (`V4.0_SPEC_SUMMARY.md` §2) govern things
  like fee-exception approval, results publication, and governance item approval — far
  more structured, chain-based approval routing than the simple self-approval-denied check
  built today in `PermissionEvaluatorService`. The code has the self-approval guard and a
  working `ApprovalRequest`/`ApprovalInboxController` mechanism
  (`api/src/main/java/com/edu360/api/approval/`), but the full 48-process/60-chain catalogue
  from the spec is not all wired as distinct backend workflows yet — verify a specific
  chain's existence in `ApprovalInboxService`/`T10DomainService` before asserting it is
  live.
- **Results publication gate (`fn_publish_gate`)** — DB-enforced rule that only the
  mode-correct publisher (`SCHOOL_PRINCIPAL`/`SCHOOL_ADMIN` for schools, `CONTROLLER` for a
  college under a university, `COLLEGE_ADMIN` for standalone/external colleges) may
  publish results, rejecting any mismatch as `WRONG_PUBLISHER_FOR_MODE`
  (`V4.0_SPEC_SUMMARY.md` §1, §3). This exists in the SQL bundle under
  `docs/EDU360_V4.0_Final/.../sql/02_rules.sql` — **not** in the repo-root `sql/` (there is
  none). Now checked line-by-line: `ReportCardController.publish`
  (`ReportCardController.java:23-26`) → `ReportCardService.publish`
  (`ReportCardService.java:52-73`) only calls `permissions.authorize(..., "RESULT_PUBLISH",
  RbacScope.institution(...), ...)` (line 60) — a plain institution-scoped action check,
  with **no** mode-specific "is this role actually the correct publisher for
  school/college-under-university/standalone-college" branch and no
  `WRONG_PUBLISHER_FOR_MODE` error anywhere in the file. **Gap confirmed**: whatever
  mode-correctness the spec requires is enforced only by whichever role happens to hold
  `RESULT_PUBLISH` in `app.role_action` for that tenant, not by a dedicated code-level gate
  — a materially weaker guarantee than the spec's DB trigger. API + screen both exist
  (`ReportCardViewerPage`, `/academic/report-cards`), but this specific publisher-mode rule
  is a real gap, not just an unverified claim.
- **Fee structure state machine** (`DRAFT → PENDING_APPROVAL → LIVE →
  SUPERSEDED/REJECTED`), frozen once out of `DRAFT`, with `fn_allocation_guard` forbidding
  partial payments. `api/src/main/java/com/edu360/api/finance/FeeStructureController.java`
  and `FeeStructureService.java` now checked line-by-line:

  **✅ API + ✅ Screen** (`/finance/structures`, `FeeStructureManagementPage`).

  | Endpoint | Controller:line | Service method | Action code | DB table |
  |---|---|---|---|---|
  | `POST /api/tenants/{t}/fee-structures` | `FeeStructureController.java:19-23` | `FeeStructureService.create` | `FEE_STRUCTURE_CREATE` (`FeeStructureService.java:42`) | `app.fee_structure`, `app.fee_structure_line`, `app.fee_structure_installment` — inserts row with `status='DRAFT'` (line 75) |
  | `POST .../fee-structures/{id}/submit` | `FeeStructureController.java:25-30` | `FeeStructureService.submit` | `FEE_STRUCTURE_CREATE` again (same action code for create and submit, `FeeStructureService.java:143`) | same tables; rejects with `FEE_STRUCTURE_NOT_DRAFT` unless `status='DRAFT'` (lines 144-147), and `FEE_STRUCTURE_EFFECTIVE_DATE_REQUIRED` for a revised (version > 1) structure with no effective date (148-151) |

  `submit` also resolves a mode-based approver role (`SCHOOL_PRINCIPAL` vs
  `PRINCIPAL_COLLEGE` by `institution_type`, line 153-154) and requires an active role
  selected via `TenantContext.currentActiveRoleCode()` (155-158) before creating an
  `ApprovalRequest`/`ApprovalStep` row — so the DRAFT→PENDING_APPROVAL transition (and the
  mode-aware approver choice) **is** implemented in `FeeStructureService`, closer to spec
  parity than the report-card publish gate above. The `LIVE`/`SUPERSEDED`/`REJECTED` later
  states and `fn_allocation_guard`'s partial-payment prohibition were not traced past
  `submit` in this pass — only `create`/`submit` were opened.

  Payments: `api/src/main/java/com/edu360/api/finance/FeePaymentController.java` — **✅ API
  + ✅ Screen** (`/finance/payments`, `FeePaymentPortalPage`).

  | Endpoint | Controller:line | Service method | Action code | DB table |
  |---|---|---|---|---|
  | `POST /api/tenants/{t}/payments/checkout` | `FeePaymentController.java:21-30` | `OnlinePaymentCheckoutService.start` | `FEE_PAY_ONLINE` (`OnlinePaymentCheckoutService.java:78`) | `app.gateway_order`, `app.payment_intent_item` |
  | `GET .../payments/{paymentId}/receipt` | `FeePaymentController.java:32-41` | `FeeReceiptService.getReceipt` | `FEE_VIEW_OWN` (own payer) or `LEDGER_VIEW` (institution-scoped staff view) (`FeeReceiptService.java:81,83`) | `app.payment`, `app.receipt`, `app.payment_allocation` |

## 4. Role restrictions

### Most senior/complex role: Management (tenant-wide top post) / University Admin

Per `PermissionEvaluatorService.topPostCovers(...)` (lines 497-498), `MANAGEMENT` and
`UNIVERSITY_ADMIN` are the only roles that cover **any** tenant-wide scope purely by
holding a `ScopeType.TENANT` assignment — this is the real, enforced version of "the top
post at each level does everything" (the project's own private note on top-post
authority).

**What it cannot do, with the mechanism:**
- **Cannot approve its own request.** `SELF_APPROVAL_FORBIDDEN` fires before any role
  check at all (`PermissionEvaluatorService.java:197-202`) whenever
  `effectiveTarget.targetUserAccountId()` equals the caller's own account and the action is
  flagged as an approval action in `ref.action_catalog`. This holds even for Management —
  there is no "top post bypasses self-approval" branch anywhere in the class.
- **Cannot act on a personal student/parent/applicant action even via top-post
  coverage.** `PERSONAL_ACTIONS` (lines 67-73: things like `FEE_PAY_ONLINE`,
  `ASSIGNMENT_SUBMIT`, `STUDENT_LEAVE_APPLY`) are explicitly excluded from coverage (lines
  259-262) — Management can administer the system but can never, for example, "pay a
  student's fee as if it were their own" through the top-post path.
- **Cannot touch `LOCKED_ACTIONS`** (`PAYROLL_APPROVE_TOTALS`, `SALARY_INDIVIDUAL_VIEW`,
  `TENANT_EXPORT_APPROVE`, `TENANT_DELETION_APPROVE`, `RETENTION_CHANGE_APPROVE`) via
  top-post coverage either (lines 263-266) — these stay with a named holder regardless of
  organisational seniority (Decision D / TPA-04).
- **Cannot see or decide `POT05_CONFIDENTIAL_ACTIONS`** (grievance case view/assign/
  hearing/resolve/evidence) unless it specifically holds one of the three authorized role
  codes — `INTERNAL_COMPLAINTS_COMMITTEE`, `PRINCIPAL_COLLEGE`, `SCHOOL_PRINCIPAL`
  (`POT05_AUTHORIZED_ROLE_CODES`, lines 92-94) — and even then only as a real, active,
  standard (non-custom) role whose base matches and is flagged sensitive (lines 244-251).
  Management holding neither of those three role codes is denied `POT05_ROLE_FORBIDDEN`
  outright, regardless of seniority — grievance confidentiality (PO-T05) deliberately
  overrides the top-post rule.
- **Cannot act with two simultaneous ACTIVE assignments at the same exact scope.** If that
  state ever exists, the check logs a warning and denies with `AMBIGUOUS_ROLE_ASSIGNMENT`
  rather than merging them (lines 215-223) — a defensive rule against ever accidentally
  combining two grants.
- **Cannot see or act outside this tenant at all.** RLS forces `tenant_id` matching on
  every `app.*` table regardless of role (D4/§3); even Management's broadest coverage
  never crosses a tenant boundary.

**What happens if it tries anyway:** `authorize(...)` throws `UnauthorizedException` (403)
via `ActiveRoleRequiredException` (409) for the choice-required case, or plain
`UnauthorizedException` otherwise (`PermissionEvaluatorService.java:286-300`), handled by
`GlobalExceptionHandler` into `{timestamp, status, error}` with `fieldErrors` only on
validation failures. The 403 response also tries to name 1-2 roles that *do* hold the
action (`heldByRoleNames`, lines 324-341) — unless the action itself is sensitive (locked,
personal, or a non-"Normal" sensitivity class), in which case the UI gets a generic
message instead of a role list (`isSensitiveAction`, lines 308-315) — a deliberate
anti-disclosure rule: even telling someone *who* can approve a sensitive action is itself
information the system withholds.

### Delta for other tenant-admin roles

All other roles in this audience go through the exact same `can(...)`/`authorize(...)`
method — the mechanism never changes, only the scope the role's assignment carries and
whether top-post coverage applies:

- **Vice Chancellor** — top-post coverage extends to its own institution as well as the
  tenant (`topPostCovers`, line 499-500: `ScopeType.TENANT` **or** a matching
  `ScopeType.INSTITUTION`), narrower than Management's blanket tenant coverage, but still
  wider than a plain institution-scoped role.
- **Principal (College/School)** — top-post coverage only within its own institution, and
  explicitly **never** at tenant scope (`scope.scopeType() != ScopeType.TENANT`, line
  502-503) — a Principal can never act as if a tenant-wide action applied to them, even if
  no one else holds it. It is also one of the two role codes trusted for PO-T05
  confidential grievance actions (along with the Internal Complaints Committee).
- **Governing Body Approver / Finance Director** — get a narrow, named exception rather
  than general top-post coverage: they may act on `CONCESSION_APPROVE` or
  `SCHOLARSHIP_LIST_APPROVE` only, and only at tenant scope (lines 490-496) — this is PO-T04
  from the code comment, a deliberately limited stand-in power, not the broad coverage
  Management/VC get. Per the spec (§2 above), the Governing Body Approver additionally
  loses even its roll-down authority once a real Management role exists for the tenant.
- **Head of Department and every other department/institution-scoped role** — no top-post
  coverage branch names them at all, so `sameScope(...)` must match **exactly**
  (`ScopeType.DEPARTMENT` with the same institution and department IDs, lines 479-483);
  there is no fallback. A HOD acting outside their own department, or a College Admin
  acting on another institution's record, is denied `NO_ACTIVE_ROLE_IN_SCOPE` with no
  exception path.
- **Any tenant admin role holding a custom (tenant-defined) role** instead of a standard
  one is still checked the same way, but is additionally blocked from ever being granted a
  `LOCKED_ACTIONS`, `PLATFORM_ONLY_ACTIONS`, or `POT05_CONFIDENTIAL_ACTIONS` item in the
  first place — `isLockedForCustomRoles(...)` (lines 115-119) is the guard used wherever
  custom roles are built, so no amount of tenant-side role configuration can hand a custom
  role power the spec calls permanently locked.

## Client Pitch Summary

*This section serves two readers at once. The "On screen"/"Speaker notes" parts are the
plain-English client pitch. Each feature is immediately followed by a "Developer detail"
block with full technical depth so a new engineer could pick up the feature from this
section alone. Status labels for the client: "Live today", "Built, screen coming soon",
"On the roadmap".*

**On screen — what your Admin team can do today:**
- Set up institution structure: departments, programs, academic terms
- Manage staff records, roles and permissions
- Run fee structures, approvals and finance
- Oversee student records, results and appraisals

**Speaker notes:** This is the day-to-day control centre for your institution — structure,
people, money, and records. Not every one of the 71 possible admin roles in our long-term
design is switched on yet for every client, but the core operations your institution needs
on day one are already working. Status: Live today (core flows); full role catalogue still
growing.

**Developer detail — tenant authorization mechanism (applies to every feature below, read
this first):**

Like the platform side (see `platform.md`'s Client Pitch Summary for the full explanation
of the imperative-vs-`@PreAuthorize` pattern and action codes, if you haven't read it yet),
every tenant-side create/update/delete flow in this codebase calls one explicit line of
Java: `permissionEvaluatorService.authorize(principal, "ACTION_CODE", scope, target)`
(`api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java`). The tenant side
is more elaborate than the platform side, though, because a tenant has many more roles,
organized into a real hierarchy (Tenant → Institution → Department), and the same action
code (e.g. "view a student record") can mean something different depending on *whose*
students and *which* institution. Reading this class top to bottom, here is the exact order
of checks it runs — a junior developer debugging "why was this denied?" should walk this
list in order, because an early check can deny a request for a completely different reason
than a later one would have:

1. **Does the action code even exist?** Every permission in this system is registered as a
   row in a reference table, `ref.action_catalog` — think of it as the master dictionary of
   every possible action code in the whole application. If someone calls `authorize(...)`
   with a typo'd or made-up action code, it's denied immediately (line 171-174), before any
   role or scope logic runs at all. This protects against a bug where a new feature is
   wired up to check a permission that was never actually registered — better to fail
   loudly than to silently allow everything because the lookup found nothing to deny.
2. **Does the user need to pick which role they're acting as?** Some users legitimately
   hold more than one role at once (e.g. someone could be both a Subject Teacher and a HOD).
   If they haven't told the system which one they're currently "wearing," the system does
   **not** try to be clever and merge both roles' permissions together — instead it returns
   a `409 ACTIVE_ROLE_REQUIRED` (lines 224-225) and makes the user explicitly choose. The
   reason this matters: silently combining two roles' permissions could accidentally grant
   someone a combination of powers nobody intended them to have at the same time (for
   instance, combining a request-raising role with an approving role, defeating the
   separation of duties the whole system is built around).
3. **Does the role's scope match where the action is being performed?** This is the most
   important concept to understand on the tenant side. Every role assignment carries a
   "scope" — `TENANT` (the whole school group), `INSTITUTION` (one specific school/college
   in the group), or `DEPARTMENT` (one department inside one institution). The rule is that
   the action's required scope and the role's assigned scope must match **exactly** — a
   Head of Department assigned at the Computer Science department cannot act on Mechanical
   Engineering's data, even within the same institution, because the scopes don't match.
   There is one deliberate, named exception to "exact match only," called **top-post
   coverage** (`topPostCovers(...)`, lines 486-506): certain senior roles are trusted to
   act across a wider scope than their own assignment technically names, because
   organizationally they really do sit above everyone else. Specifically: `MANAGEMENT` and
   `UNIVERSITY_ADMIN` cover the *entire tenant* just by holding a tenant-level assignment;
   `VICE_CHANCELLOR` covers either the tenant or their own institution; a `PRINCIPAL`
   (college or school) covers only their own institution, and explicitly never the whole
   tenant, no matter how senior they feel. Crucially, top-post coverage is switched *off*
   for two categories of action, hard-coded as exclusion lists: `PERSONAL_ACTIONS` (line
   259-262) — things that only make sense done by the specific person they belong to, like
   a student paying their own fee — and `LOCKED_ACTIONS` (line 263-266) — things considered
   too sensitive to ever be covered by generic seniority, like approving payroll totals.
   Both of those lists are explained with real examples in the role-restriction sections
   below.
4. **Is this person trying to approve their own request?** Before even looking at roles,
   the system checks whether the record being approved belongs to the person doing the
   approving, and if so, denies it outright with `SELF_APPROVAL_FORBIDDEN` (lines
   197-202) — no role, however senior, can override this. This is the code-level version
   of a rule every auditor looks for: nobody should be able to approve their own request,
   because that turns an approval step into a rubber stamp and opens the door to fraud.
5. **Is the role itself currently active?** Roles aren't just "exists or doesn't" — each
   one has a lifecycle status in `app.role_definition` (`DRAFT`, `ACTIVE`, `INACTIVE`,
   `RETIRED`, `ARCHIVED`). Even if a role has permissions wired up in the permissions table,
   if the role itself isn't `ACTIVE`, nothing it's wired to grants anything
   (`ROLE_NOT_ACTIVE`, lines 235-240). This lets an institution retire or draft a role
   (e.g. while redesigning its structure) without having to go delete every individual
   permission row first — flipping one status field disables the whole role at once.
6. **Is the specific action actually granted to this role, right now?** The final check
   reads `app.role_action` (through `RoleActionRepository`,
   `api/src/main/java/com/edu360/api/rbac/RoleActionRepository.java`) — this database table
   is the real, live source of truth for "which role can do which action," distinct from
   the big static permission matrix described in the design-spec documents. The spec
   documents describe what *should* eventually be true everywhere; `app.role_action` is
   what's *actually* configured for this specific tenant, right now, and it's what the code
   actually checks.

Sign-in for this audience works exactly like the mechanism explained in full in
`platform.md`'s Client Pitch Summary (same `Host`-header-picks-the-realm logic, same
`POST /api/auth/login`, same per-request token re-verification) — the one difference is
that a successful tenant login produces a `TenantPrincipal`
(`api/src/main/java/com/edu360/api/config/TenantPrincipal.java`) instead of a
`PlatformPrincipal`, and critically, the request also sets something called
`TenantContext`. Remember from `platform.md` that Postgres enforces Row-Level Security
(RLS) and only shows `app.*` rows to a request that has set a tenant-identifying session
variable — `TenantContext` is exactly that: setting it is what makes this tenant's own
rows visible to this request at the database level, on top of (not instead of) all the
Java-level checks above.

On the frontend, admin routes live under `/university/...` and `/tenants/...` in
`app/src/routes/Routes.tsx:192-236`, wrapped in a component called `UniversityAdminRoute`
(`Routes.tsx:168`). This component is deliberately **fail-closed**: rather than showing the
page and hoping the backend blocks any bad request, it refuses to even render the page —
redirecting away instead — until the browser has definite, confirmed proof of what the
signed-in user is allowed to do. The practical effect today is that `/university/*` is
unreachable for most users until that confirmed-permissions data is wired up for them,
which is a stricter, safer failure mode than "show the page and let the API calls inside it
fail individually." Each individual route is further wrapped in `<ActionGate
anyOf={...}>`, naming the specific action code(s) a user needs to even see that menu item —
e.g. `/university/institutions` requires `TENANT_ROUTE_ACTIONS.institutions`
(`Routes.tsx:195`), `/university/staff` requires `TENANT_ROUTE_ACTIONS.staffRecords`
(`Routes.tsx:205`), `/university/roles` requires `TENANT_ROUTE_ACTIONS.roles`
(`Routes.tsx:208`). It's worth stressing to any junior developer: this frontend gating is a
**user-experience convenience**, hiding buttons and menus a user can't use — it is not a
security boundary by itself. The real security boundary is always the backend's
`authorize(...)` call; a determined user could, in principle, call the API URL directly
even if the frontend hid the button, and the backend check above is what actually stops
them.

What the user sees on a denial: `authorize(...)` throws `UnauthorizedException`, which
becomes an HTTP 403, or (for the "you hold two roles, pick one" case) a 409 via
`ActiveRoleRequiredException`. Both get turned into the same consistent JSON shape,
`{timestamp, status, error}` (with `fieldErrors` added only for ordinary form-validation
failures), by `GlobalExceptionHandler` — the same shared error handler used everywhere in
this codebase, so the frontend only needs one error-handling code path. One more detail
worth knowing: the 403 response normally tries to be helpful and names 1-2 roles that *do*
hold the action the user was denied (`heldByRoleNames`, lines 324-341), so a user can go ask
the right person — *except* when the action itself is flagged sensitive (locked, personal,
or a non-"Normal" sensitivity classification), in which case the system deliberately omits
that hint and shows a generic message instead (`isSensitiveAction`, lines 308-315). This is
an intentional anti-disclosure rule: for a sensitive action, even revealing *who* is allowed
to do it is itself information the system doesn't want to leak to someone who was just
denied.

**Feature — Staff appraisals and HR**
- On screen: run yearly appraisal cycles for staff; department heads score, then the
  Principal reviews; no one can score their own appraisal.
- Speaker notes: e.g. a department head reviews their team's appraisals, and the Principal
  then signs off, but a department head can never score themselves. The system blocks that
  automatically, closing a common fraud and favouritism risk.
- Status: Live today.
- **Developer detail:** The whole feature lives in one controller,
  `api/src/main/java/com/edu360/api/staff/controller/StaffAppraisalController.java`, and
  maps neatly onto the real-world workflow: a cycle gets created, staff submit into it, a
  Head of Department scores it, then the Principal reviews it. Each step is its own
  endpoint with its own scope-level permission check, which is worth noticing as a pattern:
  rather than one generic "update appraisal" endpoint with internal if/else branches, the
  team gave each workflow step its own URL and its own explicit permission check — this
  makes it much easier to answer "who can do step 3?" just by reading one method, instead
  of tracing a branching function.

  Step 1, creating the cycle itself (e.g. "2026 annual appraisal"): `POST
  .../institutions/{institutionId}/appraisals/cycles` (line 33) is called once by an admin
  at the start of the cycle. It writes a new row into the `app.appraisal_cycle` table, and
  the permission check here is institution-scoped — meaning the caller's role assignment
  must cover that specific institution (directly, or via top-post coverage as explained
  above), so a Principal at School A can never accidentally create an appraisal cycle for
  School B.

  Step 2, staff/admin viewing progress: `GET .../appraisals/cycles/{cycleId}/submissions`
  (line 50) reads from `app.appraisal_submission`, with a read-scoped `authorize` call —
  read access still goes through the same permission check as a write, just checking a
  "view" action code instead of a "manage" one; reads are not an exception to the
  authorization rule in this codebase.

  Step 3, the actual scoring: `POST .../submissions/{submissionId}/hod-score` (line 72) is
  where a Head of Department records their score for one staff member's appraisal,
  updating the same `app.appraisal_submission` row, gated by a department-scoped
  `authorize` call (so a HOD can only score people inside their own department — see check
  #3 in the authorization mechanism above). This endpoint is also where the self-approval
  rule described above becomes concrete and visible: if it turns out the HOD being asked to
  score a submission is scoring *their own* submission (e.g. a HOD who is also a subject
  teacher being appraised that cycle), `SELF_APPROVAL_FORBIDDEN` fires before the system
  even looks at whether their role would otherwise be allowed (`PermissionEvaluatorService.
  java:197-202`). In practice, this means a HOD's own appraisal has to be routed to and
  scored by the Principal instead — there is no code path where a HOD scores themselves,
  no matter how the request is shaped.

  Step 4, the Principal's sign-off: `POST .../submissions/{submissionId}/principal-review`
  (line 79) is the Principal's own review step on that same `app.appraisal_submission` row,
  gated by an institution-scoped `authorize` call (wider than the HOD's department-level
  check, matching the Principal's broader seniority).

  Known gap, worth flagging to anyone picking this up: the full staff HR module described
  in the design docs — including a staff "service book" (employment history record) and
  credential verification (checking teaching qualifications) — is scoped in
  `docs/tenant-agent-prompts/T3-staff-hr-leaves-and-appraisals.md` (Migrations 59-60), but
  this pass only confirmed the appraisal-cycle slice above is actually built. Before
  telling a client "service book and credential verification are live," a developer should
  independently check for those controllers/services; they weren't found and verified in
  this review.

**Feature — Student records**
- On screen: full searchable student directory; staff can add notes to a student's record;
  notes can never be edited or deleted once saved.
- Speaker notes: staff can look up any student and add notes, but once a note is saved, it's
  permanent. No one can go back and quietly edit the record later. This protects the
  integrity of your student history for audits, disputes, and legal situations.
- Status: Live today.
- **Developer detail:** Two endpoints cover this feature, both in
  `api/src/main/java/com/edu360/api/student/StudentDirectoryController.java`. `GET
  /api/tenants/{tenantId}/students` (line 20) is the search/list endpoint behind the
  student directory screen; it reads from two tables, `app.student_profile` (the student's
  core identity/demographic data) and `app.student_enrollment` (which class/section/year
  they're currently enrolled in) — a student directory almost always needs both, which is
  why the query joins them. Access is gated by the `STUDENT_SEARCH` action code; notice
  that the exact same action code is also what the frontend's `<ActionGate>` checks for the
  `/tenants/students` route (`Routes.tsx:231`) — this is a good example of one action code
  being the single source of truth for both "can the API be called" and "should the menu
  item even be shown," which keeps the backend and frontend from drifting out of sync with
  each other.

  `POST .../students/{studentId}/notes` (line 33) is how a staff member attaches a free-text
  note to a student's record (e.g. "spoke to parent about attendance on 12 Nov"), writing a
  new row into `app.student_note`. The permission check here is institution/department
  scoped, matching the normal rule that staff can only act on students within the part of
  the organization their role covers.

  The genuinely interesting part of this feature, for a junior developer, is *where* the
  "notes can never be edited or deleted" promise is actually enforced. It would be easy to
  assume this is just "the UI doesn't show an edit button" — but it's not; it's enforced
  one layer deeper, inside the database itself, by a Postgres trigger function called
  `fn_note_guard`. A trigger is a piece of logic the database runs automatically whenever
  a certain kind of statement (here, an UPDATE) is attempted against a table — `fn_note_guard`
  is specifically written to reject any UPDATE statement against `app.student_note`,
  unconditionally. The practical consequence: even if a future developer wrote a new "edit
  note" API endpoint and forgot to add a permission check, or even if someone connected
  directly to the database with a SQL client and tried to run `UPDATE app.student_note SET
  ...`, the database itself would refuse it. This is a stronger guarantee than an
  application-level rule, because it doesn't depend on every single piece of code that
  might ever touch this table remembering to respect the rule.

**Feature — Fee structures: a safe approval path**
- On screen: fee structures start as a draft, then go for approval; the correct approver
  depends on institution type automatically; once approved, nothing can be changed quietly.
- Speaker notes: e.g. an accountant drafts next year's fee structure, submits it, and the
  system automatically routes it to the right approver for that type of institution — a
  school principal, or a college's controller — without anyone picking the wrong person by
  mistake.
- Status: Live today.
- **Developer detail:** This feature is a good example of a **state machine** — a fee
  structure moves through a fixed sequence of named states (`DRAFT` → `PENDING_APPROVAL` →
  eventually `LIVE`/`SUPERSEDED`/`REJECTED`), and the code's job at each step is to check
  "is this record currently in a state where this action is even allowed?" before doing
  anything. The code lives in
  `api/src/main/java/com/edu360/api/finance/FeeStructureController.java` and
  `FeeStructureService.java` (the controller receives the HTTP request and checks
  permissions; the service class is where the actual state-machine logic and database
  writes happen — this controller/service split is the standard pattern throughout this
  codebase), behind the screen `/finance/structures` (`FeeStructureManagementPage`).

  Creating a draft: `POST /api/tenants/{t}/fee-structures`
  (`FeeStructureController.java:19-23`) calls `FeeStructureService.create`, checking the
  `FEE_STRUCTURE_CREATE` action code (`FeeStructureService.java:42`). This inserts rows
  into three related tables at once — `app.fee_structure` (the structure's header
  information), `app.fee_structure_line` (the individual fee items that make it up, e.g.
  "tuition," "library fee"), and `app.fee_structure_installment` (how it's split across
  payment installments) — all created together with `status='DRAFT'` (line 75). Splitting
  one logical "fee structure" across three tables like this is a normal relational-database
  pattern: the header holds one row of shared information, while the lines and
  installments each need their own row per item, so they live in their own child tables
  linked back to the header.

  Submitting for approval: `POST .../fee-structures/{id}/submit`
  (`FeeStructureController.java:25-30`) calls `FeeStructureService.submit`. One detail
  worth flagging explicitly for a future developer: this re-uses the *same*
  `FEE_STRUCTURE_CREATE` action code (`FeeStructureService.java:143`) rather than having a
  separate "submit" action code of its own. Practically, this means there's no way today to
  grant someone permission to create a draft but *not* submit it (or vice versa) — if you're
  ever asked to build a role that can draft but needs someone else to submit, you'd need to
  add a new, separate action code first; the current wiring doesn't support that split.
  Before actually transitioning the state, `submit` runs two validation checks and refuses
  to proceed if either fails: it rejects with `FEE_STRUCTURE_NOT_DRAFT` unless the record's
  current `status` is still `'DRAFT'` (lines 144-147) — this stops someone accidentally
  re-submitting something already submitted, or submitting something that was already
  rejected — and it rejects with `FEE_STRUCTURE_EFFECTIVE_DATE_REQUIRED` if this is a
  *revised* structure (version number greater than 1) with no effective date set (lines
  148-151) — a revision needs a clear date for when the new fees start applying, otherwise
  it would be ambiguous which structure applies to a payment made in between. Once those
  checks pass, the code figures out *who* should approve this submission — not a fixed
  person, but a role chosen based on the kind of institution: `SCHOOL_PRINCIPAL` for a
  school, `PRINCIPAL_COLLEGE` for a college, decided by looking at the institution's
  `institution_type` column (lines 153-154). It then requires that the submitting user has
  an active role selected (via `TenantContext.currentActiveRoleCode()`, lines 155-158 —
  the same "you must pick which role you're acting as" rule explained in the general
  mechanism above), and finally creates an `ApprovalRequest`/`ApprovalStep` row, which is
  this codebase's general-purpose mechanism for routing something to a specific approver
  and tracking whether they've acted on it yet. So, concretely: the DRAFT → PENDING_APPROVAL
  transition, *and* the "figure out the right kind of approver automatically" logic, really
  are implemented in working code here, not just described in a spec document.

  Known gap, important for whoever picks this up next: this review only read as far as
  `create` and `submit`. The later states — `LIVE` (approved and currently in effect),
  `SUPERSEDED` (replaced by a newer version), `REJECTED` — and a database rule called
  `fn_allocation_guard`, which the design spec says should forbid partial payments against
  a fee structure, were **not** independently traced through the code in this pass. Don't
  assume they work exactly as specified without reading the rest of `FeeStructureService`
  yourself first.

  Payments (a related but separate feature): `api/src/main/java/com/edu360/api/finance/
  FeePaymentController.java`, behind the screen `/finance/payments`
  (`FeePaymentPortalPage`). `POST /api/tenants/{t}/payments/checkout`
  (`FeePaymentController.java:21-30`) is what a student (or their parent) calls to start an
  online payment; it calls `OnlinePaymentCheckoutService.start`, checking the `FEE_PAY_ONLINE`
  action code (`OnlinePaymentCheckoutService.java:78`) — note `FEE_PAY_ONLINE` is one of
  the "Personal" actions discussed in the general mechanism above, meaning even a senior
  admin role can never trigger this action on someone else's behalf through top-post
  coverage; only the actual fee-payer (or a specifically-granted role) can call it. It
  writes to `app.gateway_order` (the order sent to the payment gateway) and
  `app.payment_intent_item` (which specific fee line items this payment is meant to cover).
  `GET .../payments/{paymentId}/receipt` (`FeePaymentController.java:32-41`) retrieves a
  receipt via `FeeReceiptService.getReceipt`, checking either `FEE_VIEW_OWN` (if the caller
  is the person who actually paid) or `LEDGER_VIEW` (if the caller is institution-scoped
  staff looking at someone else's receipt for administrative reasons) —
  (`FeeReceiptService.java:81,83`) — reading from `app.payment`, `app.receipt`, and
  `app.payment_allocation` (which records exactly how one payment amount was split across
  multiple fee line items, since a single payment can cover several fees at once).

**Feature — Results publication: a known gap, flagged honestly**
- On screen: our long-term design requires the correct role to publish results for each
  institution type; today's build checks permission, but not yet that specific role-match
  rule.
- Speaker notes: we're being candid here — today, publishing results checks that someone is
  permitted to publish, but doesn't yet double-check they're specifically the right kind of
  approver for your type of institution. It's a known, tracked gap, not a hidden one, and
  it's on our near-term list to close.
- Status: Built, screen coming soon (the screen and the basic permission check exist; the
  stricter role-match rule is not yet built).
- **Developer detail:** This is a genuinely useful example for a junior developer of the
  gap between "what the design spec says should happen" and "what the code actually does
  today" — and it's worth reading carefully, because the same kind of gap could exist
  elsewhere without being written down as clearly as it is here.

  What the spec says: a school can run in different "modes" depending on its organizational
  type — a standalone school, a college that belongs under a university, or a standalone
  college with no parent university — and the *correct* person to publish final results is
  different for each mode: `SCHOOL_PRINCIPAL`/`SCHOOL_ADMIN` for a school,
  `CONTROLLER` for a college that sits under a university, `COLLEGE_ADMIN` for a standalone
  college. The spec's intended enforcement mechanism is a database function called
  `fn_publish_gate`, which would reject any publish attempt from the wrong kind of role for
  that institution's mode with a specific error, `WRONG_PUBLISHER_FOR_MODE`. That function
  exists only inside the design-spec package's own SQL file
  (`docs/EDU360_V4.0_Final/.../sql/02_rules.sql`) — it has **not** been copied into this
  repository's actual, live SQL migration folder (`sql/`, at the repo root), which is the
  only place database objects actually get created in this project (see this repo's
  `CLAUDE.md` on database migrations). In other words: the rule is fully designed, but
  never built.

  What the code actually does: the real publish flow is `ReportCardController.publish`
  (`ReportCardController.java:23-26`), which calls `ReportCardService.publish`
  (`ReportCardService.java:52-73`). Reading that method, the *only* permission check it
  performs is `permissions.authorize(..., "RESULT_PUBLISH", RbacScope.institution(...),
  ...)` (line 60) — a completely ordinary institution-scoped action check, exactly like
  every other "does this role hold this action in this institution" check described in the
  general mechanism above. There is no extra code anywhere in this file that looks at the
  institution's mode and compares it against the caller's specific role, and there is no
  `WRONG_PUBLISHER_FOR_MODE` error anywhere in the file.

  Why this is a real, confirmed gap and not just a theoretical risk: imagine a tenant's
  admin accidentally (or carelessly) grants the `RESULT_PUBLISH` action to a role that
  shouldn't hold it for that institution's mode — say, granting it to a `CONTROLLER` role
  at a standalone school that should only ever let its `SCHOOL_PRINCIPAL` publish. Today's
  code would allow that publish to go through, because all it checks is "does this role
  hold `RESULT_PUBLISH` for this institution," not "is this specifically the *correct kind*
  of role for *this* institution's mode." The spec's database trigger would have been a
  second, independent line of defence against exactly that misconfiguration; without it,
  the only thing standing between a misconfigured role grant and a wrongly-published result
  is whoever set up `app.role_action` for that tenant getting it right in the first place.

  What to do about it, for whoever picks this up: there are two ways to close this gap, and
  they are not equally strong. The weaker fix is adding an explicit mode-check inside
  `ReportCardService.publish` itself — straightforward, but it only protects this one
  service method; if another future code path also needs to publish results, it would have
  to remember to add the same check again. The stronger fix is actually porting
  `fn_publish_gate` into the repo-root `sql/` bundle as a real database trigger — that
  protects the rule at the data layer itself, the same way `fn_note_guard` protects student
  notes (see the Student records feature above), so it would catch *any* future code path
  that tries to publish results incorrectly, not just this one. The screen and the basic API
  both otherwise work fine today (`ReportCardViewerPage`, route `/academic/report-cards`) —
  this gap is specifically about the missing extra layer of role-mode validation, not about
  the feature being broken end to end.

**Who can do what — Institution Admin roles**

| Action | Management / University Admin | Vice Chancellor | Principal | Head of Department | Governing Body Approver |
|---|---|---|---|---|---|
| Act institution-wide | Yes | Limited (own institution) | No | No | No |
| Approve own request | No | No | No | No | No |
| View confidential grievance cases | No* | No* | Yes | No | No |
| Approve salary / payroll totals | No | No | No | No | No |
| Approve fee concessions | Limited | Limited | Limited | No | Yes |

*Only the Principal and the designated grievance committee can see confidential grievance
cases — not even the top management role, unless they specifically hold that role.

**Why the top role still has limits (plain terms):** even the most senior role in your
institution — Management — has real limits, and that's by design, not an oversight. They can
never approve their own request, they can never see confidential grievance cases unless
they're specifically the Principal or grievance committee, and they can never touch locked,
highly sensitive actions like payroll totals, no matter how senior they are. This protects
your institution from both fraud and from any single point of failure.

**Coming next / planned:**
- Role roll-down: if a post is vacant, authority automatically passes to a defined stand-in,
  so approvals never get permanently stuck; the institution can withhold a stand-in's
  everyday duties, but never their ability to approve. Status: On the roadmap.
- A much larger catalogue of ready-made admin roles (71 in total across all levels); only a
  portion of these are switched on for any given institution today. Status: On the roadmap.
- A full set of 48 structured approval processes across 60 approval chains (today a smaller,
  working slice of this exists). Status: Built, screen coming soon / On the roadmap
  (depending on the specific process).
- Stricter publisher-role check for results publication (see feature above). Status: On the
  roadmap.
- A dedicated screen for setting attendance rules (the underlying function already works, no
  dedicated screen yet). Status: Built, screen coming soon.
