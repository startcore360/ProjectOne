# EDU 360 — Platform Completion Plan (user stories and acceptance criteria)

**Status:** approved scope for building the platform side to 100% — screens, backend, database, tests and production readiness.
**Written:** 2026-11-16, from a line-by-line comparison of the code on `manoj_feature` against D1 §7.13/§8.20/§10.2/§11/§26.24, D2 (Data, Open Decisions), D3 (Role Catalogue, Scoped Letters), D4 (platform schema, §2.4), D5 (46 `x-kind: PLATFORM` operations), D6 (E2E-001/033/035, ACT-* tests), D7 (flows 02, 12, 14, 15, 18), D8 (Security Controls), `GOVERNANCE_DECISION_LOG.md` (TPA-01..05, SES-01), `TOP_POST_AUTHORITY_RULES.md` and `docker/PRODUCTION_TENANT_ROUTING.txt`.
**Authority order (D1 V4-2.1):** Product Owner decisions → D1 Part II with D2/D3 → D4/D5 → D7/D9/D10. Where this plan deviates from a deliverable, the deviation is named and the decision it rests on is cited.
**How to use:** build epics in order E0 → E11. Every story has a backend, frontend, database and test section; a story is *done* only when all four pass and the acceptance criteria are demonstrated on the local stack. Follow `CLAUDE.md` (brief → approval → build → validate) for every story.

---

## 0. Decisions this plan rests on

### 0.1 Already recorded (decision log)

| Decision | Effect on this plan |
|---|---|
| **TPA-01** Product Owner runs the platform and holds every platform action; hands work to others later | Every story is written so the PO can do it alone; other platform roles get the same screens filtered by their held actions. |
| **TPA-02** Shared / only-this-person hand-over; PO acts on handed-over work only by stepping in with a reason (30 min) | Already built (`PlatformPermissionService`, `PlatformStepInService`). No story changes it. |
| **TPA-03** Editable templates; PO and DPO roles not customisable | Already built. |
| **TPA-04** Safety rules stay: the four data-protection approvals stay with a **separate** Data Protection Officer; platform staff see tenant data only through a tenant-granted, ticket-bound, read-only session ≤ 24 h | The DPO is a second person. The PO can *request* exports/deletions/retention changes but never *approve* them. Stories E5 and E4-S1 enforce this. |
| **SES-01** many devices, one session per browser | Already built for platform users. |
| **PD-7** (code comment, `PlatformDataProtectionService`) "mark now, purge later" | Deletion approval locks the tenant immediately; purge runs after `retention_ends_at`. Kept. |
| **L-01** `DELETE /platform/tenants/{id}` removed | Deletion only through the three-step chain. The frontend is fixed to match (E0-B2). |

### 0.2 Decided on 2026-11-16 (project owner, this session) — add to `GOVERNANCE_DECISION_LOG.md`

| ID | Decision | Replaces / conflicts with |
|---|---|---|
| **PD-8 Isolation** | All tenants are created **SHARED** (one database, RLS) in this plan. `ISOLATED` provisioning (database per tenant, Secrets Manager, migrations) is a later epic (E10-S9, optional). The create-tenant API rejects `ISOLATED` with 422 until then; the form does not offer it. | D2 row 202 / TE-01 "own database by default" — recorded deviation. |
| **PD-9 Retention at creation** | The create-tenant form **requires** `retentionMonths` (applies to every `ref.data_class` that is not a system class) and `backupRetentionDays`. No system default. Later changes go through the DPO-approved retention-change request. | OD-06 stays open (durations need counsel); this only fixes *who enters* the number. |
| **PD-10 First admin** | The first tenant admin gets a **temporary password shown once** at creation, never stored or returned again, forced change on first login. Invitation e-mails are not built. | D7 F02_T04 "invite first Admin"; D5 responses carry no password — recorded deviation until an e-mail service exists. |
| **PD-11 Feature catalogue** | The entitlement editor offers **modules (29, from `ref.module_catalog`) and a provisional feature list per module** (section 6). The list is seeded into `ref.feature_catalog` and marked `provisional = true`; OD-43 closes it. | OD-43 open. |

### 0.3 Resolved by this plan without a new decision (follows the documents)

- Discounts are entered as a **percentage** (D1 §11.1 step 14) and stored as the resulting amount (`platform.invoice.discount_amount`).
- Invoices gain the **APPROVED** step (`ck_invoice_status` already allows it): Billing Manager drafts → Product Owner approves → Billing Manager sends.
- Billing cycle generates invoices from the **plan price** plus **usage lines** (`invoice_line` SMS/STORAGE) where `tenant_usage_monthly` has rows; tenants without usage rows get a plan line only (OD-73 open; nothing is estimated).
- Support ticket categories (OD-15 open): a fixed provisional list `LOGIN, ACCESS, DATA, BILLING, PERFORMANCE, BUG, OTHER`, stored in `ref.setting_definition` so OD-15 can replace it without code change.
- Security incidents (OD-137 open): keep the existing create/update endpoints; the recording action stays `PLATFORM_AUDIT_VIEW` / `SECURITY_POLICY_SET` as today.
- Who requests a retention change (OD-130 open): `TENANT_SETTINGS` holders (PO, PA) on the platform side, as today.
- Idle timeout per role class (D1 §11.0): policy gains `idleTimeoutMinutesPrivileged` (default 15) and `idleTimeoutMinutesStandard` (default 60); platform roles use the privileged value.

### 0.4 Still open for the Product Owner (not blocking the plan)

OD-06 durations · OD-42/OD-70/OD-73 usage semantics and billable units · OD-63 backup thresholds · OD-11/OD-60 relocation and migration tool · OD-79 grant scope by module · OD-39 idempotency-key retention · OD-77 rate limits · P9 custom-domain ownership proof · the real production domain name.

---

## 1. Definition of "platform 100%"

The platform is complete when all of the following are true on a staging environment that matches production:

1. Every one of the 46 D5 platform operations (plus the approved TPA additions) exists, is authorised by the correct action code, and is exercised by a screen.
2. Every D1 §11.1–11.6 workflow step that is in v1 can be performed from the UI by the role named, and by the Product Owner (TPA-01), with the TPA-04 exceptions enforced.
3. Every `platform.*` table that Phase 1 needs is written by the application; no screen shows a figure that nothing produces (L-06 stays: unmeasured = null + note).
4. Every mutating platform call writes a `platform.audit_outbox` row; every refusal writes a DENIED row.
5. The 84 ACT-* platform action tests, DB-98..112, E2E-001, E2E-033 and E2E-035 pass against a real Postgres and Keycloak.
6. `docker/PRODUCTION_TENANT_ROUTING.txt` P1–P7 and P10–P12 are done and its "Done when" checklist passes on staging.

---

## 2. Epic E0 — Repairs (no new decisions, do first)

Goal: nothing in the console lies. All items are small; ship as one PR.

| ID | Story | Acceptance criteria | Evidence of defect |
|---|---|---|---|
| **E0-B1** | As any platform role I open the console and see a dashboard that loads. | Given a Billing/Support/Release/DPO user, when `/platform` loads, then no 403 is raised, no DENIED audit row is written, and the role's own dashboard (E9) renders. Overview requests only data the caller's `/me/permissions` allows. | `PlatformOverviewView.tsx:19-34` calls PO-only endpoints |
| **E0-B2** | As the PO I request tenant deletion from the Tenants page through the three-step chain. | Given a tenant, when I click *Request deletion* and type its name, then `POST /platform/deletion-requests` is called, the response status is `REQUESTED`, and the modal shows the chain (Requested → PO confirm → DPO decision). `DELETE /platform/tenants/{id}` is removed from `platformTenantService.ts`. | `platformTenantService.ts:174` |
| **E0-B3** | As the PO I request a retention change. | Calls `POST /platform/tenants/{id}/retention-change-requests`; shows `REQUESTED`; data-class dropdown uses the 14 `ref.data_class` codes (not `STUDENT_RECORDS`). | `platformTenantService.ts:178`; wrong codes in modal |
| **E0-B4/B6** | Export and retention tables show real data. | DTOs match the backend (`exportRequestId`, `status`, `createdAt`, `fileRef`, `downloadExpiresAt`; `dataClassCode`, `retentionMonths`, `effectiveFrom`). With one export row the table renders without a runtime error. | `TenantDataProtectionModal.tsx:215-235, 326-337` |
| **E0-B5** | Usage modal shows `platform.tenant_usage_monthly` rows. | Columns: month, metric code, quantity, computed at, final. Renders with zero rows (empty state) and with rows. | `TenantUsageModal.tsx:68-124` |
| **E0-B7** | Creating a tenant never returns 500. | `isolationMode = ISOLATED` → 422 `ISOLATION_NOT_AVAILABLE` (PD-8). Default is `SHARED`. | `TenantCreateRequest.java:49`, `ck_tenant_r2` |
| **E0-B8** | Suspend/archive stamp their timestamps. | `suspended_at` set on SUSPENDED, `archived_at` on ARCHIVED, cleared on reactivation; visible in the tenant drawer. | `TenantManagementService.java:645, 664` |
| **E0-B9** | Platform Admin cannot change prices (F-4). | Given a PLATFORM_ADMIN, when `PUT /platform/plans/{id}` carries `priceAmount`, `currencyCode` or `pricingType` different from the stored values, then 403 `PRICE_CHANGE_PO_ONLY` and a DENIED audit row. Same rule for custom roles based on Platform Admin. Plan form hides price fields for non-PO. | `TenantManagementService.java:600-637` |
| **E0-B12** | Entitlements can be read. | `GET /platform/plans/{id}/entitlements` and `GET /platform/tenants/{id}/entitlements` return `{targetId, targetType, features:{"MODULE:FEATURE":bool}}` plus the effective merge for a tenant (`plan`, `override`, `effective`). | PUT-only controller |
| **E0-B13** | DPO export decision exists. | `POST /platform/export-requests/{id}/decision` {decision, remark} — DPO only; requester never decides; REJECTED needs remark (`ck_export_request_r1`); APPROVED moves to `APPROVED` and enqueues the export job (E5-S3). `GET /platform/export-requests` lists all, filter by `status`, `tenantId`. | no endpoint; `TENANT_EXPORT_APPROVE` dead |
| **E0-B14** | Tenant-level `BACKUP_STATUS_VIEW` stays honest; platform screens show "No backup system connected" until E6-S3. | Already true; add a test. | — |

**Tests:** vitest for each modal with mocked API shapes copied from the Java DTOs; `PlatformTenantControllerTest` (ISOLATED → 422, PA price change → 403); `PlatformDataProtectionControllerTest` (export decision ALLOW/DENY/SCOPE/GUARD = ACT-TENANT_EXPORT_APPROVE-*).

---

## 3. Epic E1 — Tenant onboarding and lifecycle

Sources: D1 §7.13, §11.1 steps 7–12, 25–26; §11.2 steps 3–4; D2 rows 202–205; D4 §2.4; D7 flow 02; D5 `createTenant`, `activateTenant`, `suspendTenant`, `archiveTenant`, `updateTenantSettings`.

### E1-S1 Create tenant with retention and backup retention (PD-9, CL-08, WF-02)
*As the Product Owner or Platform Admin I create a tenant so that it starts in Trial with its retention set.*

- **Backend:** `TenantCreateRequest` gains `retentionMonths` (1–240, required) and `backupRetentionDays` (7–3650, required). On create, one `platform.tenant_retention_policy` row per non-system `ref.data_class` (`STRUCTURE, PERSON, ACADEMIC_RECORD, FINANCIAL, COMMUNICATION, STUDENT_NOTE, GOVERNANCE, APPROVAL, BY_OWNER, TENANT_LIFETIME`) with `status = ACTIVE`, `effective_from = now`; `AUDIT` and `BACKUP` classes use `backupRetentionDays/30` months rounded up. Audit event `TENANT_CREATED` already written.
- **Frontend:** `CreateTenantModal` adds the two fields with helper text "Set at creation [CL-08]; later changes need the Data Protection Officer".
- **AC:**
  - Given valid data, when I submit, then the tenant is `TRIAL`, `trialEndsAt = now + 30 d`, 12 retention rows exist, response shows the temporary password once (PD-10) and the login address.
  - Given a missing `retentionMonths`, then 400 with `fieldErrors.retentionMonths`.
  - Given a reserved subdomain (`platform, www, api, admin, auth, keycloak, mail, static, status`), then 422 `SUBDOMAIN_RESERVED` (P6).
  - Given a subdomain with a dot or 1 character, then 400 (`ck_tenant_subdomain`).
- **Tests:** ACT-TENANT_CREATE-ALLOW/DENY/SCOPE/GUARD; retention rows asserted in a `*IT` against Postgres.

### E1-S2 Provisioning steps and compensation (TE-19, D4 §2.4)
*As the Platform Admin I can see why a tenant setup failed and retry it safely.*

- **DB:** entity for `platform.tenant_provision_step` (step_code, status PENDING/DONE/FAILED, attempt, error, started_at, finished_at).
- **Backend:** `TenantProvisioningService` runs ordered idempotent steps: `P01 REGISTRY` (tenant row + `app.tenant_local`), `P02 RETENTION`, `P03 ENTITLEMENTS` (copy plan entitlements to `tenant_entitlement`), `P04 KEYCLOAK_REALM`, `P05 REALM_POLICY`, `P06 FIRST_ADMIN`, `P07 STANDARD_ROLES`. A failed step marks the tenant `CREATED` (not TRIAL), records the error, and compensates completed steps in reverse (delete realm, delete admin). A Keycloak failure is no longer swallowed. `POST /platform/tenants/{id}/provisioning/retry` (TENANT_CREATE) re-runs from the first non-DONE step.
- **Frontend:** tenant drawer shows a *Setup* tab with the step list and a *Retry setup* button; Tenants list badge "Setup failed".
- **AC:** Given Keycloak is down, when I create a tenant, then the response is 503 `KEYCLOAK_UNAVAILABLE`, the tenant exists in state `CREATED` with step P04 `FAILED`, no admin account exists, and a `PLATFORM_ALERT` notification (E9-S5) is created for Platform Admins. When Keycloak is back and I click Retry, then steps P04–P07 complete and the tenant becomes `TRIAL`.
- **Tests:** unit with a failing `KeycloakAdminClient` stub; `*IT` for compensation.

### E1-S3 Tenant settings within plan (WF-06, R2-14)
*As the Platform Admin I change a tenant's limits within its plan; beyond the plan needs the Product Owner.*

- **Backend:** `updateTenantSettings` compares each limit with the plan: a value above the plan's limit is allowed only for `PRODUCT_OWNER`; otherwise 403 `BEYOND_PLAN_PO_ONLY`. Custom domain allowed only when `plan.customDomainAllowed = true` (new plan column, E2-S1), else 422 `CUSTOM_DOMAIN_NOT_IN_PLAN`.
- **Frontend:** `TenantSettingsModal` shows plan limit beside each field; fields above plan are disabled for non-PO with the tooltip "Beyond the plan — Product Owner only".
- **AC:** Given plan user limit 1000 and a Platform Admin, when I set 1500, then 403; when the PO sets 1500, then saved and audited `TENANT_SETTINGS_UPDATED` with old/new values.

### E1-S4 Branding (R2-14, D2 row 205)
*As the Product Owner I upload a tenant's logo and, on higher plans, set colours and a custom domain.*

- **DB:** map `branding_colors jsonb` on `Tenant`; add `plan.branding_colors_allowed boolean`, `plan.custom_domain_allowed boolean` (E2-S1 migration).
- **Backend:** `PUT /platform/tenants/{id}/branding` (TENANT_SETTINGS): multipart logo (PNG/SVG ≤ 512 KB, stored through `FileStorage` with purpose `TENANT_LOGO`, id into `branding_logo_file_ref`) and `{primary, accent}` hex colours validated `^#[0-9A-Fa-f]{6}$`. `GET /platform/tenants/{id}/branding` returns the logo URL and colours. Tenant side: `GET /api/tenants/{id}/branding` (public to signed-in users) so the app shell can apply them.
- **Frontend:** *Branding* tab in the tenant drawer; colour pickers disabled when the plan does not allow them. App shell reads the branding and applies logo + colours through existing design-system tokens.
- **AC:** Given a plan without colours, when I set colours, then 422; logo upload works on every plan; a tenant user sees the logo after next load.

### E1-S5 User-limit warning and upgrade request (WF-01)
*As a tenant Admin I am warned at 90% of the user limit and can ask for an upgrade.*

- **Backend:** `UserAccountService` already blocks at 100%; add `GET /api/tenants/{id}/user-limit` → `{limit, active, percent}` and `POST /api/tenants/{id}/upgrade-requests` (creates a support ticket with category `BILLING`, subject "Upgrade request"). Platform side: ticket appears in Tickets with a *Billing* filter.
- **Frontend:** Users page banner at ≥ 90% with *Request upgrade*; platform Tickets view shows upgrade requests.
- **AC:** Given 900/1000 active users, then banner visible; at 1000, user creation returns 409 `USER_LIMIT_REACHED` and the banner says so.

### E1-S6 Platform Admin tenant dashboard (§11.2 step 2) — see E9-S2.

### E1-S7 Trial expiry notice (R2-24)
*As a tenant user whose trial has ended I see why the app is read-only.*
- Backend already returns 403 `TRIAL_EXPIRED`; frontend shows a persistent read-only banner "Trial ended on {date}. Contact your administrator." on that error. Platform Tenants list shows "Trial ended" badge and days remaining.

---

## 4. Epic E2 — Plans and entitlements

Sources: D1 §11.1 steps 3–6, 11; D2 rows 201, 206; D5 `createPlan`, `updatePlan`, `setPlanEntitlements`, `setTenantEntitlements`; PD-11.

### E2-S1 Plan editor (MK-03, MK-04, WF-07)
*As the Product Owner I create a plan with pricing, limits and ticked modules/features; the Platform Admin edits everything except prices.*

- **DB:** migration `34_v41_plan_commercial.sql`: `plan.custom_domain_allowed`, `plan.branding_colors_allowed`, `plan.billing_cycle_months` (1|3|6|12, default 12), `plan.sms_unit_amount numeric(14,2)`, `plan.storage_unit_amount_per_gb numeric(14,2)`. Rollback file + tests.
- **Backend:** `CreatePlanRequest`/`UpdatePlanRequest` carry the new fields; price fields PO-only (E0-B9).
- **Frontend:** `PlansView` → *Create/Edit plan* modal with sections Pricing (PO only), Limits, Billing cycle, Allowances, and the **Entitlements** section (E2-S2) inline.
- **AC:** Given a Platform Admin, then the Pricing section is read-only; saving limits works. Given the PO, all fields editable; audit `PLAN_UPDATED` with changed fields.

### E2-S2 Entitlement editor — plan and tenant (D1 §11.1 steps 5–6, 11; PD-11)
*As the Product Owner or Platform Admin I switch modules and features on or off per plan and per tenant.*

- **DB:** seed `ref.feature_catalog` from section 6 (migration `35_v41_feature_catalog_provisional.sql`, `provisional = true` column added to `ref.feature_catalog`). `ref.module_dependency` seeded with the hard dependencies in section 6.
- **Backend:** `GET …/entitlements` (E0-B12) returns modules grouped with their features and the effective value. `PUT` validates: a feature cannot be enabled when its module is off (422 `MODULE_OFF`); a module cannot be turned off while a dependent module is on (422 `MODULE_DEPENDENCY`, names the dependant); Phase 2/3 modules can be stored but are flagged `phase` in the response. Tenant override cannot enable what the plan disables unless the caller is PO (WF-06 pattern).
- **Frontend:** `EntitlementEditor` component (used in the plan modal and in the tenant drawer *Features* tab): accordion per module with a master switch and feature switches; badges Phase 2/3; "From plan" vs "Override" indicator on the tenant view; disabled switches explain why.
- **AC:**
  - Given module `FEES` off, when I enable `FEES:ONLINE_PAYMENT`, then 422 and the switch snaps back with the reason.
  - Given `RESULTS` on, when I turn `EXAMINATIONS` off, then 422 `MODULE_DEPENDENCY: RESULTS`.
  - Given a tenant whose plan has `LIBRARY` off, when a Platform Admin enables it as override, then 403 `BEYOND_PLAN_PO_ONLY`; the PO can.
  - `ModuleEntitlementEnforcer` denies tenant calls to a module that is off (already exists) — covered by `RegI07ModuleEntitlementTest` plus a new test for the override merge.

---

## 5. Epic E3 — Billing

Sources: D1 §11.1 steps 13–14, 18; §11.3; D7 flow 14; D2 rows 207–209; D4 `invoice`, `invoice_line`, `tenant_usage_monthly`.

### E3-S1 Run billing cycle (MK-06, MK-10; F14_B01)
*As the Billing Manager I generate draft invoices for all tenants due this cycle.*

- **DB:** entity for `platform.invoice_line`. Migration `36_v41_invoice_approval.sql`: `invoice.approved_by uuid`, `invoice.approved_at`, `invoice.due_at date`, `invoice.next_renewal_at date` on `tenant` (or derive from `activated_at + billing_cycle_months`).
- **Backend:** `POST /platform/billing-cycles` (INVOICE_MANAGE, Idempotency-Key required) with `{periodStart, periodEnd}`: for every ACTIVE tenant whose next renewal falls in the period and has no invoice for it, create a DRAFT invoice with lines: `PLAN` (plan price × cycle), `SMS` (quantity from `tenant_usage_monthly` metric `SMS_SENT` × `sms_unit_amount`) and `STORAGE` (GB over the plan limit × unit) when usage rows exist. `subtotal = Σ lines` (trigger-safe). Returns `{created, skipped[]}` with reasons. Trial tenants: one `SETUP`/first invoice at activation request (E3-S5).
- **Frontend:** Invoices page → *Run billing cycle* dialog (period picker, preview count, confirm) → result list.
- **AC:** Given two ACTIVE tenants due and one already invoiced, when I run the cycle, then two DRAFT invoices with correct lines exist, the third is in `skipped` with `ALREADY_INVOICED`; running again with the same Idempotency-Key returns the same result and creates nothing.

### E3-S2 Invoice lines and review (D4 `invoice_line`)
- Invoice drawer lists lines; lines editable (add/remove `OTHER`) only while DRAFT (trigger enforces); subtotal recomputed server-side.
- **AC:** editing a line on a SENT invoice → 409 `INVOICE_NOT_DRAFT`.

### E3-S3 Product Owner approval step (§11.1 step 13)
- `POST /platform/invoices/{id}/approval` (INVOICE_MANAGE, PO only; Billing Manager gets 403) DRAFT→APPROVED; `sendInvoice` requires APPROVED (409 otherwise). Discounts (E3-S4) allowed in DRAFT or APPROVED, never after SENT.
- **AC:** Given a DRAFT invoice, when the Billing Manager clicks Send, then 409 `APPROVAL_REQUIRED`; after PO approval, Send works and `sent_at` is set.

### E3-S4 Percentage discount (§11.1 step 14, MD-34)
- `ApplyDiscountRequest {discountPercent 0.01–100, reason}`; server computes amount, stores reason in audit payload. UI enters %. Billing Manager still denied (existing).
- **AC:** 10% on 50,000 → `discount_amount 5000`, `total 45000`; 101% → 400.

### E3-S5 Payment, overdue and reminders (§11.3 steps 5–6)
- `recordInvoicePayment` requires `paymentMode` and reference (exists). Add `due_at = sent_at + 15 d` (configurable in security/billing settings, E8-S1). Scheduled job marks `overdue = due_at < now and status SENT` (derived, no new state) and sends a reminder notification to the tenant's admins (in-app via existing notifier) and lists them under *Overdue*. `flagOverdue` stays manual (Billing Manager flags for PO suspension review); the Tenants page shows "Overdue — review" with a *Suspend* shortcut for the PO.
- **AC:** Given an invoice sent 16 days ago, when the daily job runs, then it appears in Overdue, the tenant admin has a notification, and nothing is flagged until the Billing Manager clicks Flag.

### E3-S6 Upgrade / downgrade (MK-08)
- `POST /platform/tenants/{id}/plan-change` (TENANT_SETTINGS, PO or PA within plan rules) `{planId, effective: NOW|RENEWAL}`: upgrade now creates a pro-rata DRAFT invoice line `PLAN` for the remaining days; downgrade is stored as `pending_plan_id` and applied by the renewal job.
- **AC:** Given 100 days left of a 365-day cycle, upgrade from 10,000 to 20,000 → line amount `round((20000-10000)×100/365,2)`.

### E3-S7 Revenue analytics (MK-23, §11.1 step 18)
- `GET /platform/reports/REVENUE` (PLATFORM_REPORTS_VIEW / USAGE_COUNTS_VIEW) returns monthly revenue (paid invoices by month, 12 months), churn (tenants ARCHIVED/DELETED per month), renewals due in 60 days. Renewal reminder notification to Billing Manager and the tenant admin at 60 and 30 days. Reports page gets a *Revenue* tab with a bar chart (design-system chart component).
- **AC:** figures reconcile with the invoices table for the same period; months with no data show 0, not null (they are measured).

---

## 6. Epic E4 — Support and the support session

Sources: D1 §11.1 steps 15–16; §11.4; D7 flow 15; D2 rows 210–213; D6 E2E-033; TPA-04; D4 `support_ticket`, `support_access_grant`.

### E4-S1 Read-only support session (WF-11, GUARD_PLATFORM_ISOLATION) — *the design, since `docs/SUPPORT_SESSION_DESIGN.md` does not exist*

*As the Support Manager (or the Product Owner, TPA-04) I open a tenant's data read-only, for one ticket, for up to 24 hours, with every view logged, and I lose access the moment the tenant withdraws it.*

- **Mechanism:**
  1. `POST /platform/support-sessions {ticketId}` (SUPPORT_ACCESS_USE) checks the ticket and an active grant (exists), then mints a **support session token**: a signed JWT issued by the API (not Keycloak), `typ = "support"`, claims `sub = platformUserId`, `tenant_id`, `ticket_id`, `grant_id`, `institution_id?`, `scope = "READ"`, `exp = min(grant.expires_at, now + 1 h)`. Stored in `platform.support_session` (new table: id, grant_id, platform_user_id, issued_at, expires_at, ended_at, end_reason). Returns `{token, expiresAt, tenantLoginAddress}`.
  2. A new `SupportSessionAuthenticationFilter` accepts `Authorization: Bearer <support token>` on tenant paths only. It validates signature/expiry, re-checks the grant row on **every request** (`withdrawn_at IS NULL AND expires_at > now`), sets `TenantContext` (tenant, `institution_scope` = grant institution or `ALL`), and a `SupportPrincipal`.
  3. `SupportPrincipal` is accepted only by `GET`/`HEAD` on paths whose D5 operation has `x-support-readable: true` (allow-list generated into `ref.action_catalog.support_readable`; seeded from the yaml). Everything else → 403 `SUPPORT_READ_ONLY`. Any write method → 403 even before authorisation.
  4. Every request under a support session writes a tenant audit row `SUPPORT_VIEW` (`AuditEventType`) with ticket, grant, path, and a `platform.audit_outbox` row `SUPPORT_SESSION_VIEW` — "every view logged".
  5. `DELETE /platform/support-sessions/current` ends it; tenant withdrawal (`…/withdrawal`) ends every open session for that grant (sets `ended_at`, reason `WITHDRAWN`); expiry ends it (`EXPIRED`).
- **Frontend:** Tickets/Support Access view → *Open read-only session* → the app opens the tenant address in a new tab with the support token in `sessionStorage` under a separate key; the shell shows a red top banner "Support session — read-only — ticket SUP-… — ends at …" with *End session*; all mutating controls are hidden by a `useSupportSession()` hook; API errors 403 `SUPPORT_READ_ONLY` show a toast. Tenant side: the existing Support page lists active sessions on its grants with *Withdraw*.
- **AC (E2E-033):**
  - Given an active grant, when the Support Manager opens a session and reads the users list, then 200 and one `SUPPORT_VIEW` audit row per request.
  - When they `PATCH` a user, then 403 `SUPPORT_READ_ONLY`, a DENIED audit row, nothing changed.
  - When the tenant Admin withdraws the grant, then the next request returns 401 `SUPPORT_SESSION_ENDED` within one request.
  - When the grant expires (24 h), the same.
  - Given no grant, `POST /platform/support-sessions` → 403 `SUPPORT_GRANT_REQUIRED` (OD-79 code names).
  - A platform user token (not a support token) on a tenant path still fails as today; a support token on a platform path fails.
- **Tests:** `SupportSessionIT` against the local stack (real RLS); unit tests for the filter; vitest for the banner and hidden controls.

### E4-S2 Triage (§11.4 step 2; OD-15 provisional list)
- `UpdateSupportTicketRequest` gains `priority`, `category`; status `TRIAGED` set when both are present and status was `NEW`. Category values from `ref.setting_definition` key `SUPPORT_TICKET_CATEGORIES`.
- **UI:** ticket drawer with priority/category selects; queue sorted by priority then age.
- **AC:** NEW + set priority/category → TRIAGED; invalid category → 400.

### E4-S3 Escalate (§11.1 step 16, §11.4 step 6)
- `POST /platform/support-tickets/{id}/escalation {to: ENGINEERING|RELEASE_MANAGER, note}` (SUPPORT_TICKET_MANAGE): status `ESCALATED`, `escalated_at`, notification to Release Managers when `to = RELEASE_MANAGER` and to the PO always (PO handles escalations, D2 row 211).
- **AC:** escalating a CLOSED ticket → 409; PO sees the ticket under *Escalations* on their dashboard.

### E4-S4 Notify requester on resolve/close (F15_SU09)
- On RESOLVED/CLOSED, write a tenant in-app notification to `raised_by_user_id` using the existing notifier; include the resolution note.
- **AC:** requester's bell shows "Ticket SUP-… resolved".

### E4-S5 Support dashboard — E9-S4.

---

## 7. Epic E5 — Data protection (Data Protection Officer)

Sources: D1 §7.13, §11.6; D2 rows 194–200; D5 export/deletion/retention ops; D4 `export_request`, `deletion_request`, `deletion_certificate`, `tenant_retention_policy`; D7 flow 12; TPA-04.

### E5-S1 DPO workspace (§11.6 steps 1–4)
*As the Data Protection Officer I see every pending export, deletion and retention request and decide each one separately.*

- **Frontend:** new route `/platform/data-protection` (nav gated on `TENANT_EXPORT_APPROVE | TENANT_DELETION_APPROVE | RETENTION_CHANGE_APPROVE | TENANT_EXPORT_REQUEST | TENANT_DELETION_REQUEST`), three tabs: *Export requests*, *Deletion requests*, *Retention changes*. Each row opens a decision drawer: tenant, requester, dates, prerequisites (export done? retention met?), *Approve* / *Reject (remark required)*. The PO sees the same page with *Request* buttons and *Confirm deletion*, but no Approve/Reject (hidden and server-denied). States: loading, empty ("Nothing waiting for you"), error, denied, read-only (decided rows).
- **Backend:** `GET /platform/export-requests`, `GET /platform/deletion-requests`, `GET /platform/retention-change-requests` with `status` filter (lists exist except exports, E0-B13).
- **AC:**
  - Given a DPO, when I open the page, then all three queues load and nothing writes a DENIED audit row.
  - Given the PO who confirmed deletion request X, when the PO calls decide, then 403 (`ck_deletion_request_r4`); UI never shows the button.
  - Rejecting without a remark → 400; with remark → `REJECTED`, remark stored, tenant unchanged.
  - Bulk selection is **not offered** (C12: DPO approvals never bulk-approved).

### E5-S2 Deletion chain prerequisites (D4 `deletion_request` comments, AP-23)
- `confirmTenantDeletion` sets `status = PO_CONFIRMED` and links/creates the export; a scheduled `DeletionReadinessJob` moves PO_CONFIRMED → `EXPORT_DONE` when the export is `READY` → `PENDING_DPO` when `retention_ends_at ≤ today`. The DPO can decide only in PENDING_DPO (exists). **Exception kept (PD-7):** the DPO may approve early when the request origin is `PRODUCT_OWNER` *and* the PO ticked "waive retention wait" with a reason (stored in `dpo_remark` prefix) — otherwise the D4 rule holds.
- **AC:** confirm → PO_CONFIRMED (not PENDING_DPO); with export READY and retention not met → stays EXPORT_DONE and the DPO sees "Waiting: retention ends {date}".

### E5-S3 Export job (CL-09)
- `ExportJob` (Spring `@Scheduled` + `app.job` row): for APPROVED requests, generate CSV per Phase-1 module table group (users, institutions, academic structure, enrolments, attendance, exams, results, fees, leave, notices, complaints, notes, staff, policies), a ZIP of `app.file_object` files, and `data_dictionary.md` (table/column comments from `01_schema.sql`); store through `FileStorage` (S3 in prod, E10-S6; local in dev); status GENERATING → READY with `file_ref`, `download_expires_at = ready + 24 h`; FAILED with error on exception. `GET /platform/export-requests/{id}/download` (TENANT_EXPORT_REQUEST or TENANT_EXPORT_APPROVE) streams while not expired; an `ExpiryJob` sets EXPIRED and deletes the file.
- **AC:** approved request becomes READY within the job interval; the ZIP contains one CSV per module that has rows and the dictionary; download after 24 h → 410 `EXPORT_EXPIRED`; download is audited `EXPORT`.

### E5-S4 Purge job and deletion certificate (CL-10, PD-7)
- Entity for `platform.deletion_certificate`. `PurgeJob`: for APPROVED requests with `retention_ends_at ≤ today`: delete the tenant's `app.*` rows (as `edu_retention`-like dedicated role `edu_purge` granted DELETE on `app.*` — new SQL role in migration `37_v41_purge_role.sql`, DB test), delete files, delete the Keycloak realm, write `deletion_certificate` (counts per data class in `summary`), set request `DELETED` + `deleted_at`, tenant `db_connection_ref = NULL`.
- **AC:** after the job, no `app.*` row has that `tenant_id` (verified as superuser in an IT), the realm is gone, the certificate row exists and is visible in the DPO workspace under *Certificates* (read-only), and the tenant row remains in state DELETED.

### E5-S5 System-created deletion request at retention end (CL-07)
- `RetentionEndJob` (daily): for tenants ARCHIVED whose longest retention has ended and have no open deletion request, insert a request with `origin = SYSTEM_RETENTION_END`, `requested_by_user_id = NULL`; notify PO and DPO.
- **AC:** archived tenant past retention → request appears in the DPO queue the next day with origin "System (retention ended)".

### E5-S6 Tenant-side requests (D2 rows 194/196 "Own tenant")
- `POST /api/tenants/{id}/tenant-export-requests` and `POST /api/tenants/{id}/tenant-deletion-requests` (TENANT_EXPORT_REQUEST / TENANT_DELETION_REQUEST at TENANT scope; University/College/School Admin); `requested_by_kind = TENANT_ADMIN`, origin `TENANT_ADMIN`. Tenant Support/Governance page shows own requests and status.
- **AC:** a College Admin can request; a teacher gets 403; the request shows in the DPO workspace.

### E5-S7 DPO delegation (C12) and RBAC mapping check (§11.6 step 7, D1 §8.19)
- `POST /platform/delegations` (APPROVAL_DELEGATE, DPO only) `{delegateUserId (must be DPO), from, to}`; evaluator treats an active delegate as holder of the three approve actions; delegate cannot re-delegate; never for the delegator's own requests.
- `GET /platform/rbac-mapping-report` (RBAC_MAPPING_CHECK): compares `ref.standard_role_action` + `platform_role_action` + every tenant's `app.role_action` (via `edu_report`, read-only, no RLS bypass: iterate tenants setting the GUC) against the matrix; returns drift rows; *Export CSV*. DPO workspace tab *RBAC check*.
- **AC:** delegation to a non-DPO → 422; drift report flags a tenant custom role holding an action its base template never had.

---

## 8. Epic E6 — Observability: audit, usage, backup

Sources: D2 rows 216–219; D1 §7.14, §11.1 steps 2, 17, 20–21; D4 `usage_metric_daily`, `tenant_usage_monthly`, `tenant_backup_status`, `audit_outbox`.

### E6-S1 Usage collection (R2-13, PV-07; OD-70/73 provisional)
- `UsageRollupJob` nightly: counts per tenant for the previous day from tenant audit (`LOGIN_SUCCESS`), attendance entries, online fee payments, SMS sent (when communication exists; 0 until then, flagged `measured=false`), storage GB from `file_object` sizes, active users. Writes `tenant_usage_monthly` (metric codes `LOGINS, ATTENDANCE_MARKED, FEES_PAID_ONLINE, SMS_SENT, STORAGE_GB, ACTIVE_USERS`, `is_final = false` until month end) and `usage_metric_daily` combined totals excluding `analytics_opt_out` tenants.
- **AC:** after the job, Usage & metrics shows yesterday's numbers; an opted-out tenant's logins are absent from the combined figure but present in its own usage (billing still needs them, D2 row 217 note); `totalUserCountGap` text is replaced by `ACTIVE_USERS` computed by the job (within the job, not by a cross-tenant query at request time).

### E6-S2 Audit log filters and export (AU-03)
- `GET /platform/audit-logs?tenantId&actorUserId&actionCode&outcome&from&to&page&size`; `GET /platform/audit-logs/export?…` streams CSV (max 50,000 rows, 413 beyond) and writes an `EXPORT` audit row. UI: filter bar + Export button; row drawer with full payload.
- **AC:** filter by `outcome=DENIED` returns only denied rows; export is audited.

### E6-S3 Backup status feed (WF-33, NFR RPO 15 min / RTO 4 h; OD-63)
- `POST /platform/backup-status` (new internal op, `serviceAuth` client `edu360-backup-agent`, platform realm role `backup:write`) `{tenantId?, lastDailyBackupAt, outcome, earliestRestorableAt, latestRestorableAt, lastRestoreTestAt?, lastRestoreTestOutcome?}` upserting `tenant_backup_status`; a `docker/backup/backup-agent.ps1` + prod CronJob calling `pg_dump`/RDS snapshot API and posting the result. Alert (E9-S5) when a tenant has no SUCCESS in 36 h (threshold in settings, OD-63 provisional).
- **AC:** after one agent run, platform and tenant backup screens show real timestamps; stale backup raises an alert.

### E6-S4 Audit old/new values and masking (AU-01, AU-04, OD-09 follow-up)
- `PlatformAuditWriter.record(..., changes: Map<field,{old,new}>)` stored in a new `payload jsonb` column (migration `38_v41_platform_audit_payload.sql`); values for fields named `password|secret|token|nationalId|salary` masked `***`. Used by tenant settings, plan, platform user, security policy updates.
- **AC:** updating a user limit writes `{userLimit:{old:1000,new:1500}}`; a secret never appears in `audit_outbox`.

---

## 9. Epic E7 — Releases

Sources: D1 §11.5, §11.2 step 7; D2 row 214; R2-26.

| ID | Story | AC |
|---|---|---|
| E7-S1 Schedule deploy | `PlatformRelease.scheduled_at`; `POST /releases/{id}/schedule {at}` (RELEASE_MANAGE; PO/PA/RM); status stays STAGING_TESTED with a scheduled badge; transition to DEPLOYED allowed only after `scheduled_at` or by the PO with a reason | scheduling an UPLOADED (untested) release → 409 `NOT_STAGING_TESTED` |
| E7-S2 Release notes to tenant admins | On DEPLOYED, create an in-app notification + notice for every ACTIVE tenant's admins with the release notes (one version for all, R2-26) | every tenant admin sees "EDU 360 updated to v…" |
| E7-S3 Health after deploy | `GET /platform/releases/{id}/health`: error count from `audit_outbox` outcome FAILED and 5xx count (from a new `platform.request_metric_hourly` filled by a filter) in the 24 h after deploy vs the 24 h before | screen shows before/after with "not measured" until the metric table has rows |
| E7-S4 Schema releases | entity + write `platform.schema_release` when `sql/run_all.sh` output is registered (`POST /platform/releases/{id}/schema-version`) | release shows highest migration applied |

---

## 10. Epic E8 — Security policy and platform accounts

| ID | Story | AC |
|---|---|---|
| E8-S1 Idle timeout per role class (§11.0) | policy gains `idleTimeoutMinutesPrivileged` (default 15) / `idleTimeoutMinutesStandard` (60); `/me` returns the caller's value; `inactivityTracker` uses it; billing `due_days` setting also lives here | platform user is logged out at 15 min idle; a teacher at 60 |
| E8-S2 Realm re-sync button | `SecurityPolicyView` → *Re-apply to all realms* with per-realm result table | results shown; failure rows highlighted |
| E8-S3 Token must match host (P10, D4 §2.4) | filter compares the token's realm/tenant with `LoginTenantResolver` resolution of the `Host`; mismatch → 403 `TENANT_HOST_MISMATCH`; platform tokens only on the platform host | abc token on xyz host → 403; test added |
| E8-S4 Platform user lifecycle | reactivation and *Reset authenticator* (Keycloak OTP reset via admin client); every change audited with payload (E6-S4) | former employee INACTIVE cannot log in (Keycloak user disabled) |
| E8-S5 Idempotency-Key and If-Match (D5 conventions) | `IdempotencyService` applied to every platform op D5 marks REQUIRED (createTenant, activate, suspend, archive, decisions, createInvoice, payment, discount, export/deletion requests, confirm, billing cycle); `ETag` on GET of plan/tenant settings/platform user/ticket/release/integration type and `If-Match` → 409 `VERSION_CONFLICT` on PATCH | replaying a create with the same key returns the first response; stale If-Match → 409 |

---

## 11. Epic E9 — Dashboards, navigation and notifications per role

Sources: D1 §11.1 step 2, §11.2 step 2, §11.3 step 1, §11.4 step 1, §11.5 step 1, §11.6 step 1; D9 screen states.

| ID | Story | Content | AC |
|---|---|---|---|
| E9-S1 PO dashboard | tenants by state, revenue this month, active users (from E6-S1), system health (backup stale count, failed setups, 5xx last 24 h), usage counts, escalations, pending DP approvals | every tile has loading/empty/error states; every number traces to an endpoint the PO holds |
| E9-S2 Platform Admin dashboard | tenants pending activation, failed setups (E1-S2), open tickets, failed jobs (`app.job` FAILED count), backup alerts | no tile calls an endpoint the PA does not hold |
| E9-S3 Billing dashboard | invoices due / paid / overdue, revenue, renewals due 60 d | — |
| E9-S4 Support dashboard | open by priority, escalated, active support sessions | — |
| E9-S5 Platform alerts | `platform.platform_notification` (new table) written by E1-S2, E3-S5, E4-S3, E5-S5, E6-S3; bell in the platform shell; mark read | alert appears within 1 min (NFR) |
| E9-S6 Navigation | `PLATFORM_PAGE_ACTIONS` extended with `dataProtection`, `entitlements`, `billingCycles`; the Overview route renders the dashboard variant for `platformRole`; DPO lands on `/platform/data-protection` | each role's nav shows only pages whose `anyOf` it holds (server `/me/permissions` is the source) |

Wireframe — DPO workspace:

```
┌ Data protection ───────────────────────────────────────────────┐
│ [Export requests (3)] [Deletion requests (1)] [Retention (2)] [Certificates] [RBAC check] │
│ ┌──────────┬──────────────┬───────────┬────────────┬──────────┐ │
│ │ Tenant   │ Requested by │ Status    │ Waiting on │          │ │
│ │ ABC Univ │ Product Owner│ REQUESTED │ your decision│ [Review]│ │
│ └──────────┴──────────────┴───────────┴────────────┴──────────┘ │
│ Drawer: tenant · requester · dates · prerequisites ✓/✗ ·        │
│         [Approve]  [Reject…] (remark required)                  │
└────────────────────────────────────────────────────────────────┘
```

Wireframe — Entitlement editor (plan or tenant):

```
▸ Users, roles and access      [on ]   From plan
▾ Fees                         [on ]   Override
    Online payment             [on ]
    Scholarships               [off]
    Late-fee rules             [on ]
▸ Library (Phase 2)            [off]   disabled: not in Phase 1
```

---

## 12. Epic E10 — Production readiness (`PRODUCTION_TENANT_ROUTING.txt`)

| ID | Item | AC |
|---|---|---|
| E10-S1 P1–P2 | wildcard DNS + TLS; nginx serving `app/dist` and proxying `/api`, `/v1` with `Host` preserved; port 8080 private; swagger blocked | checklist lines 226–235 |
| E10-S2 P3 | `axios.ts` same-origin in prod | network tab shows same-origin calls |
| E10-S3 P4–P5 | `tenant-host-suffix`, `platform-host-label`, no wildcard CORS, prod properties without defaults | missing env stops startup |
| E10-S4 P6–P7 | reserved subdomains (E1-S1); Keycloak redirect URIs from config; existing realms migrated | — |
| E10-S5 P11–P12 | admin client-credentials service account with `manage-users` on realm clients; identity-events client | tenant creation works with no master password set |
| E10-S6 Object storage | `S3FileStorage` implementing `FileStorage` (LocalStack in dev); exports, logos, uploads | export ZIP lands in S3 |
| E10-S7 Backup agent | E6-S3 in prod (RDS snapshots / pg_dump to S3) | first SUCCESS row |
| E10-S8 Verification | pen test, load test (10× peak 1 h), restore rehearsal, E2E-001/033/035 on staging | reports attached to `docs/` |
| E10-S9 (optional, later) ISOLATED tenants | orchestrator for DB create + Secrets Manager + migrations (PD-8) | — |

---

## 13. Epic E11 — Tests and evidence

- **Action tests:** keep `PlatformActionTest` as the ACT-* mapping; add ALLOW/DENY/SCOPE/GUARD for every new endpoint (billing cycle, approval, plan-change, escalation, export decision, delegation, rbac report, support session, provisioning retry, branding).
- **Integration (`*IT`, real stack):** `SupportSessionIT`, `TenantProvisioningIT`, `ExportJobIT`, `PurgeJobIT`, `TokenHostMismatchIT`. Add Failsafe to `api/pom.xml` so `verify -Pit` runs them in CI with the Postgres + Keycloak services.
- **SQL:** `34…38_v41_*.sql` each with `r` rollback and `t` tests; `run_all.sh` extended; DB test for the `edu_purge` role.
- **Frontend:** vitest per new screen (loading/empty/error/denied/read-only), MSW handlers generated from the Java DTOs.
- **E2E:** Playwright suite `app/e2e/platform/` covering E2E-001 (onboard → activate), E2E-033 (support session), E2E-035 (standalone go-live), plus "DPO decides deletion", "Billing cycle to paid". Runs against `dev-up.ps1` stack.
- **Evidence:** every story's PR links the test run; `PHASE1_PROGRESS_CHECKLIST.md` Platform rows get a *Verified* column fed only by passing E2E.

---

## 14. New API operations (record at the next D5 revision)

| Operation | Method/path | Action | Epic |
|---|---|---|---|
| getPlanEntitlements / getTenantEntitlements | GET …/entitlements | PLAN_MANAGE·FEATURE_TOGGLE / FEATURE_TOGGLE·TENANT_SETTINGS | E0 |
| decideTenantExport (D5 exists), downloadTenantExport | POST …/export-requests/{id}/decision; GET …/download | TENANT_EXPORT_APPROVE; TENANT_EXPORT_REQUEST | E0/E5 |
| retryTenantProvisioning | POST /platform/tenants/{id}/provisioning/retry | TENANT_CREATE | E1 |
| setTenantBranding / getTenantBranding | PUT/GET /platform/tenants/{id}/branding | TENANT_SETTINGS | E1 |
| runBillingCycle | POST /platform/billing-cycles | INVOICE_MANAGE | E3 |
| approveInvoice | POST /platform/invoices/{id}/approval | INVOICE_MANAGE (PO) | E3 |
| changeTenantPlan | POST /platform/tenants/{id}/plan-change | TENANT_SETTINGS | E3 |
| escalateTicket | POST /platform/support-tickets/{id}/escalation | SUPPORT_TICKET_MANAGE | E4 |
| endSupportSession | DELETE /platform/support-sessions/current | SUPPORT_ACCESS_USE | E4 |
| createPlatformDelegation | POST /platform/delegations | APPROVAL_DELEGATE | E5 |
| getRbacMappingReport | GET /platform/rbac-mapping-report | RBAC_MAPPING_CHECK | E5 |
| exportPlatformAuditLogs | GET /platform/audit-logs/export | PLATFORM_AUDIT_VIEW | E6 |
| postBackupStatus | POST /platform/backup-status | serviceAuth `backup:write` | E6 |
| scheduleRelease, getReleaseHealth | POST …/schedule; GET …/health | RELEASE_MANAGE | E7 |
| listPlatformNotifications, markRead | GET/POST /platform/me/notifications | any platform role | E9 |
| tenant: requestTenantExport, requestTenantDeletion, getUserLimit, requestUpgrade, getBranding | tenant paths | per D2 | E1/E5 |

## 15. Database migrations

| File | Content |
|---|---|
| `34_v41_plan_commercial.sql` | plan commercial columns; tenant `next_renewal_at`, `pending_plan_id`; `branding_colors` already exists |
| `35_v41_feature_catalog_provisional.sql` | `ref.feature_catalog.provisional`; seed section 6; `ref.module_dependency` seed |
| `36_v41_invoice_approval.sql` | `invoice.approved_by/approved_at/due_at`; `support_ticket` category check against setting; `platform.support_session`; `platform.platform_notification` |
| `37_v41_purge_role.sql` | `edu_purge` role, grants, DB test that `edu_app` still cannot DELETE |
| `38_v41_platform_audit_payload.sql` | `audit_outbox.payload jsonb`; `request_metric_hourly` |
Each with `r` rollback and `t` tests; `run_all.sh` updated; `provision-edu-app.ps1` list extended.

## 16. Provisional feature catalogue (PD-11; replace when OD-43 closes)

Hard dependencies (`ref.module_dependency`): RESULTS→EXAMINATIONS; EXAMINATIONS→INSTITUTION_SETUP; ATTENDANCE→TIMETABLE; TIMETABLE→INSTITUTION_SETUP; ASSIGNMENTS→INSTITUTION_SETUP; FEES→INSTITUTION_SETUP; LEAVE→USERS_ACCESS; RISK→ATTENDANCE; everything→USERS_ACCESS. `USERS_ACCESS`, `INSTITUTION_SETUP`, `GOVERNANCE`, `PLATFORM` cannot be switched off.

| Module | Features (`MODULE:FEATURE`) — from D1 §8 |
|---|---|
| USERS_ACCESS | BULK_UPLOAD, CUSTOM_ROLES, GUARDIAN_LINKS, PARENT_OTP_LOGIN |
| INSTITUTION_SETUP | SUBJECT_GROUPS, PROMOTION, GRADING_SCHEMES, WEEKLY_OFF |
| TIMETABLE | ONLINE_CLASSES, SYLLABUS |
| ATTENDANCE | CORRECTION_REQUESTS, BULK_UPLOAD, SUBSTITUTES, DAILY_SMS_SUMMARY |
| EXAMINATIONS | HALL_TICKETS, EXTERNAL_MARKS_UPLOAD, SUPPLEMENTARY |
| RESULTS | WITHDRAWAL, PERFORMANCE_REPORT |
| ASSIGNMENTS | STUDY_MATERIAL, HOMEWORK_STATUS |
| FEES | ONLINE_PAYMENT, OFFLINE_PAYMENT, SCHOLARSHIPS, CONCESSIONS, LATE_FEE_RULES, REMINDERS, TALLY_EXPORT, PERIOD_LOCK |
| LEAVE | STUDENT_LEAVE, STAFF_LEAVE, LEAVE_BALANCES, LOSS_OF_PAY |
| COMMUNICATION | NOTICES, MESSAGES, CLASS_BROADCAST, SMS, EMAIL, PUSH, EMERGENCY_NOTICE |
| COMMUNITY | POSTS, COMMENTS, EVENTS, RSVP |
| COMPLAINTS | ESCALATION |
| STUDENT_NOTES | DISCIPLINE, HEALTH, PARENT_HEALTH_VIEW |
| POLICIES | POLICY_ACCEPTANCE, DEPARTMENT_DOCUMENTS, ACCREDITATION_DOCUMENTS |
| STAFF_RECORDS | SERVICE_RECORDS, DOCUMENT_VERIFICATION |
| RISK | ATTENDANCE_RISK, ACADEMIC_RISK, PARENT_ALERTS |
| REPORTS | FIXED_REPORTS, BUYER_DASHBOARD, STATUTORY_EXPORTS, ID_CARD_EXPORT, USAGE_ANALYTICS |
| DATA_RECORDS | IMPORT_TEMPLATES, GLOBAL_SEARCH, DUPLICATE_REVIEW, BULK_CORRECTION, CERTIFICATES |
| GOVERNANCE | AUDIT_EXPORT, DATA_REQUESTS, CONSENT, COMPLIANCE_REPORTS, RBAC_MAPPING |
| PLATFORM | SUPPORT_ACCESS, BACKUP_STATUS |
| Phase 2/3 modules | one feature `CORE` each until their specs are implemented |

## 17. Order and expected completion

| Wave | Epics | Calendar (1 senior dev) | Platform score after |
|---|---|---|---|
| 0 | E0 | 2–3 days | 70% |
| 1 | E9, E2, E4-S2..S4, E5-S1, E8-S1..S2 | 2 weeks | 80% |
| 2 | E1, E3, E5-S2..S7, E6, E7 | 3–4 weeks | 92% |
| 3 | E4-S1 support session, E8-S3..S5 | 2 weeks | 97% |
| 4 | E10, E11 (staging, E2E, pen/load/restore) | 2–3 weeks + infra | 100% deployable |

Scores use the method in the gap analysis of 2026-11-15/16 (D5 operations, D1 §11 steps, D2 actions, D4 tables, routing checklist, tests).
