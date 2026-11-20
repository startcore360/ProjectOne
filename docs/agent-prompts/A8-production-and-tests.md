# Chat A8 — Production readiness and end-to-end tests

You are a senior full-stack developer and DevOps engineer (Java 21 / Spring Boot, React 19 / TypeScript, PostgreSQL 16, Keycloak, nginx, AWS) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Make the platform deployable on a real domain, and prove it works end to end:
- tenant routing on a real domain;
- the Keycloak service accounts;
- object storage;
- the production backup job;
- the test harness;
- the browser end-to-end tests that show the platform works as a whole.

**Start the production items (E10) at once. Write each end-to-end test only after the feature it covers has been pushed by its chat.**

## Your stories

Your acceptance criteria are **every bullet and table cell for these story IDs in `docs/PLATFORM_COMPLETION_PLAN.md`** (§12 and §13), plus the whole "Done when" checklist in `docker/PRODUCTION_TENANT_ROUTING.txt`, word for word. The notes below add detail; they never remove anything.

| ID | What | Source |
|---|---|---|
| E10-S1 | P1–P2: wildcard DNS and TLS (document the steps; the domain isn't chosen yet, so use `edu360.com` as a placeholder with an environment variable). nginx serves `app/dist` and proxies `/api` and `/v1` with `Host` preserved; port 8080 private; swagger blocked. | routing doc P1, P2 |
| E10-S2 | P3: `app/src/services/axios.ts` uses same-origin calls in production (keep the dev behaviour). | P3 |
| E10-S3 | P4–P5: `tenant-host-suffix` and `platform-host-label` settings (rename `localDevHostSuffix`); production properties with no defaults, so a missing value stops startup; no wildcard CORS. | P4, P5 |
| E10-S4 | P7: Keycloak redirect URIs from configuration (today hard-coded in `KeycloakAdminClient.java` ~line 113), plus a one-time fix for existing realms. P6 reserved subdomains belong to chat A2 (E1-S1); don't build them. | P6, P7 |
| E10-S5 | P11–P12: `KeycloakAdminClient` uses a client-credentials service account in production (today it uses the master-realm password). Add the identity-events client. | P11, P12 |
| E10-S6 | `S3FileStorage` implementing the existing `FileStorage` interface (LocalStack in dev, S3 in prod), chosen by configuration. Exports, logos and uploads all go through it. | plan §12 |
| E10-S7 | Production backup job: a CronJob or scheduled task running chat A6's backup agent against RDS snapshots or `pg_dump` to S3 and posting the result. | plan §12 |
| E10-S8 | Verification: the plan for pen test, load test (10× peak for 1 hour) and restore rehearsal; run what can run locally; write reports to `docs/`. | plan §12 |
| E10-S9 | **Optional — only if the user asks:** isolated (database-per-tenant) provisioning. | plan §12, PD-8 |
| E11 | Test harness and end-to-end: add a Failsafe profile to `api/pom.xml` so `*IT` tests run with `verify -Pit` (and in CI with Postgres and Keycloak services). Add a Playwright suite in `app/e2e/platform/` with E2E-001 (onboard → activate), E2E-033 (support session), E2E-035 (standalone go-live), "DPO decides deletion", "Billing cycle to paid", and one landing-page check per platform role. Runs against the `dev-up.ps1` stack. | plan §13 |

Each chat writes the ALLOW/DENY/SCOPE/GUARD and `*IT` tests for its own features. You build the harness and the cross-feature browser tests.

## Database migrations you own

Use **only** numbers 47 and 48, and only if production setup needs a schema change. Include `r` and `t` files and register them.

## Files you own

`docker/` (except `docker/backup/backup-agent.ps1`, which is A6's), nginx and deployment config, the production branch of `app/src/services/axios.ts`, `KeycloakProperties`, the host-suffix handling in `LoginTenantResolver`, the Keycloak client and redirect configuration in `KeycloakAdminClient`, the new `S3FileStorage`, `api/pom.xml` test plugins, `app/e2e/`, the Playwright config, `.github/workflows/ci.yml` (E2E and IT jobs; never remove existing gates), and `application-prod.properties`.

## Depends on / others depend on you

- **You need from every other chat:** their features pushed before you write the matching end-to-end test.
- **You need from A6:** the backup agent script.
- **You need from A4:** the token/host filter (E8-S3); you only add the production configuration around it.
- **Others need from you:**
  - A5 uses `FileStorage`, and your S3 version slots in without changes to their code;
  - every chat benefits from the Failsafe profile, so push it early.

## Not yours (other chats build these — do not build them)

E0 (A1), E1, E2 and P6 reserved subdomains (A2), E3 (A3), E4 and E8-S3 (A4), E5 (A5), E6, E7 and E8-S1/S2/S4 (A6), E8-S5 (A1), E9 (A7). Don't fix their features; report failing end-to-end tests to the user, naming the chat that owns the feature.

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
6. **For this chat also:** `docker/PRODUCTION_TENANT_ROUTING.txt` and `LOCAL_DEV.md`, in full.

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
