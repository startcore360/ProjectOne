# Prompt for the "EDU 360 Final" project — split the platform work into agent prompts

> **Before pasting:** commit the current work (including `docs/` and `CLAUDE.md`, now un-ignored) and merge `manoj_feature` into `sprint-2-development`, so the agents get the latest code and documents when they pull.
> Paste everything below the line into a new chat in the claude.ai project "EDU 360 Final".
> **Attach these files to that chat**. The project sources are dated 30 Oct 2026 and don't contain them, and that chat cannot read the repository:
> 1. `docs/PLATFORM_COMPLETION_PLAN.md` (the epics, stories and acceptance criteria — the main input)
> 2. `docs/GOVERNANCE_DECISION_LOG.md`
> 3. `docs/TOP_POST_AUTHORITY_RULES.md`
> 4. `docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md`
> 5. `docker/PRODUCTION_TENANT_ROUTING.txt`
> 6. `CLAUDE.md`
> 7. `docs/V4.0_SPEC_SUMMARY.md`

---

You are the delivery lead for EDU 360. Your job in this chat is **planning only**: split the remaining **platform-side** work into separate, self-contained prompts, one per AI coding agent. I will give each prompt to a different agent. The agents work in parallel on the same Git branch and do not see each other's chats, this chat or this project. Each prompt must therefore contain everything that agent needs to finish its part 100%: backend, database, API, frontend screens, tests and validation. Do not write any code yourself.

## 1. Sources and which one wins

- The project sources (D1–D10, the SQL bundle and the OpenAPI file) are dated **30 Oct 2026**. Decisions taken after that date are in the attached files and **override the sources wherever they conflict**.
- Authority order: Product Owner decisions (decision log and section 2 below), then D1 Part II with D2 and D3, then D4 and D5, then D7, D9 and D10.
- `PLATFORM_COMPLETION_PLAN.md` is the agreed scope. Every story in it (E0 to E11) must be assigned to **exactly one** agent. Copy every acceptance criterion **word for word**. Do not summarise criteria, merge them or drop any.
- If you find a conflict between the plan and a source that the plan does not already explain, do not resolve it silently. List it in a "Questions for the Product Owner" section at the end of your answer, and keep that story's wording unchanged.

## 2. Decisions every agent must follow (repeat them in every prompt)

- **TPA-01:** the Product Owner runs the platform and holds every platform action, then hands work to staff. Every screen must work for the Product Owner alone. Other platform roles see the same screens filtered by `GET /api/platform/me/permissions`.
- **TPA-02:** a permission is handed over as "shared" or "only this person". On "only this person" work, the Product Owner can act only by stepping in with a reason, for 30 minutes. This is already built; do not change it.
- **TPA-03:** templates are editable. The Product Owner and Data Protection Officer roles cannot be customised. This is already built.
- **TPA-04:** these safety rules stay in force:
  - The four data-protection approvals (`TENANT_EXPORT_APPROVE`, `TENANT_DELETION_APPROVE`, `RETENTION_CHANGE_APPROVE`, `APPROVAL_DELEGATE`) belong only to a separate Data Protection Officer. The Product Owner can request and confirm, never approve.
  - Platform staff see tenant data only through a tenant-granted, ticket-bound, read-only session of at most 24 hours.
  - Nobody approves their own request.
  - Tenant and institution separation stays enforced.
- **TPA-05, SES-01:** as in the decision log.
- **PD-7:** deletion approval locks the tenant now; the purge runs later.
- **L-01:** `DELETE /platform/tenants/{id}` is removed. Deletion happens only through the three-step chain.
- **PD-8:** every tenant is created **SHARED**. A request for `ISOLATED` is rejected with 422 `ISOLATION_NOT_AVAILABLE`. Isolated provisioning is optional later work.
- **PD-9:** the create-tenant form requires `retentionMonths` and `backupRetentionDays`. There is no system default. Later changes need Data Protection Officer approval.
- **PD-10:** the first tenant admin gets a temporary password, shown once and never stored or returned again, with a forced change at first login. No invitation e-mails.
- **PD-11:** the entitlement editor shows the 29 modules plus the provisional feature list in plan section 16. It is seeded into `ref.feature_catalog` with `provisional = true`.
- **Plan section 0.3:** the rules resolved there (percentage discounts, invoice APPROVED step, billing from plan plus usage lines, provisional ticket categories, per-role idle timeout, and the others listed) are binding.

## 3. Facts about the current code (put the relevant ones in each prompt)

- **Stack:** Spring Boot monolith in `api/` (Java 21) and React 19 + TypeScript + Vite in `app/`. Postgres 16 with RLS. Keycloak with one realm per tenant plus `edu360-platform`. LocalStack.
- **Platform backend:** `api/src/main/java/com/edu360/api/platformops/`.
  - It runs on a separate `edu_platform` connection pool (`config/PlatformJpaConfig`).
  - Authorisation goes through `PlatformPermissionService.authorize(principal, "ACTION")`. There is no `@PreAuthorize` anywhere.
  - Audit goes through `role/PlatformAuditWriter`, which is required and never optional.
  - Errors go through `exception/GlobalExceptionHandler`.
- **Platform frontend:** `app/src/features/platform/`.
  - API calls go through `api/*.ts`, which use the shared axios instance.
  - Menu gating is in `platformAccess.ts` and `app/src/layout/AppLayout.tsx`. Routes are in `app/src/routes/Routes.tsx`.
  - Use design-system components only (`app/src/design-system/`), with no hard-coded colours or fonts.
- **Database:** `sql/` is the only schema authority. Hibernate never creates tables. Every migration is a numbered `NN_*.sql` with an `NNr_` rollback and an `NNt_` test file. Each new file is registered in `sql/run_all.sh` and in `docker/postgres/provision-edu-app.ps1`. The highest number used today is **33**.
- **Known defects (plan section 2, E0):** the Overview page breaks for non-Product-Owner roles; the deletion tab calls a removed endpoint; the retention request goes to the wrong path; the export, usage and retention DTOs don't match the backend; ISOLATED creation returns 500; `suspended_at` and `archived_at` are never set; Platform Admin can change prices; entitlements have no GET; the Data Protection Officer export decision is missing; the support session returns 501.
- **Commands:**
  - Run the local stack with `.\dev-up.ps1`.
  - Run backend tests with `.\mvnw.cmd clean verify` in `api/`.
  - Run frontend checks with `npm run lint`, `npm test` and `npm run build` in `app/`.
  - Run the SQL tests with `OUT=<temp file> ./sql/run_all.sh`.
  - Run `*IT` tests by name while the stack is up.
- **Documents in the repository:** `CLAUDE.md`, `LOCAL_DEV.md`, `PHASE1_PROGRESS_CHECKLIST.md` and all of `docs/` are committed on `sprint-2-development`. This includes the full V4.0 package under `docs/EDU360_V4.0_Final/`, `PLATFORM_COMPLETION_PLAN.md` and `GOVERNANCE_DECISION_LOG.md`. Agents read them from their checkout after pulling; no attachments are needed.

## 4. Workstream split (use this split; change it only with a stated reason)

| Agent | Workstream | Stories | Owns these files (others may only append, see section 5) | Migration numbers reserved |
|---|---|---|---|---|
| **A1** | Foundation and repairs | E0 (all), E8-S5 (Idempotency-Key and ETag/If-Match helpers applied to existing platform operations), E6-S4 (audit payload and masking), plus the shared `platform.platform_notification` table and `PlatformNotificationService` (the write side of E9-S5) | `PlatformPermissionService`, `PlatformAuditWriter`, `GlobalExceptionHandler`, `platformAccess.ts`, the platform section of `AppLayout.tsx`, the platform section of `Routes.tsx`, existing DTO fixes in `platformTenantService.ts` and `platformOpsService.ts` | 34, 35 |
| **A2** | Tenant onboarding and plans | E1 (S1–S5, S7), E2 (S1, S2) including the PD-11 feature catalogue seed and branding | `TenantManagementService`, new `TenantProvisioningService`, `PlatformFeatureToggleController`, tenants and plans screens, new `EntitlementEditor` | 36, 37 |
| **A3** | Billing | E3 (S1–S7) | billing package, `InvoicesView`, revenue report | 38, 39 |
| **A4** | Support and isolation | E4 (S1–S4) including the full support-session design in plan E4-S1, and E8-S3 (token must match host) | support package, the new `SupportSessionAuthenticationFilter`, the security filter chain in `SecurityConfig`, tickets and support-access screens, tenant support page | 40, 41 |
| **A5** | Data protection | E5 (S1–S7) | dataprotection package, new `/platform/data-protection` page, export/purge/retention jobs, tenant-side export and deletion requests | 42, 43 |
| **A6** | Observability, releases and security policy | E6 (S1–S3), E7 (S1–S4), E8 (S1, S2, S4) | usage, backup and audit-log controllers, release package, `SecurityPolicyView`, `UsageCountsView`, `BackupStatusView`, `AuditLogView`, `ReleasesView`, the backup agent script | 44, 45 |
| **A7** | Dashboards, navigation and alerts | E9 (S1–S6): the per-role dashboards, the notification bell UI and read API, and the final navigation entries | `PlatformOverviewView` and the new dashboard components, notification bell | 46 |
| **A8** | Production readiness and tests | E10 (S1–S8; S9 only if asked), E11 (Failsafe profile, `*IT` harness, Playwright suite, E2E-001, E2E-033, E2E-035 and the extra flows in plan section 13) | `docker/`, nginx config, `axios.ts` production branch, `KeycloakProperties`, `LoginTenantResolver` suffix handling, the new `S3FileStorage`, `api/pom.xml` test plugins, `app/e2e/` | 47, 48 |

**Order and dependencies (include each agent's part in its prompt):**

- **Wave 0:** A1 starts first. Everyone else may start their "understand" steps (section 6, steps 1–3) at once. They must pull A1's foundation commits before building on the shared services: the notification service, idempotency helper, audit payload and fixed DTOs.
- **Wave 1:** A2, A3, A4, A5 and A6 work in parallel once A1 has pushed its foundation.
- **A5:** uses the `FileStorage` interface and the local implementation. A8 adds `S3FileStorage` behind the same interface, and A5 must not depend on S3.
- **A7:** builds dashboard tiles only on endpoints that already exist on the branch. Where an endpoint isn't there yet, it shows the loading or empty state and records the gap in its report. It finishes after A2–A6.
- **A8:** does E10 at once. It writes each end-to-end test after the feature it covers has been pushed.

Also produce a **coverage table** listing every story ID from E0-B1 to E11, with exactly one owning agent for each, and confirm that nothing is unassigned or duplicated.

## 5. Git rules (copy them word for word into every prompt)

- All agents use the **same branch: `sprint-2-development`**. Never work on another branch and never create one, unless your prompt says otherwise.
- **Before you start:**
  - Run `git fetch origin`, then `git checkout sprint-2-development`, then `git pull --rebase origin sprint-2-development`.
- **Before every push:**
  1. Run the full checks for the layers you touched: backend `verify`, frontend lint, test and build, and the SQL bundle if you touched `sql/`.
  2. Run `git pull --rebase origin sprint-2-development`.
  3. Resolve any conflicts while keeping the other agents' work. Never delete or rewrite another agent's code to make a conflict go away.
  4. Run the checks again.
  5. Push with `git push origin sprint-2-development`.
- **Never** force-push, reset the remote, rebase published commits, or skip hooks with `--no-verify`.
- Commit in small pieces, one story or sub-story per commit. Message format: `<story-id>: <what and why>`, for example `E3-S3: require Product Owner approval before an invoice is sent`.
- Stage files by name. Never commit secrets, `.env*` files or `graphify-out/`.
- Don't edit the source specification (`docs/EDU360_V4.0_Final/`), `docs/PLATFORM_COMPLETION_PLAN.md`, `docs/GOVERNANCE_DECISION_LOG.md` or `CLAUDE.md`. If any of them looks wrong, report it instead. You may update only your own stories' rows in `PHASE1_PROGRESS_CHECKLIST.md`, and only with evidence.
- **Shared files you don't own** (see the table in section 4):
  - You may only **append** your own clearly separated entry, such as a route, a menu item, an action-code entry, an `AuditEventType` value, an exception mapping, or a line in `run_all.sh` or `provision-edu-app.ps1`.
  - Pull immediately before editing one of these files, and push soon after.
  - Do not reorder or reformat these files.
- **Migrations:** use only your reserved numbers. Each one needs its `r` rollback and `t` test files, and must be registered in `sql/run_all.sh` and `provision-edu-app.ps1`. Never edit another agent's migration or any migration already on the branch.
- If a pull brings in a change that breaks your build, and the change is not yours, do not "fix" it in that agent's area. Record it in your report under "Blocked by another workstream".

## 6. Working method (copy it into every prompt: understand first, then build)

Every agent works through these steps in order and does not skip ahead.

1. **Pull, then read** from your checkout: `CLAUDE.md` in full, `docs/V4.0_SPEC_SUMMARY.md` (use its navigation table), `docs/GOVERNANCE_DECISION_LOG.md` and `docs/PLATFORM_COMPLETION_PLAN.md`, especially your own stories.
2. **Understand the code:**
   - Open every file the stories touch, and record what exists today: endpoints, services, entities, tables, DTOs, screens and tests.
   - Use `graphify` first if `graphify-out/` exists.
   - Never assume something exists without opening it.
3. **Check the documents:** for each story, open the deliverable sections it cites (D1 to D10, SQL, OpenAPI) and confirm the rule, action code, table and screen states.
4. **Write the implementation brief** using the nine headings in `CLAUDE.md`, including a Mermaid diagram and a wireframe for every new screen. **Stop and wait for my approval** before changing any file.
5. **Build** story by story, in the order given. If you discover something that changes the plan, stop and tell me before continuing.
6. **Validate after each story:**
   - Run the tests and do the UI/UX check from `CLAUDE.md`: all screen states (loading, empty, validation error, server error, permission denied, read-only, success), both themes, short laptop heights, tokens only, and a grep for hard-coded hex colours.
   - Check that it works when logged in as the Product Owner and as one restricted platform role.
7. **Report at the end** with:
   - the stories done, and the files changed for each;
   - every acceptance criterion with pass or fail and its evidence (the real test output or an API call on the local stack — never assumed);
   - deviations and the reason for each;
   - anything blocked or open.

## 7. Required content of each agent prompt

Each prompt you write must contain these sections in this order:

1. **Mission:** two lines.
2. **Read first:** the exact files and sections.
3. **Decisions you must follow:** section 2, in full.
4. **Current code facts relevant to you:** from section 3, plus anything you know from the sources.
5. **Your stories:** each with its story ID, its user story, and **all its acceptance criteria word for word** from the plan. Then its backend, database, API, frontend and test parts exactly as the plan states them, including endpoints, action codes, error codes, statuses and table and column names.
6. **New API operations and migrations you own:** with your reserved numbers.
7. **Files you own, and shared files you may only append to.**
8. **Dependencies:** what you need from other agents, what they need from you, and the order.
9. **Out of scope:** the other agents' stories, listed by ID, so you don't build them.
10. **Git rules:** section 5, word for word.
11. **Working method:** section 6, word for word.
12. **Definition of done:**
    - every acceptance criterion is demonstrated;
    - backend `verify`, frontend lint, test and build pass;
    - the SQL bundle passes if `sql/` was touched;
    - the relevant `*IT` tests pass against the local stack;
    - UI checks are done;
    - the work is pushed to `sprint-2-development` without conflicts;
    - the report is delivered.
13. **Questions to raise instead of guessing:** the open decisions that touch this agent (OD-06, OD-15, OD-39, OD-42, OD-43, OD-63, OD-70, OD-73, OD-77, OD-79, OD-130, OD-137, P9 custom-domain proof, the production domain name), each with the provisional behaviour the plan already fixes.

## 8. Your answer format

1. The **coverage table** (story → agent).
2. The **dependency and order diagram** (Mermaid).
3. The **conflict hot-spot list**: the shared files and who appends to each.
4. **Prompt A1 to prompt A8**, each complete and ready to paste, each in its own fenced block so I can copy it.
5. **Questions for the Product Owner** (only conflicts the plan does not already settle).

Be complete and exact. A missing acceptance criterion, endpoint, error code, table, screen state or test means that agent will not finish 100%. Before you answer, check your output against the plan story by story.
