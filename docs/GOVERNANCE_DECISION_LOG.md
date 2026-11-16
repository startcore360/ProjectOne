# EDU 360 — Governance Decision Log

This log records Product Owner decisions on open items from
`docs/EDU360_V4.0_Final/02_Product_Governance_and_Action_Register/*.xlsx` ("Open Decisions 136")
as they are formally approved or deferred. It is an **addendum**, not a replacement — the
original governance register sheet is the historical record of record and is never edited in
place; this log is where a decision's current status, impact and traceability live going
forward. Where this log and the original register disagree on a decision's status, this log is
authoritative for anything dated after it (see each entry's date), and the disagreement itself is
flagged, not silently resolved.

Cross-referenced from `docs/V4.0_SPEC_SUMMARY.md` §2 and §8 (navigation table).

**Decided by:** Product Owner, per message received 2026-11-04 (see "Source of this round" below).

---

## Decisions in this round — 2026-11-04

| Decision | Status |
|---|---|
| OD-03 | Approved |
| OD-10 / OD-80 | Approved |
| OD-09 | Approved |
| OD-01 | Approved |
| OD-62 | Deferred |
| OD-134 | Approved |
| SOURCE-GAP | Approved for next revision |

---

### OD-03 — Institution-level RLS

- **Decision title:** Whether institution-level RLS is enforced as an additional scoping layer under tenant-level RLS.
- **Product Owner decision:** Approved. Implement PTD-I1 exactly as already defined in the committed SQL bundle (`sql/03_bindings.sql`'s `RESTRICTIVE p_institution` policies on tables with an `institution_id` column).
- **Status:** Approved.
- **Architectural / functional impact:** Every request must carry institution scope (not just tenant scope) from the identity through to the DB session. This is additive to the existing tenant-RLS mechanism (`TenantContext` / `TenantRoutingDataSource`'s `SET app.tenant_id`) — it does not replace or weaken it.
- **Affected documents:** D1 (Part I, PTD-I1), D4 (`04_Database_and_Technical_Architecture.md`, `sql/03_bindings.sql`), `docs/V4.0_SPEC_SUMMARY.md` §3.
- **Affected epics/stories:** Institution setup (all 22 stories — nearly every one has "Scope: Own institution/college/school"); Users & roles (`USER_VIEW`, `ROLE_ASSIGN*` — scope checks); cross-cutting Foundation item 5 (tenant/institution context).
- **Dependencies:** None blocking — the SQL-side policies already exist and pass (verified live 2026-11-04: 97/97 SQL tests, 160 RLS-forced tables, re-run against the local Postgres 16.15 container). The only missing piece is the application-side GUC wiring.
- **Implementation status:** **Not implemented.** `TenantContext` (`api/src/main/java/com/edu360/api/tenant/TenantContext.java`) currently carries only `tenantId` + `isolationMode` + `dbConnectionRef`. There is no `institutionScope`/`institutionIds` field, and `TenantRoutingDataSource` sets only `app.tenant_id` on connection borrow — `app.institution_scope`/`app.institution_ids` are never set. Until this is built, institution-scoped queries fail closed (return nothing), per the existing fail-closed design — this is safe, just incomplete.
- **Outstanding actions:** Build as Foundation item 5 (tenant/institution context) — add `institutionScope`/`institutionIds` to `TenantContext`, set them from the verified identity (not the request body), wire `SET`/`RESET` for both new GUCs the same way `app.tenant_id` already works, do not redesign or weaken the existing `p_tenant`/`p_institution` SQL policies.

---

### OD-10 / OD-80 — Keycloak identity linkage

- **Decision title:** Keycloak realm model and application-user ↔ Keycloak-identity linkage.
- **Product Owner decision:** Approved. Accept the existing architecture as-is: realm-per-tenant Keycloak model, `user_identity_link` mapping, `UserIdentityLink`/`TenantUserLookup` as the architectural basis. Do not redesign.
- **Status:** Approved.
- **Architectural / functional impact:** None beyond what's already running — this decision ratifies existing code rather than requiring new work. `docs/KEYCLOAK_MIGRATION_PLAN.md` is no longer purely a plan for this part; its realm-per-tenant + identity-link proposal is confirmed as the accepted design.
- **Affected documents:** `docs/KEYCLOAK_MIGRATION_PLAN.md`, D1 (security/identity sections).
- **Affected epics/stories:** Sign-in and account (`AUTH_LOGIN`), Foundation item 6 (Identity).
- **Dependencies:** None.
- **Implementation status:** **Mostly implemented.** `identity/UserIdentityLink`, `identity/UserAccount`, `tenant/LoginTenantResolver`, `config/TenantIssuerAuthenticationManagerResolver` all exist and are covered by `TenantIssuerAuthenticationManagerResolverIT` (7 tests passing as of the last full suite run). **Not yet implemented:** per-role-type login rules (staff email+password / college-student Student-ID+password / parent mobile OTP / applicant OTP), two-step login for the roles the spec requires it for, and device/session management (`AUTH_DEVICES_MANAGE`).
- **Outstanding actions:** None required by this decision alone — the remaining gaps above belong to Sign-in epic stories 1 and 4, to be scoped in their own briefs.

---

### OD-09 — Audit-store integrity

- **Decision title:** How audit records are protected from tampering.
- **Product Owner decision:** Approved. Implement append-only audit records: a DB trigger blocking UPDATE/DELETE, consistent with the existing `fn_audit_guard` pattern in the SQL bundle. Capture create/edit/delete/approval/export/login/denied-access/sensitive-view events, each with actor, role, scope and correlation ID. Cryptographic hash-chain integrity is explicitly deferred to a separate, future, separately-defined requirement — do not build an undocumented crypto mechanism now.
- **Status:** Approved (append-only trigger + expanded event coverage); hash-chain integrity explicitly out of scope for now.
- **Architectural / functional impact:** `audit.AuditLog`/`AuditLogService` needs to grow from its current 5 event types to cover the full GUARD_AUDIT list, add `activeRole`, `scope`, and `correlationId` columns, and gain a DB-level append-only guard.
- **Affected documents:** D1 (GUARD_AUDIT, §2 of `docs/V4.0_SPEC_SUMMARY.md`), D4 (`fn_audit_guard` in `sql/02_rules.sql`), D8 (security controls).
- **Affected epics/stories:** Cross-cutting — every story's GUARD test (the fourth of each story's four ALLOW/DENY/SCOPE/GUARD tests) depends on audit coverage existing for that action.
- **Dependencies:** None blocking on its own, but it is a practical prerequisite for any story's GUARD test to be meaningful.
- **Implementation status:** **Partially implemented.** `audit/AuditLog.java` and `audit/AuditLogService.java` exist; `AuditEventType` currently has only `LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, ACCOUNT_CREATED, PASSWORD_CHANGED` — no `CREATE/EDIT/DELETE/APPROVAL/EXPORT/DENIED_ACCESS/SENSITIVE_VIEW` categories. No append-only DB trigger exists on the audit table yet (current schema is Hibernate auto-DDL, not the SQL bundle's `app` schema — see OD-03's note on `TenantContext`/schema status). No `correlationId`/`activeRole`/`scope` columns exist yet.
- **Outstanding actions:** Scope as its own Foundation-item brief (item 9, "Audit") once the role model (Foundation item 7) exists enough to populate `activeRole`/`scope` meaningfully — building the full event taxonomy before there's a role to log against would be premature.

---

### OD-01 — PostgreSQL major version

- **Decision title:** Which PostgreSQL major version is the project standard.
- **Product Owner decision:** Approved. PostgreSQL 16 is the official EDU 360 database major version. No other major version without Product Owner approval.
- **Status:** Approved.
- **Architectural / functional impact:** None — ratifies current state.
- **Affected documents:** D4, `docker-compose.yml`, `ci.yml`.
- **Affected epics/stories:** None directly; infrastructure-level.
- **Dependencies:** None.
- **Implementation status:** **Already implemented and verified.** Local dev runs `postgres:16-bookworm`; `ci.yml`'s `database-migration-validation` job uses `postgres:16`; the SQL bundle was re-validated live on 2026-11-04 against Postgres 16.15 (97/97 PASS). No action needed.
- **Outstanding actions:** None.

---

### OD-62 — Role-template update behaviour

- **Decision title:** Whether updates to a role template propagate automatically or manually to existing institutions already using that template.
- **Product Owner decision:** **Deferred.** Remains undecided until `ROLE_CUSTOM_MANAGE` is implemented. Do not assume automatic propagation. Do not assume manual propagation. Do not implement either behaviour as if approved.
- **Status:** Deferred.
- **Architectural / functional impact:** None yet — explicitly blocked from having any.
- **Affected documents:** D3 (Baseline Templates / Template Governance sheets).
- **Affected epics/stories:** Users & roles story 9 (`ROLE_CUSTOM_MANAGE`).
- **Dependencies:** Blocked on `ROLE_CUSTOM_MANAGE` existing at all (which itself depends on Foundation item 7, the role/action model).
- **Implementation status:** Not implemented; correctly not implemented — nothing in code currently propagates template changes in either direction.
- **Outstanding actions:** Revisit when `ROLE_CUSTOM_MANAGE` is scoped. Do not build template-update propagation logic before that revisit, in either direction.

---

### OD-134 — Individual student creation permission

- **Decision title:** Whether creating a single student needs its own action code.
- **Product Owner decision:** Approved. Use the existing `USER_CREATE` action. No separate `STUDENT_CREATE` permission.
- **Status:** Approved.
- **Architectural / functional impact:** Confirms the action-catalogue mapping for `POST /v1/tenants/{tenantId}/students` — no new action code, no new permission-matrix cell, no new role-action pairs needed.
- **Affected documents:** D2 (action register), D3 (permission matrix), D5 (OpenAPI `x-action-codes` for the `students` resource).
- **Affected epics/stories:** Users & roles story 2 (`USER_CREATE`).
- **Dependencies:** None.
- **Implementation status:** Not implemented — `USER_CREATE` itself (and the student-creation endpoint) doesn't exist in code yet; this decision only fixes which action code it will use once built.
- **Outstanding actions:** None beyond building `USER_CREATE` as scoped in the Users & roles epic brief, using this action code.

---

### SOURCE-GAP — Role and Permission Management Rules

- **Decision title:** Whether the previously-missing "Role and Permission Management Rules" source document's proposed content is approved.
- **Product Owner decision:** Approved for the next requirements revision. All 12 sections approved as listed (see `docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md` for the full incorporated text).
- **Status:** Approved for next revision — **not retroactive V3.3 functionality.** This closes the previously-open High-severity finding (§5 of `docs/V4.0_SPEC_SUMMARY.md`: "one High-severity finding is open because its own source document was never supplied") going forward, but does not change what V3.3 already specified.
- **Architectural / functional impact:** Materially shapes Foundation item 7 (the 71-role/280-action authorisation model) — role ownership hierarchy, role lifecycle states (Draft/Active/Inactive/Archived), no-hard-delete, self-elevation/self-approval prohibition, and the separation of role/permission/scope/approval-authority as distinct concepts are now binding requirements on that model's design, not just recommendations.
- **Affected documents:** D1 (new section, next revision), D3 (role/permission documentation), `docs/V4.0_SPEC_SUMMARY.md` §2 (the "one High-severity finding... source document was never supplied" note needs updating to reflect this closure).
- **Affected epics/stories:** Users & roles story 9 (`ROLE_CUSTOM_MANAGE`) directly; Foundation item 7 (role/action model) as a binding design input; indirectly every story via the GUARD_CUSTOM_ROLE / GUARD_NO_SELF_APPROVAL / GUARD_ROLE_SCOPE_STATE cross-cutting rules this document reinforces.
- **Dependencies:** None blocking adoption of the rules as documentation. Implementation is blocked on Foundation item 7 existing.
- **Implementation status:** Documentation only at this stage — **no code implements any of this yet.** See `docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md` for exactly what was approved vs. what full section text was not supplied and is therefore not recorded as content.
- **Outstanding actions:** Incorporate as a binding input when the Foundation item 7 implementation brief is written. Do not build any role-management code against this decision until that brief is presented and approved per `CLAUDE.md`'s workflow.

---

## Source of this round

These seven decisions were supplied verbatim by the Product Owner in a structured governance
message dated 2026-11-04, specifying status, required content and explicit restrictions
(including: do not invent product behaviour, do not change existing SQL security rules, do not
create new permission codes without approval, do not implement deferred OD-62 behaviour, do not
treat this documentation update as completed software implementation). This log, together with
`docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md` and
`docs/REQUIREMENTS_TRACEABILITY_AND_GAP_UPDATE.md`, is the complete record of that message's
instructions being carried out. No code was written or modified as part of this update.

---

## Decisions in this round — 2026-11-07

**Decided by:** the project owner, in the Claude Code session of 2026-11-07. These entries extend the original register and are authoritative for anything dated after it. **The platform side is implemented (see "Implementation status" below). The tenant side is not.**

| Decision | Status |
|---|---|
| TPA-01 Top post authority | Approved, including decisions A to E |
| TPA-02 Keep or hand over a permission | Approved |
| TPA-03 Editable templates | Approved |
| OD-62 Role-template update behaviour | Approved; replaces the 2026-11-04 deferral |
| TPA-04 Safety rules stay | Approved |

### TPA-01 — Top post authority

- **Decision:** The top post at each level starts with authority over almost all work in its area, then hands work to others as staff join. Product Owner runs the platform. Inside a tenant the order is Management, then Vice Chancellor, then Principal or School Principal. Management is above the Principal in schools and colleges, and above the Vice Chancellor in universities.
- **Conflicts with (flagged, not silently resolved):** D3 CR-02 and D2 action 16 (only Admins build custom roles); the D1 Management row and D2 action 276 (Management oversees rather than operates); D1 V4-19 RD-G5 and the never-roll-down list (platform roles excluded from roll-down); the exact-scope rule in `PermissionEvaluatorService`; and the register's "Never allowed" rules that name a top post.
- **Action list:** `docs/TOP_POST_AUTHORITY_RULES.md`, reviewed and approved the same day. The Addendum's source-of-truth constraint says it does not alter permission mappings, so this entry is the deliberate change to those mappings.
- **Decisions on "Never allowed" rules that name a top post:** A, allow `USER_CREATE`. B, allow `ATTENDANCE_MARK` and `MARKS_ENTER` only by stepping in with a reason. C, allow `FEE_STRUCTURE_CREATE`, with approval by someone else. D, keep individual salaries locked (`SALARY_INDIVIDUAL_VIEW`; `PAYROLL_APPROVE_TOTALS` stays totals only). E, allow `GRADUATION_CONFIRM` only by stepping in with a reason.

### TPA-02 — Keep or hand over a permission

- **Decision:** When giving a permission, the giver chooses "shared" or "only this person". Either way the giver can take it back at any time. With "only this person" the work goes to that person by default, and the top post can act only by stepping in with a recorded reason.
- **Approvals:** Stepping in covers one step of one request, never the person's own request, and never skips a step.

### TPA-03 — Editable templates

- **Decision:** Top posts can fully edit role templates, within the safety rules. The 71 standard templates stay as the starting library, and each tenant or the platform edits its own copy. Platform roles become editable as well.
- **Conflicts with (flagged):** D3 RP-01 and D10 (standard roles are read-only); the closed list of six platform roles in D3 and `ck_platform_user_platform_role`.
- **Limits that stay:** D3 CR-01 (approved actions only), CR-03 ("Never allowed" stays blocked), CR-04 (nobody gives more than they hold or changes their own roles), and CR-10 (approval power comes only from approval chains).

### OD-62 — Role-template update behaviour (replaces the 2026-11-04 deferral)

- **Decision:** Edits made while creating a role apply only to that role. The creator can separately choose to update the template, and later roles then start from the updated suggestion. Existing roles never change automatically.

### TPA-04 — Safety rules stay

- **Decision:** These remain in force over TPA-01 to TPA-03: nobody approves their own request (Addendum rules 11 and 12); platform roles see tenant data only through a tenant-granted, ticket-bound, read-only support session of up to 24 hours (D1 MX-01); and tenant and institution separation stays enforced (Addendum rule 13).
- **Data protection approvals:** Tenant export, tenant deletion and retention approvals stay with a separate Data Protection Officer (D2 action 198, D1 §7).
- **Product Owner support access:** Product Owner may use a tenant-granted support session, in addition to the Support Manager named in D2 action 213. Product Owner and Platform Admin can no longer grant that access themselves.

### Implementation status (2026-11-07)

**Platform side: implemented, not yet committed.**
- **Database:** `sql/12_v41_platform_roles.sql`, `12d_v41_platform_role_templates.sql`, `12r_v41_platform_roles_rollback.sql`, with tests DB-98 to DB-112 in `13_tests_platform_roles.sql`.
- **Backend:** `platformops/role/`, and `PlatformPermissionService` now checks custom roles, handed-over actions and step-ins.
- **Screens:** Platform roles, Role templates and Platform users, with menus built from the actions the server allows.

**Choices made while building, for the Product Owner to note:**
- **Never in a custom role:** the four Data Protection Officer actions, `PLATFORM_USER_MANAGE` and `TENANT_DELETION_CONFIRM`. The last two stay with the Product Owner so nobody can raise their own access or take both steps of a deletion.
- **Not customisable:** the Product Owner and Data Protection Officer roles. The Data Protection Officer template is locked, and that role is assigned as the standard role.
- **Limits carried into custom roles:** a Billing Manager role can't hold `DISCOUNT_APPLY`, only a Support Manager role can hold `SUPPORT_ACCESS_USE`, and only the Product Owner can change plan prices.
- **Step-ins:** last 30 minutes. A handed-over action blocks the Product Owner only while an active person holds an active role with it, so an empty role never stops the work.
- **Managing roles and templates:** uses `PLATFORM_USER_MANAGE`, because no new action code was approved.
- **Raising support tickets:** moved from `/platform/support-tickets` to the tenant path D5 defines. Any signed-in tenant user may raise one.

**New API operations not in D5 (deliberate deviation, to add at the next D5 revision):** `/platform/me/permissions`, `/platform/role-actions`, `/platform/roles` and its lifecycle and take-back operations, `/platform/role-templates` and its reset and archive operations, and `/platform/step-ins`.

**Tenant side: implemented.** Top post authority for Management, Vice Chancellor, University Admin, and the Principals (College & School), including decisions A to E, 30-minute step-in window, locked actions, and audit trail foundation (14_v41, 15_v41, 16_v41, 17_tests_tenant_top_post), is fully wired, verified with DB-113..125, and active.


---

## Decisions in this round — 2026-11-09

**Decided by:** the project owner, in the Claude Code session of 2026-11-09.

### TPA-05 — University Admin is the top post of a university tenant

- **Decision:** In a university tenant, the University Admin sits above everyone. The order is University Admin, then Management, then Vice Chancellor, then Principal (College) or School Principal. The University Admin holds top-post authority (TPA-01 to TPA-03) across the whole tenant.
- **Replaces:** the tenant order in TPA-01 for university tenants only.
- **Limits that still apply (TPA-04):**
  - No self-approval.
  - Approval chains can't be skipped.
  - Data protection approvals stay with a separate Data Protection Officer.
  - Platform staff see tenant data only through a tenant grant.
  - Tenant and institution separation stay enforced.
  - Decisions A to E apply to the University Admin as to any top post.
- **Conflicts with (flagged, not silently resolved):**
  - **D1 Management row:** names Management as the tenant top for private institutions.
  - **D2 action 276 (`TENANT_OVERSIGHT_VIEW`):** gives Management oversight.
  - **D2 action 16 note:** limits the University Admin to university-office custom roles [C4].
  - **D3 RP-06:** names Management and the Vice Chancellor for roll-down changes.
  - **`ck_role_rolldown_override_set_by_role`:** allows the University Admin only for non-approval roll-down actions.
  - These need revisiting when this decision is implemented.
- **Open:** whether the College Admin or School Admin is likewise the top post of a standalone college or school tenant. This decision covers university tenants only.
- **Implementation status:** **Implemented.** `PermissionEvaluatorService` evaluates tenant-scoped University Admin across constituent institutions under TPA-01..TPA-05, enforces TPA-04 limits (personal and locked action exclusions), respects 30-minute step-in limits, and enforces `SCHOOL_PRINCIPAL` standard role code. Fully tested by DB-113..125 and unit test suites.

### SES-01 — Users may stay signed in on several devices; one session per browser

- **Decision:** A user (tenant or platform) can be signed in on any number of devices at once. Signing in again from the same browser replaces that browser's previous session. Every user can see all their signed-in devices in "My sessions" and sign any of them out.
- **Replaces:** R2-11 / WF-35 "one phone and one computer per user", as encoded by `ux_user_device_1` on `app.user_device` (one active row per user and device class).
- **What did not change:** students and guardians may still be remembered for up to 60 days (`remembered_until`); that part of R2-11 is not affected.
- **Platform accounts:** they get the same device list (new table `platform.platform_user_device`), which the original design did not cover.
- **Known consequence:** sign-in tokens are kept per browser tab, so signing in from a second tab of the same browser signs out the first tab.
- **Implementation:**
  - `sql/18_v41_user_sessions.sql`, with rollback `18r_` and tests DB-126..137 in `19_tests_user_sessions.sql`.
  - Backend: `SessionService`, `DeviceDescriptor`, `KeycloakAdminClient.listUserSessions`, and the login flow in `AuthService`.
  - Frontend: the "My sessions" page and the `X-Browser-Id` header sent on sign-in.
- **Status:** Implemented.
