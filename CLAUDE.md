# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

EDU 360 is a school management application with two independently versioned halves in one repo:

- `app/` — React 19 + TypeScript + Vite frontend.
- `api/` — Java 21 + Spring Boot 4 backend (single monolith today, see "Architecture status" below).

`docs/EDU360_V4.0_Final/` contains the target-state product/architecture spec for a future multi-service V4.0 design — it is aspirational, not a description of the current code. Treat it as reference only; do not assume anything described there is implemented.

**Read `docs/V4.0_SPEC_SUMMARY.md` before opening any raw file under `docs/EDU360_V4.0_Final/`.**
It is a full digest of all 10 deliverables in that package (product spec, governance register,
roles/permissions, database architecture + SQL bundle, API spec, QA traceability, UX/workflow,
security compliance, design system, interactive prototype) — every file in there was read in full,
not sampled, including the 1.4MB OpenAPI yaml, the 2.1MB UX workflow HTML, the complete SQL
migration bundle, and every xlsx sheet (including historical change logs). It ends with a
navigation table mapping "if your question is about X, open file Y, sheet/section Z" — use that
table instead of re-reading the raw spec package from scratch. It also tracks several real
numeric discrepancies between the spec's own sources (e.g. a 79-vs-97 test-count disagreement, a
206-vs-208-table count disagreement, a 29-vs-30 module-count disagreement within the same file) —
don't silently resolve those if asked for exact figures; cite them as open discrepancies.

## How to work in this repository (mandatory)

### Act as a senior team, not a code generator

On every task, work as a **well-experienced senior full-stack developer** (10+ years, Java/Spring
Boot, React/TypeScript, PostgreSQL), and review your own plan through these lenses as well:

- **Project / product manager** — is this in scope and in the right phase? Which requirement,
  workflow and role does it serve? Does it depend on an open decision? What else does it affect?
- **Technical architect** — does it fit the current architecture *and* move toward (not away from)
  the V4.0 target: layering, data model, API contract, tenancy, security, migration approach?
- **Senior UI/UX designer** — for anything visible (see "UI/UX standard" below).
- **QA / security reviewer** — how will it be tested, and what could go wrong (permissions, data
  leaks, self-approval, validation, error states)?

Think before coding, prefer small safe changes, never guess at business rules, and say so plainly
when something is unclear, risky or conflicts with the documents.

### Required workflow for every implementation request

This applies to every request that builds, changes or extends a feature, screen, endpoint, table,
permission or workflow — in `api/`, `app/`, or the database. It does **not** block routine
reads/greps, explanations, running existing tests, or answering questions.

**Step 1 — Analyse the current code.** Read the relevant parts of `api/` and `app/` and record what
exists today: controllers/endpoints, services, entities/tables, roles and `@PreAuthorize` rules,
DTOs, frontend pages/routes/components, design-system usage, and tests. Do not assume — open the
files.

**Step 2 — Analyse the documents.** Start with `docs/V4.0_SPEC_SUMMARY.md` and its navigation table,
then open the raw `docs/EDU360_V4.0_Final/` files it points to for detail. Check every area the
request touches:

| Area | Where to look (deliverable) | What to confirm |
|---|---|---|
| Product rules and workflow | 01 Master Product Specification (Part I V4-x, Part II incl. §7 approval chains, §11–14 role workflows, §26 action list) | Business rules, approval chain, who does what, phase |
| Actions and decisions | 02 Product Governance & Action Register | Action codes, "limited to", "never allowed", open decisions (OD-xx) that block the work |
| Roles and permissions | 03 Master Roles & Permission Specification (matrix, Role Catalogue, Roll-down Map/Rules, Roles & Permissions Screens) | Which roles may do it, scope, sensitive actions, separation of duties, role roll-down (V4-PD-01) |
| Database | 04 Database & Technical Architecture + `sql/` bundle | Tables, columns, constraints, RLS, triggers, the DB test that covers the rule |
| API | 05 API & Integration Specification + OpenAPI yaml | Operation, path, request/response schema, error codes, idempotency, If-Match |
| Tests | 06 Requirements Traceability & QA | Requirement IDs, existing test designs (DB-xx, E2E-xx) to implement |
| Workflow and screens | 07 Unified Workflow & UX Specification | The journey, screen states (loading, empty, error, denied, read-only) |
| Security | 08 Security, Compliance & Final Validation | Controls, findings, accepted risks |
| Visual design | 09 Master Design System | Tokens, components, patterns |
| Screen reference | 10 Interactive Prototype | The existing screen and interaction for this role |

**Step 3 — Validate before proposing.** Compare code against documents and validate the plan from
the project-manager, architect, UX and QA/security viewpoints above. Flag anything the documents do
not cover, anything ambiguous, any open decision that blocks it, and any conflict between the
documents themselves (never silently resolve a conflict — cite both sources).

**Step 4 — Present an implementation brief and STOP.** Before editing any file, show the user this
brief, using these exact headings:

1. **Request** — what was asked, in one or two lines.
2. **What exists in the code today** — files, endpoints, tables, screens, tests (with paths).
3. **What the documents say** — the rules, roles, actions, API, tables and screens that apply, each
   with its reference (e.g. "D1 Part II §7.1", "D3 Roll-down Map", "D5 `setRolldownOverride`").
4. **Gap** — what is missing or different between the code and the documents.
5. **What I will implement** — per layer: database, backend, API, frontend; the exact files to
   create or change; and how closely it follows the V4.0 target or deliberately deviates (and why).
6. **Out of scope / not doing** — what this change will not touch.
7. **Risks, open decisions and questions** — anything that needs the user's choice.
8. **How I will validate it** — tests to add or run, UI checks, document re-check.
9. **Visualisation** — at least one diagram (Mermaid) that makes the change clear, e.g. a
   before → after flow, a sequence diagram (UI → API → service → DB), an ER diagram of new or
   changed tables, or a screen-flow for UI work. Add a simple ASCII or Mermaid wireframe for any new
   or changed screen.

Then **wait for explicit approval** ("go ahead", "approved", or equivalent). Do not create, edit or
delete files until the user approves. If the user changes the plan, update the brief and ask again.

**Step 5 — Build.** Implement exactly the approved plan, in small steps. If you discover something
that changes the plan (a missing rule, a conflict, a bigger impact), stop and tell the user before
continuing.

**Step 6 — Validate after building and report.** Run the relevant tests (`npm test`, `npm run
lint`, `npm run build`, `./mvnw clean verify`, single tests as needed), re-check the result against
the documents from Step 2, and run the UI/UX checks below for any visible change. Finish with a
short report: what changed (files), how it matches the documents, test results (real output, never
assumed), and anything still open. Never claim something works without evidence.

### Front-end changes — always check design, prototype, workflow, roles and database

If a change touches anything in `app/` (page, component, route, form, table, state, text):

1. **Design system (D9) and `app/src/design-system/`** — use existing tokens and components only;
   no new colours, fonts, spacing or patterns unless approved.
2. **Interactive prototype (D10)** — find the matching screen for that role and follow its layout,
   content and interactions; if no screen exists, say so in the brief and propose one that follows
   the same patterns.
3. **Workflow and screen states (D7)** — the user journey and the required states: loading, empty,
   validation error, server error, permission denied, read-only, success.
4. **Roles and permissions (D3, D2)** — which roles see the screen and each button; hide what a role
   may not do, but the server must still enforce it.
5. **API (D5)** — the operation and schema the screen calls; all calls through the shared `axios`
   instance; protected routes under `PrivateRoute`.
6. **Database (D4)** — the tables and rules behind the data shown, so the screen never promises
   something the database refuses (e.g. self-approval, locked terms, immutable receipts).

Then apply the "UI/UX standard" below before calling it done.

### Back-end and database changes — always check the contract

If a change touches `api/` or the schema: confirm the action code and allowed roles (D2/D3), the
API operation, schema and error codes (D5), the tables, constraints, RLS and the SQL test that
covers the rule (D4 + `sql/`), the approval chain and business rules (D1), and the test designs
(D6). Keep the feature-package layout and `GlobalExceptionHandler` conventions. Do not add a second
migration mechanism (see "Database migrations" below).

**`docs/KEYCLOAK_MIGRATION_PLAN.md`** records the move from local cookie-session JWT auth to
Keycloak OIDC. Phases 0–2 are implemented, and its realm-per-tenant + identity-link design was
ratified by OD-10/OD-80 (`docs/GOVERNANCE_DECISION_LOG.md`, 2026-11-04). Read both before touching
auth architecture.

### Required workflow for every implementation request

Before writing or changing any code in `api/` or `app/`, for every request that implies building,
changing, or extending a feature:

1. **Check it against `docs/V4.0_SPEC_SUMMARY.md`** (and the raw `docs/EDU360_V4.0_Final/` files
   it points to, via its navigation table, if more detail is needed) to see whether the V4.0 spec
   defines how this should work — the relevant role(s), permissions, API shape, schema, business
   rules, or approval chain.
2. **State a short plan before touching code**: what the spec says (or "the spec doesn't cover
   this / this conflicts with the spec, here's why"), and exactly what you intend to implement —
   which files, which approach, and how closely it follows or deliberately deviates from the V4.0
   target.
3. **Wait for explicit go-ahead** before implementing. Don't start editing files until the plan is
   confirmed.

This applies to feature work and non-trivial changes. It does not block routine
reads/greps/explanations, running existing tests, or answering questions.

### UI/UX standard

For any screen, component, or visual change: **act as a senior UI/UX designer and validate the
result as one would** — don't just make it technically work, then move on. Concretely, before
calling any UI change done, validate it against each of these, explicitly:
- Fits common viewport sizes without unnecessary scrolling (check short/laptop heights, not just
  desktop).
- Spacing/rhythm is consistent with nearby screens, not invented ad hoc per component.
- Interactive elements have real affordance and all their states (hover, focus, disabled, loading,
  error) — not just the default state.
- Contrast and visual hierarchy hold up in both light and dark theme, not just whichever one was
  eyeballed during the edit.
- Every color/font actually comes from the design-system tokens (`app/src/design-system/`) —
  grep for hardcoded hex values / font names before calling it finished.
- Matches the intended pattern in `docs/V4.0_SPEC_SUMMARY.md` §6 (and the Master Design System /
  Interactive Prototype it points to) rather than inventing a new one.

Flag UX problems proactively — the way the login-page scroll issue was caught — instead of only
fixing what's explicitly pointed out. This validation pass is part of finishing the work, not an
optional follow-up.

The headline fact worth remembering without opening anything: V4.0 specifies a multi-tenant SaaS
with 71 roles, a 278-action permission matrix (236 of which are actually wired to an API
operation), a 206-table RLS-enforced Postgres schema (97/97 SQL tests passing against a real
throwaway Postgres cluster — the one part of the package that's actually been executed), a
408-operation/313-path OpenAPI contract, and 29–30 functional modules across 60 approval chains.
The code has started implementing this (the `sql/` bundle, Keycloak realms, RLS routing, the action
evaluator, about 70 endpoints), but only a small slice. The spec package's own statement that no
application code exists against it is out of date; still treat the spec as a target, and check the
code for what is actually built.

### Use the graphify graph first to save tokens

`graphify-out/` holds a knowledge graph of this repository (about 4,500 nodes: classes, methods,
files and the links between them). Query it **before** broad greps or reading many files, to find
where something lives and what it touches. Then open only the files it points to.

```bash
graphify explain "PlatformDataProtectionService"    # a class or file and its connections
graphify path "TenantsView" "TenantManagementService" # how two things connect
graphify query "how is the tenant chosen at login" --budget 1500   # broad question, capped answer
```

- **Read `graphify-out/GRAPH_REPORT.md` first** for an overview of the main areas instead of
  exploring the tree file by file.
- **Check it's current:** the report names the commit it was built from. If `git rev-parse HEAD`
  differs, or there are many uncommitted changes, run `graphify update .` first. It costs no API
  tokens.
- **Ambiguous name:** if `explain` reports several matches, retry with the full node ID it prints.
- **The graph locates code; it isn't evidence.** Before reporting status, stating that a bug exists or
  is fixed, or editing anything, read the actual lines. Inferred edges (about 9%) can be wrong.
- **Don't use it for** document content in `docs/EDU360_V4.0_Final/` (use `docs/V4.0_SPEC_SUMMARY.md`),
  test results, or live behaviour.
- `graphify-out/` is generated, so don't commit it.

## Commands

### Full local stack (repo root, PowerShell)

The backend cannot run on its own: dev needs Postgres, Keycloak and LocalStack. `LOCAL_DEV.md` has
the details, tenant hostnames and known quirks.

```powershell
.\docker\dev-hosts-setup.ps1   # one-time, as Administrator: adds the *.edu360.local hosts entries
.\dev-up.ps1                   # Docker services, SQL bundle + DB roles, backend and frontend windows
.\dev-status.ps1               # first stop when something is down
.\dev-logs.ps1 backend -Follow # also: postgres | keycloak | localstack | frontend
.\dev-restart.ps1              # dev-down + dev-up
.\dev-down.ps1                 # stops everything; the Postgres volume survives
```

Ports: Vite 5173, API 8080, Postgres 5433, Keycloak 8081, LocalStack 4566.

Open the app at `http://abc.edu360.local:5173`, `http://xyz.edu360.local:5173` or
`http://platform.edu360.local:5173`. Login from `localhost:5173` fails with "Unknown sign-in
address" because the tenant comes from the `Host` header.

### Frontend (`app/`)

```bash
npm ci                # install (matches CI)
npm run dev           # Vite dev server on port 5173
npm run build         # tsc -b type-check, then vite build (the CI build gate)
npm run lint          # ESLint
npm run format:check  # Prettier check (not a CI gate)
npm test              # vitest run: full suite, once
npm run test:watch    # vitest watch mode
npm run test:coverage # vitest run --coverage
```

Run a single test file: `npx vitest run src/path/to/file.test.tsx`.

### Backend (`api/`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # dev profile (requires SPRING_PROFILES_ACTIVE=dev); needs the stack above and the DB passwords that dev-up.ps1 exports
./mvnw clean verify      # build + unit tests (CI gate); H2 + `test` profile, no external services
./mvnw -Dtest=ClassName test   # single test class
```

On Windows use `.\mvnw.cmd`.

`*IT` classes (`TenantIssuerAuthenticationManagerResolverIT`,
`TenantRoutingDataSourceInstitutionScopeIT`) are the only tests that exercise real RLS and real
Keycloak tokens. **`verify` does not run them**: Failsafe isn't configured and Surefire's default
patterns skip `*IT`. Run them by name with `-Dtest=...IT test` while the local stack is up; they
connect to `127.0.0.1:5433` as the `edu360` superuser, not through Testcontainers. Run them after
any change to tenant routing, token validation or RLS, because a green `verify` says nothing about
those.

### Database (`sql/`)

```bash
OUT=/tmp/run_results.txt PSQL="psql -h localhost -U postgres" ./sql/run_all.sh
```

This builds and tests the bundle in two throwaway databases (`edu360_v4_test`, `edu360_v4_ref`,
dropped first) and needs PostgreSQL 15+. Always pass `OUT`: the default overwrites the tracked
`sql/run_results.txt`. The committed copy of that file currently holds only a header line, so it is
not evidence that the tests passed.

### CI parity

`.github/workflows/ci.yml` is the source of truth for what must pass: frontend (`npm ci`, lint, full
vitest suite, `npm run build`), backend (`mvn verify`), npm audit (high) + OWASP dependency-check
(CVSS 7) + gitleaks, PR dependency review, CodeQL, and `sql/run_all.sh` against a Postgres 16
service.

## Architecture

### Multi-tenancy: the thread that runs through everything

A single Spring Boot app serves many tenants. Each tenant has its own Keycloak realm, plus one
platform realm (`edu360-platform`). A request flows like this:

1. **Login** (`auth/AuthController` → `tenant/LoginTenantResolver`): the realm comes from the `Host`
   header, never from a request field. The backend calls Keycloak (`config/keycloak/`) and returns
   access and refresh tokens in the response body. There is no sign-up, and this app never stores
   passwords.
2. **Every later request** (`config/TenantIssuerAuthenticationManagerResolver`): the bearer token's
   unverified `iss` claim only selects a validator, built from a URL the class reconstructs itself.
   Only after signature verification does it set `TenantContext` and build the principal.
3. **Principals are two separate types by design**: `TenantPrincipal` and `PlatformPrincipal`. A
   platform request never sets `TenantContext`.
4. **Every DB connection** (`tenant/TenantRoutingDataSource`): chooses the shared pool (`SHARED`
   tenants) or a lazily opened per-tenant pool (`ISOLATED`, credentials from Secrets Manager /
   LocalStack). On each borrow it sets or resets the session GUCs `app.tenant_id`,
   `app.institution_scope` and `app.institution_ids`, which the RLS policies in
   `sql/03_bindings.sql` read.

Fail-closed consequences to design around:
- With no tenant context, RLS hides every `app.*` row, including from platform admins.
- `TenantUserLookup` uses the overload that leaves institution IDs empty, and nothing in production
  code passes real ones yet (Foundation Item 7). Institution-scoped rows are therefore invisible
  under RLS for now. D4 §2.2 reserves `app.institution_scope = 'ALL'` for tenant-wide roles and uses a
  list of institution IDs for everyone else; don't set `ALL` as a blanket fix.
- The app connects as `edu_app`, which has no `BYPASSRLS` and is read-only on `platform.*`. New
  platform-schema writes need `edu_platform`, not a wider `edu_app` grant.

### Backend (`api/src/main/java/com/edu360/api/`)

Feature packages use `Controller` → `Service` → `Repository` → entity, with a `dto/` subpackage:
`auth`, `identity`, `rbac`, `institution`, `selfservice`, `files`, `audit` (tenant side);
`platform`, `platformops` (platform side); `tenant`, `config`, `exception` (cross-cutting). Tenant
endpoints live under `/api/tenants/{tenantId}/...`, platform endpoints under `/api/platform/...`,
and the caller's own data under `/api/me/...`.

**Authorization is imperative, not annotation-based; there is no `@PreAuthorize` in this codebase.**
- Tenant actions call `rbac/PermissionEvaluatorService.authorize(principal, "ACTION_CODE", scope,
  target)` in the controller. Action codes come from `ref.action_catalog`, and grants are read from
  `app.role_action` at runtime. Unknown actions and self-approval are denied. Scope matching is
  deliberately **exact**: a TENANT assignment does not grant INSTITUTION- or DEPARTMENT-scoped
  actions. Two ACTIVE assignments at the same scope deny rather than merge.
- Platform actions call `platformops/PlatformPermissionService.authorize(...)`, a hard-coded
  action-to-role map; add new platform actions there.
- `config/MustChangePasswordFilter` blocks most endpoints until a forced password change is done.
  The flag is a Keycloak attribute surfaced as a JWT claim, not a DB column.
- CSRF is disabled because auth uses explicit bearer headers rather than cookies. Revisit this if
  tokens ever move into cookies.

**Errors** go through `exception/GlobalExceptionHandler` as `{timestamp, status, error}`, with
`fieldErrors` added on validation failures. Throw the existing types: `ResourceNotFoundException`
(404), `DuplicateResourceException` (409), `UnauthorizedException` / `AccessDeniedException` (403),
`InvalidCredentialsException` (401), `IllegalArgumentException` (400),
`KeycloakProvisioningException` (503). Anything else becomes a logged 500 with a generic message.

**Profiles**: `dev` is the default. `test` is activated by `src/test/resources/application.properties`
and uses H2 with `ddl-auto=none`, so the `app.*`/`platform.*`/`ref.*` tables don't exist there. Unit
tests therefore mock repositories, and RLS or SQL behaviour needs an `*IT` or `sql/` test. `prod`
needs `SPRING_DATASOURCE_{URL,USERNAME,PASSWORD}` and `KEYCLOAK_{ISSUER_URI,BASE_URL,REALM,
ADMIN_CLIENT_ID,ADMIN_CLIENT_SECRET,FRONTEND_CLIENT_ID,PLATFORM_REALM}` from the environment.
`DevAdminSeeder` and `DevRoleAssignmentSeeder` are dev-only seed data.

**Database migrations**: `sql/` is the only schema authority. Hibernate never creates or alters
tables (`ddl-auto=none` in dev and test, `validate` in prod), and entities map onto that schema.
Flyway/Liquibase haven't been chosen, so don't introduce a second migration mechanism without
reconciling it with `sql/run_all.sh` first. `docker/postgres/provision-edu-app.ps1` applies the
bundle only if it isn't already applied.

**Not yet the V4.0 target**: this is still one deployable, not the multi-service design, and
`LocalFileStorage` is a deliberate interim stand-in for an object store. Don't assume services
described in `docs/EDU360_V4.0_Final/` exist in code.

### Frontend (`app/src/`)

- `features/<name>/` — `pages/` plus, where needed, `api/` (typed service files that mirror backend
  DTOs) and `hooks/`. `role-previews/` renders role dashboards from a static catalog.
- `routes/Routes.tsx` — all route wiring and role gates. `PrivateRoute` also enforces the forced
  password change. `UniversityAdminRoute` always redirects (fail-closed) until the browser has an
  authoritative tenant permission context, so `/university/*` is unreachable today.
- `store/slices/authSlice.ts` — the current user. Exactly one of (`tenantId` + `userType`) or
  `platformRole` is set; `roleCode`/`roleName` are null until a role is assigned.
- `services/`:
  - `axios.ts` — the shared instance. It derives the API host from the page's hostname so tenant
    resolution works, attaches the bearer token, and refuses requests to other origins.
  - `tokenStorage.ts` — access and refresh tokens in `sessionStorage` (per tab, readable by JS; not
    an XSS defence).
  - `axiosInterceptor.ts` — on a 401, runs one shared refresh and retries; if that fails, clears the
    session and redirects to `/login`.
  - `bootstrapAuth.tsx` / `authManager.tsx` — startup hydration, login and logout.
  - `inactivityTracker.ts` (30-minute idle logout, started from `AppLayout`), `forceLogout.ts`,
    `logoutNotice.ts`.
- `design-system/` — plain CSS tokens and components (`tokens.css`, `components.css`) layered under
  Tailwind. `components/` holds reusable domain-free primitives; feature UI stays in its feature.

Every authenticated call must use the shared `axios` instance, and every protected route goes under
`PrivateRoute`. Client-side gates are UX only; the server enforces permissions.

**Before any production deployment, `docker/PRODUCTION_TENANT_ROUTING.txt` must be completed.** It is
not built yet, and without it no tenant can log in on a real domain. Creating a tenant must need no
frontend change: the tenant comes from the web address. Read it before changing tenant resolution,
`axios.ts`, CORS or the reverse proxy, and raise it whenever deployment or go-live is discussed.

Frontend gotchas:
- **Don't copy `app/.env.example` as-is.** It sets `VITE_API_BASE_URL=http://localhost:8080/api`,
  which overrides the hostname derivation and breaks tenant login locally. `app/.env` leaves it
  unset on purpose.
- `vitest.config.ts` takes precedence over the `test` block in `vite.config.ts` and doesn't define
  the `@` alias, so `@/` imports would fail under test. Nothing uses them today.

### Which docs to trust

- **Current**: `LOCAL_DEV.md`; `docs/GOVERNANCE_DECISION_LOG.md`, which overrides the original
  governance register for decisions dated after it (e.g. OD-03, OD-10, OD-80 approved 2026-11-04);
  `docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md`, the "Addendum rule N" cited in code comments;
  `docs/REQUIREMENTS_TRACEABILITY_AND_GAP_UPDATE.md`.
- **Partly stale, so check against the code**: `api/README.md` (H2 in dev, old feature packages),
  `docs/NEW_DEVELOPER_GUIDE.md` (teacher/student app, H2, cookie auth), and
  `docs/CI_CD_ENGINEERING_STANDARD.md` §3 (says the SQL bundle isn't committed).
- Source files carry long WHAT/HOW/WHY class comments that record real decisions. Read them before
  changing `tenant/`, `rbac/` or `config/`.
