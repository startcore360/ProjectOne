# Chat A2 — Tenant onboarding, plans and entitlements

You are a senior full-stack developer (Java 21 / Spring Boot, React 19 / TypeScript, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Let the Product Owner (and the Platform Admin, within limits) onboard a tenant properly and control what it can use: retention at creation, safe provisioning with retry, settings within the plan, branding, user-limit warning, trial notice, the plan editor, and module and feature switches per plan and per tenant.

## Your stories

Your acceptance criteria are **every bullet and table cell for these story IDs in `docs/PLATFORM_COMPLETION_PLAN.md`**, word for word. That file is the contract. The notes below add detail; they never remove anything.

| ID | What | Plan section |
|---|---|---|
| E1-S1 | Create a tenant with required `retentionMonths` (1–240) and `backupRetentionDays` (7–3650) (PD-9). Write one `platform.tenant_retention_policy` row per non-system data class, as the plan lists. Reject reserved subdomains (`platform, www, api, admin, auth, keycloak, mail, static, status`) with 422 `SUBDOMAIN_RESERVED`. The temporary password is shown once (PD-10). | §3 |
| E1-S2 | Provisioning steps P01–P07 recorded in `platform.tenant_provision_step`. A failure leaves the tenant `CREATED`, compensates in reverse, and alerts Platform Admins through A1's `PlatformNotificationService`. A Keycloak failure is no longer swallowed (today `TenantManagementService` logs a warning and carries on). Add `POST /api/platform/tenants/{id}/provisioning/retry` (TENANT_CREATE), a Setup tab and a "Setup failed" badge. | §3 |
| E1-S3 | Tenant settings within the plan: values above the plan are allowed only for the Product Owner (403 `BEYOND_PLAN_PO_ONLY`). Custom domain only when the plan allows it (422 `CUSTOM_DOMAIN_NOT_IN_PLAN`). Show the plan limits beside each field. | §3 |
| E1-S4 | Branding: `PUT` and `GET /api/platform/tenants/{id}/branding` (logo PNG/SVG ≤ 512 KB through `FileStorage` with purpose `TENANT_LOGO`; colours `^#[0-9A-Fa-f]{6}$` only when the plan allows). Tenant-side `GET /api/tenants/{id}/branding`. The app shell applies the logo and colours through design-system tokens. | §3 |
| E1-S5 | User limit: `GET /api/tenants/{id}/user-limit`, a banner at ≥ 90%, 409 `USER_LIMIT_REACHED` at 100%, and `POST /api/tenants/{id}/upgrade-requests`, which creates a support ticket with category `BILLING`. | §3 |
| E1-S7 | Trial-ended read-only banner on the tenant side when the API returns 403 `TRIAL_EXPIRED`. "Trial ended" badge and days remaining on the platform Tenants list. | §3 |
| E2-S1 | Plan editor: pricing (Product Owner only; chat A1 enforces this server-side in E0-B9), limits, billing cycle, allowances, and new plan columns. | §4 |
| E2-S2 | Entitlement editor for plans and tenants (shared `EntitlementEditor` component). Seed the provisional feature catalogue and module dependencies (plan §16). Validation: 422 `MODULE_OFF`, 422 `MODULE_DEPENDENCY`; a tenant override above the plan is Product Owner only. Module enforcement merge test. | §4, §16 |

E1-S6 (Platform Admin dashboard) belongs to chat A7, not you.

## Database migrations you own

Use **only** numbers 36 and 37, each with `r` and `t` files, registered in `run_all.sh` and `provision-edu-app.ps1`.
- `36_v41_plan_commercial.sql`: `plan.custom_domain_allowed`, `plan.branding_colors_allowed`, `plan.billing_cycle_months` (1|3|6|12, default 12), `plan.sms_unit_amount numeric(14,2)`, `plan.storage_unit_amount_per_gb numeric(14,2)`.
  - `platform.tenant.branding_colors` already exists; map it on the `Tenant` entity.
  - Map `platform.tenant_provision_step` (it already exists in `01_schema.sql`). Only add columns if you truly need them.
- `37_v41_feature_catalog_provisional.sql`: `ref.feature_catalog.provisional boolean`, the seed from plan §16, and the `ref.module_dependency` seed.

Ignore the numbers in plan §15. Billing columns such as `next_renewal_at` and `pending_plan_id` belong to chat A3.

## Files you own

`TenantManagementService`, the new `TenantProvisioningService`, `PlatformFeatureToggleController`, plan DTOs, `PlansView` / `CreatePlanModal`, `TenantsView` and the tenant modals (except `TenantDataProtectionModal` and `TenantUsageModal`, which are A1's), the new `EntitlementEditor`, tenant-side branding and user-limit code.

## Depends on / others depend on you

- **You need from A1:** `PlatformNotificationService`, the audit payload API and the idempotency helper. Pull them before E1-S2. Apply the idempotency helper to your new operations (provisioning retry, branding).
- **You need from A4:** the support ticket category list (E4-S2) for E1-S5. Until it exists, use category `BILLING` and note it.
- **Others need from you:**
  - **A3** needs the new plan columns (`sms_unit_amount`, `billing_cycle_months`, `storage_unit_amount_per_gb`). Push migration 36 early.
  - **A7** needs `GET …/entitlements` and the provisioning status.

## Not yours (other chats build these — do not build them)

E0 (A1), E1-S6 and E9 (A7), E3 (A3), E4 (A4), E5 (A5), E6, E7 and E8-S1/S2/S4 (A6), E8-S3 (A4), E8-S5 (A1), E10 and E11 (A8).

---

COMMON RULES — read and follow everything below.

## You are one of 8 separate chats

Eight separate AI chats build the EDU 360 platform side at the same time, each on its own, all on the **same branch**. You never see the other chats. Build only your stories. If you need something another chat owns and it isn't on the branch yet, use a clearly marked temporary fallback and write it under "Blocked by another chat" in your report. Never build their part yourself.

## Read first (from the repository, after pulling)

1. `CLAUDE.md`, in full. It is mandatory and its workflow applies to you.
2. `docs/PLATFORM_COMPLETION_PLAN.md`: section 0 (decisions), section 1 (definition of done) and all of your stories.
3. `docs/GOVERNANCE_DECISION_LOG.md` and `docs/TOP_POST_AUTHORITY_RULES.md`.
4. `docs/V4.0_SPEC_SUMMARY.md`. Use its navigation table to open the right part of `docs/EDU360_V4.0_Final/` (D1–D10, SQL, OpenAPI) for each story.
5. `graphify-out/GRAPH_REPORT.md`, to find code quickly.

## Decisions you must follow

- **TPA-01:** the Product Owner runs the platform and holds every platform action, then hands work to staff. Every screen must work for the Product Owner alone. Other platform roles see the same screens filtered by `GET /api/platform/me/permissions`.
- **TPA-02 / TPA-03:** hand-over ("shared" / "only this person"), stepping in with a reason for 30 minutes, and editable templates are already built. Don't change them.
- **TPA-04:**
  - the four data-protection approvals (`TENANT_EXPORT_APPROVE`, `TENANT_DELETION_APPROVE`, `RETENTION_CHANGE_APPROVE`, `APPROVAL_DELEGATE`) belong only to a separate Data Protection Officer; the Product Owner can request and confirm, never approve;
  - platform staff see tenant data only through a tenant-granted, ticket-bound, read-only session of up to 24 hours;
  - nobody approves their own request;
  - tenant and institution separation stays enforced.
- **PD-7:** deletion approval locks the tenant now; the purge happens later.
- **L-01:** `DELETE /platform/tenants/{id}` is gone; deletion is only through the three-step chain.
- **PD-8:** all tenants are SHARED; `ISOLATED` returns 422.
- **PD-9:** the create-tenant form requires `retentionMonths` and `backupRetentionDays`, with no default.
- **PD-10:** the first tenant admin gets a temporary password shown once, never stored or returned again, with a forced change at first login.
- **PD-11:** the entitlement editor uses the 29 modules plus the provisional feature list in plan §16.
- **Plan §0.3** resolutions are binding.

## Facts about the code

- **Backend** in `api/` (Spring Boot 4, Java 21).
  - Platform code is in `api/src/main/java/com/edu360/api/platformops/` and runs on a separate `edu_platform` pool (`config/PlatformJpaConfig`).
  - Authorisation is imperative: `PlatformPermissionService.authorize(principal, "ACTION")` for platform actions, `PermissionEvaluatorService.authorize(...)` for tenant actions. Never use `@PreAuthorize`.
  - Every platform change writes an audit row through `PlatformAuditWriter` (required, never optional). Errors go through `GlobalExceptionHandler`.
  - Temporary passwords come only from `auth/TemporaryPasswordGenerator`.
- **Frontend** in `app/` (React 19 + TypeScript + Vite).
  - Platform screens are in `app/src/features/platform/`. All calls use the shared axios instance.
  - Use design-system tokens and components only (`app/src/design-system/`), with no hard-coded colours or fonts.
- **Database:** `sql/` is the only schema authority, and Hibernate never creates tables. Each migration is `NN_*.sql` with `NNr_` rollback and `NNt_` tests, registered in `sql/run_all.sh` and `docker/postgres/provision-edu-app.ps1`. The highest existing number is 33.
- **Commands:**
  - local stack: `.\dev-up.ps1`
  - backend: `.\mvnw.cmd clean verify` in `api/`
  - frontend: `npm run lint`, `npm test` and `npm run build` in `app/`
  - SQL: `OUT=<temp file> ./sql/run_all.sh`
  - `*IT` tests: run by name while the stack is up
- **Logins:** the platform login needs two-step (TOTP) login. Use the dev Product Owner `owner@edu360-platform.local` with its authenticator, or ask the user for a code.

## Git rules

- Use only branch `sprint-2-development`. Never create or switch to another branch.
- **Before starting:**
  1. `git fetch origin`
  2. `git checkout sprint-2-development`
  3. `git pull --rebase origin sprint-2-development`
- **Before every push:**
  1. Run the checks for every layer you touched.
  2. `git pull --rebase origin sprint-2-development`.
  3. Resolve conflicts while keeping the other chats' work. Never delete or rewrite their code to make a conflict go away.
  4. Run the checks again.
  5. `git push origin sprint-2-development`.
- **Never:** force-push, reset the remote, rebase pushed commits, or skip hooks with `--no-verify`.
- **Commits:** one small commit per story or sub-story, message `<story-id>: <what and why>`.
  - Stage files by name.
  - Never commit secrets, `.env*` files or `scratch/`.
- **Shared files you don't own:**
  - Only **append** your own separate entry: a route, a menu item, an action code, an `AuditEventType` value, an exception mapping, or a line in `run_all.sh` / `provision-edu-app.ps1`.
  - Pull just before editing and push soon after. Don't reorder or reformat.
- **Migrations:** use only your reserved numbers. Never edit another chat's migration or any existing one.
- **Read-only files:** don't edit `docs/EDU360_V4.0_Final/`, `docs/PLATFORM_COMPLETION_PLAN.md`, `docs/GOVERNANCE_DECISION_LOG.md` or `CLAUDE.md`. If something in them looks wrong, report it.
- If a pull brings in another chat's change that breaks your build, don't fix their code. Report it under "Blocked by another chat".

## How you work: understand first, then build

1. **Pull, then read** the files in "Read first".
2. **Understand the code:** open every file your stories touch, and write down what exists today (endpoints, services, entities, tables, DTOs, screens, tests). Never assume.
3. **Check the documents:** for each story, open the D1–D10 / SQL / OpenAPI sections it cites. Confirm the rule, action code, table and every screen state (loading, empty, validation error, server error, permission denied, read-only, success).
4. **Write the implementation brief** with the 9 headings from `CLAUDE.md`, including a Mermaid diagram and a wireframe for each new screen. **Stop and wait for the user's approval** before changing any file.
5. **Build** story by story. If you find something that changes the plan, stop and tell the user first.
6. **After each story:**
   - write the tests: ALLOW / DENY / SCOPE / GUARD for every new endpoint, vitest for every screen state, and `*IT` against the real stack where RLS or Keycloak is involved;
   - run them;
   - do the UI check from `CLAUDE.md`: both themes, short laptop heights, tokens only, and a grep for hard-coded hex colours;
   - check as the Product Owner and as one restricted platform role.
7. **Push** following the Git rules.

## Definition of done

- Every acceptance criterion for your stories is shown working, with evidence.
- Backend `verify`, frontend lint, test and build pass, and the SQL bundle passes if you touched `sql/`.
- Your `*IT` tests pass against the local stack.
- The UI checks are done.
- Everything is pushed to `sprint-2-development` without conflicts.

## Final report (give this to the user at the end)

1. Stories done, with the files changed for each.
2. Every acceptance criterion with pass / fail and its evidence: real test output or a real API call. Never assumed.
3. Any deviation, and why.
4. Blocked by another chat.
5. Open questions.

## Questions to raise, never guess

Open decisions:
- OD-06 retention durations
- OD-15 ticket categories
- OD-39 idempotency-key retention
- OD-42 / OD-70 / OD-73 usage and billing units
- OD-43 feature list
- OD-63 backup thresholds
- OD-77 rate limits
- OD-79 support-grant scope
- OD-130 who requests retention changes
- OD-137 incident action
- custom-domain ownership proof
- the real production domain

Use the provisional behaviour the plan already fixes, and list anything else as a question. Never invent a business rule.
