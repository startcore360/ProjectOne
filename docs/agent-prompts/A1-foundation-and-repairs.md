# Chat A1 — Foundation and repairs

You are a senior full-stack developer (Java 21 / Spring Boot, React 19 / TypeScript, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Make the existing platform console honest and safe: fix every broken screen and endpoint (Epic E0), and build the shared pieces the other chats depend on (the platform notification store, the audit "what changed" payload, and Idempotency-Key / If-Match support). **You go first.** Other chats wait for your shared pieces, so push them early.

## Your stories

Your acceptance criteria are **every bullet and table cell for these story IDs in `docs/PLATFORM_COMPLETION_PLAN.md`**, word for word. That file is the contract. The notes below add detail; they never remove anything.

| ID | What | Plan section |
|---|---|---|
| E0-B1 | Overview page loads for every platform role; it only calls endpoints the caller holds (use `/api/platform/me/permissions`). No 403 and no DENIED audit row on page load. A simple role-safe version is enough; chat A7 builds the full per-role dashboards later. | §2 |
| E0-B2 | Tenant deletion goes through `POST /api/platform/deletion-requests` (request → Product Owner confirm → DPO decision). Remove `deleteTenant` (`DELETE /platform/tenants/{id}`) from `app/src/features/platform/api/platformTenantService.ts`. | §2 |
| E0-B3 | Retention change request calls `POST /api/platform/tenants/{id}/retention-change-requests`. The data-class dropdown uses the 14 `ref.data_class` codes. | §2 |
| E0-B4 / B6 | Export and retention tables use the real backend field names (`exportRequestId`, `status`, `createdAt`, `fileRef`, `downloadExpiresAt`; `dataClassCode`, `retentionMonths`, `effectiveFrom`). No runtime error with real rows. | §2 |
| E0-B5 | Usage modal shows `platform.tenant_usage_monthly` rows (month, metric code, quantity, computed at, final), with an empty state. | §2 |
| E0-B7 | Tenant creation never returns 500. `isolationMode = ISOLATED` → 422 `ISOLATION_NOT_AVAILABLE`; the default becomes `SHARED` (decision PD-8). | §2 |
| E0-B8 | Suspend sets `suspended_at`, archive sets `archived_at`, reactivation clears them. | §2 |
| E0-B9 | Platform Admin (and custom roles based on Platform Admin) cannot change `priceAmount`, `currencyCode` or `pricingType` → 403 `PRICE_CHANGE_PO_ONLY` + DENIED audit row. Hide price fields for non-Product-Owner. | §2 |
| E0-B12 | `GET /api/platform/plans/{id}/entitlements` and `GET /api/platform/tenants/{id}/entitlements` (tenant: `plan`, `override`, `effective`). | §2 |
| E0-B13 | `POST /api/platform/export-requests/{id}/decision` (DPO only; requester never decides; REJECTED needs a remark) and `GET /api/platform/export-requests` (filter by `status`, `tenantId`). On APPROVED, set status `APPROVED` only; the export job itself is chat A5's. | §2 |
| E0-B14 | Test that platform and tenant backup screens say "No backup system connected" when there is no data. | §2 |
| E0-B15 (new) | Remove `body.put("detail", ...)` from 500 responses in `GlobalExceptionHandler.java` (line ~267). A 500 must return a generic message (`CLAUDE.md`). Keep the exception logged on the server. | — |
| E6-S4 | Audit "what changed" payload with masking (`password`, `secret`, `token`, `nationalId`, `salary` → `***`). Add `PlatformAuditWriter.record(..., changes)`. Use it in your own changes; other chats call it for theirs. | §8 |
| E8-S5 | Idempotency-Key and ETag/If-Match helpers for platform operations. Apply them to **existing** operations that D5 marks REQUIRED: createTenant, activate, suspend, archive, createInvoice, payment, discount, deletion request/confirm/decision, retention decision, export decision. Other chats apply the same helper to the new operations they add. Note that `api/.../idempotency/IdempotencyService.java` exists but is tenant-side (`app` schema); platform calls run on the `edu_platform` pool, so they need a platform-side store. | §10 |
| E9-S5 (write side only) | Table `platform.platform_notification` and a `PlatformNotificationService.notify(roleOrUserTarget, type, title, body, link)` other chats call. Chat A7 builds the bell, list and mark-read API. | §11 |

## Database migrations you own

Use **only** numbers 49 and 50 (number 34 is already taken on `master` by `34_v41_staff_record_view_hr_grants.sql`). Each needs `NNr_` rollback and `NNt_` test files, and must be registered in `sql/run_all.sh` and `docker/postgres/provision-edu-app.ps1`.
- `49_v41_platform_notification.sql`: `platform.platform_notification` (id, target_platform_user_id nullable, target_role nullable, type, title, body, link, created_at, read_at), grants to `edu_platform`.
- `50_v41_platform_audit_payload_and_idempotency.sql`: `platform.audit_outbox.payload jsonb`, plus a platform idempotency-key table if you need one.

Plan §15 lists other file numbers. **Ignore those numbers**; each chat has its own reserved range.

## Files you own

`PlatformPermissionService`, `PlatformAuditWriter`, `GlobalExceptionHandler`, `platformAccess.ts`, the platform section of `app/src/layout/AppLayout.tsx`, the platform section of `app/src/routes/Routes.tsx`, existing types in `platformTenantService.ts` and `platformOpsService.ts`, `TenantDataProtectionModal.tsx`, `TenantUsageModal.tsx`, `PlatformOverviewView.tsx` (until A7 replaces it).

## Depends on / others depend on you

- You depend on nobody.
- **A2–A8 depend on you** for: the notification service, the audit payload API, the idempotency/If-Match helpers and the fixed DTOs. Push these **first**, in their own small commits, before the screen repairs.

## Not yours (other chats build these — do not build them)

E1, E2, E3, E4, E5 (except E0-B13), E6-S1..S3, E7, E8-S1..S4, E9 (except the E9-S5 write side), E10, E11.

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
