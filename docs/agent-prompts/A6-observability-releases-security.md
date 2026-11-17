# Chat A6 — Usage, backup, audit log, releases and security policy

You are a senior full-stack developer (Java 21 / Spring Boot, React 19 / TypeScript, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Put real data behind the platform's monitoring screens and finish releases and security settings:
- usage collection, so the usage screens stop showing empty values;
- a backup status feed;
- audit-log filters and export;
- release scheduling, notes and health;
- idle timeouts per role;
- a button to re-apply the security policy to every Keycloak realm;
- platform user lifecycle.

## Your stories

Your acceptance criteria are **every bullet and table cell for these story IDs in `docs/PLATFORM_COMPLETION_PLAN.md`**, word for word. That file is the contract. The notes below add detail; they never remove anything.

| ID | What | Plan section |
|---|---|---|
| E6-S1 | Nightly `UsageRollupJob`. Writes `tenant_usage_monthly` (metric codes `LOGINS, ATTENDANCE_MARKED, FEES_PAID_ONLINE, SMS_SENT, STORAGE_GB, ACTIVE_USERS`, `is_final = false` until month end) and `usage_metric_daily` (combined totals, excluding opted-out tenants). Unmeasured metrics are marked as not measured. Replace the `totalUserCountGap` text with `ACTIVE_USERS` computed by the job, never by a cross-tenant query at request time. | §8 |
| E6-S2 | `GET /api/platform/audit-logs?tenantId&actorUserId&actionCode&outcome&from&to&page&size`. `GET /api/platform/audit-logs/export` streams CSV (maximum 50,000 rows, 413 beyond that) and writes an `EXPORT` audit row. Filter bar, Export button, and a row drawer with the full payload. | §8 |
| E6-S3 | `POST /api/platform/backup-status`: service authentication through Keycloak client `edu360-backup-agent` with platform-realm role `backup:write`. It upserts `tenant_backup_status`. Add `docker/backup/backup-agent.ps1` for dev. Alert through A1's notification service when a tenant has no SUCCESS in 36 hours (provisional, OD-63). The production CronJob belongs to A8. | §8 |
| E7-S1 | `POST /api/platform/releases/{id}/schedule {at}` with `scheduled_at`. Scheduling an UPLOADED release → 409 `NOT_STAGING_TESTED`. DEPLOYED only after `scheduled_at`, or by the Product Owner with a reason. | §9 |
| E7-S2 | On DEPLOYED, every ACTIVE tenant's admins get an in-app notification with the release notes. | §9 |
| E7-S3 | `GET /api/platform/releases/{id}/health`: errors and 5xx in the 24 hours before and after deploy, using a new `platform.request_metric_hourly` filled by a filter. Shows "not measured" until there is data. | §9 |
| E7-S4 | Map `platform.schema_release`. `POST /api/platform/releases/{id}/schema-version` records the highest migration applied. | §9 |
| E8-S1 | Security policy gains `idleTimeoutMinutesPrivileged` (15) and `idleTimeoutMinutesStandard` (60). `/me` returns the caller's value, and `inactivityTracker` uses it. The **billing due-days setting is not yours**: chat A3 owns it, even though plan E8-S1 mentions it. | §10 |
| E8-S2 | "Re-apply to all realms" button in `SecurityPolicyView`, with a result table per realm and failed rows highlighted. The endpoint already exists. | §10 |
| E8-S4 | Platform user reactivation and "Reset authenticator" (Keycloak OTP reset). Every change is audited with the payload. An INACTIVE user's Keycloak account is disabled, so they can't log in. | §10 |

## Database migrations you own

Use **only** numbers 44 and 45, each with `r` and `t` files, registered in `run_all.sh` and `provision-edu-app.ps1`.
- `44_v41_request_metrics_and_release_schedule.sql`: `platform.request_metric_hourly`, `platform.release.scheduled_at`.
- `45`: spare, for anything else you need, such as a backup-alert setting.
- `platform.usage_metric_daily`, `tenant_usage_monthly`, `tenant_backup_status` and `schema_release` already exist; map whatever isn't mapped yet.

## Files you own

The `UsageCountsController`, `TenantUsageController` (backend), `BackupStatusController`, `PlatformAuditLogController`, the release package, `PlatformSecurityPolicyController` / `SecurityPolicyService`, `PlatformUserManagementService` lifecycle methods, the usage, backup and request-metric jobs and filter, and the screens `UsageCountsView`, `BackupStatusView`, `AuditLogView`, `ReleasesView`, `SecurityPolicyView` and `PlatformUsersView`. Also `app/src/services/inactivityTracker.ts` and `docker/backup/`.

## Depends on / others depend on you

- **You need from A1:** the notification service, the audit payload API and the idempotency helper. Apply the helper to your new operations.
- **Others need from you:**
  - A3 reads `tenant_usage_monthly` for billing lines;
  - A7 needs usage, backup-alert and 5xx figures for the dashboards;
  - A8 needs your backup agent to build the production CronJob.

## Not yours (other chats build these — do not build them)

E0 (A1), E1 and E2 (A2), E3 including the due-days setting (A3), E4 and E8-S3 (A4), E5 (A5), E6-S4 and E8-S5 (A1), E9 (A7), E10 and E11 (A8).

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
