# Chat A5 — Data protection (Data Protection Officer)

You are a senior full-stack developer (Java 21 / Spring Boot, React 19 / TypeScript, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Give the Data Protection Officer a complete workspace and finish the data-protection chains:
- export, deletion and retention queues;
- deletion prerequisites;
- a real export file;
- the purge job with a deletion certificate;
- automatic deletion requests when retention ends;
- tenant-side requests;
- DPO-only delegation;
- the RBAC mapping check.

The Product Owner can request and confirm, but **never approve** (TPA-04).

## Your stories

Your acceptance criteria are **every bullet and table cell for these story IDs in `docs/PLATFORM_COMPLETION_PLAN.md`**, word for word. That file is the contract. The notes below add detail; they never remove anything.

| ID | What | Plan section |
|---|---|---|
| E5-S1 | New route `/platform/data-protection` with tabs Export requests / Deletion requests / Retention changes, plus Certificates and RBAC check from S4 and S7. Decision drawer: Approve, or Reject with a required remark. **No bulk selection** (C12). The Product Owner sees Request / Confirm, never Approve / Reject (hidden on screen and refused by the server). All screen states. The nav entry is gated on the five DP actions (append it to the platform nav; A1 owns the file). | §7 |
| E5-S2 | Confirm → `PO_CONFIRMED`. A daily `DeletionReadinessJob` moves the request to `EXPORT_DONE` when its export is READY, then to `PENDING_DPO` when `retention_ends_at ≤ today`. Keep the plan's PD-7 exception: the Product Owner's "waive retention wait" with a reason. | §7 |
| E5-S3 | `ExportJob`: APPROVED → GENERATING → READY: one CSV per Phase-1 module group, a ZIP of files, and `data_dictionary.md`. Stored through the `FileStorage` interface. `download_expires_at = ready + 24 h`. `GET /api/platform/export-requests/{id}/download` (410 `EXPORT_EXPIRED` after expiry; audited `EXPORT`). `ExpiryJob` sets EXPIRED and deletes the file. A1 builds the decision endpoint (E0-B13); you build everything after APPROVED. | §7 |
| E5-S4 | `PurgeJob` with a dedicated `edu_purge` database role. Delete the tenant's `app.*` rows, its files and its Keycloak realm. Write `platform.deletion_certificate` (counts per data class). Request → DELETED with `deleted_at`; `db_connection_ref = NULL`. Certificates tab (read-only). Add `PurgeJobIT`. | §7 |
| E5-S5 | Daily `RetentionEndJob` creates `origin = SYSTEM_RETENTION_END` requests for ARCHIVED tenants past retention and notifies the Product Owner and the DPO. | §7 |
| E5-S6 | Tenant side: `POST /api/tenants/{id}/tenant-export-requests` and `POST /api/tenants/{id}/tenant-deletion-requests` (University / College / School Admin at TENANT scope). The tenant screen shows their own requests and status. | §7 |
| E5-S7 | `POST /api/platform/delegations` (APPROVAL_DELEGATE; DPO to DPO only, date-bounded; no re-delegation; never for own requests). `GET /api/platform/rbac-mapping-report` (RBAC_MAPPING_CHECK), with drift rows and CSV export. RBAC check tab. | §7 |

## Database migrations you own

Use **only** numbers 42 and 43, each with `r` and `t` files, registered in `run_all.sh` and `provision-edu-app.ps1`.
- `42_v41_purge_role.sql`: the `edu_purge` role (LOGIN, NOBYPASSRLS), DELETE grants on `app.*`, and a DB test proving `edu_app` still cannot delete. **This adds a new database role beyond D4 §2.3. Say so clearly in your brief and get the user's approval.**
- `43_v41_platform_delegation.sql`: a platform delegation table, if you need one.
- `platform.deletion_certificate` already exists; map it as an entity.

## Files you own

The `platformops/dataprotection/` package (except the export decision endpoint added by A1), the export, purge, readiness and retention-end jobs, the data-protection page and its components, and the tenant-side export and deletion request endpoints and screen.

## Depends on / others depend on you

- **You need from A1:**
  - the export decision endpoint (E0-B13) and the fixed data-protection DTOs;
  - the notification service, audit payload and idempotency helper. Apply the helper to your new operations.
- **You need from A2:** retention rows are written at tenant creation (E1-S1). Until then, use the existing `RETENTION_NOT_SET` behaviour.
- **You need from A8:** `S3FileStorage` comes later. Use the `FileStorage` interface and the local implementation now, and **don't depend on S3**.
- **Others need from you:**
  - A7 needs the counts of pending DP items for the dashboards;
  - A8 writes the "DPO decides deletion" browser test.

## Not yours (other chats build these — do not build them)

E0 including E0-B13 (A1), E1 and E2 (A2), E3 (A3), E4 and E8-S3 (A4), E6, E7 and E8-S1/S2/S4 (A6), E8-S5 (A1), E9 (A7), E10 and E11 (A8).

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
