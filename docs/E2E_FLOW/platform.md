# Platform side

The platform side is for EDU 360's own staff who run the SaaS business — not for any
school or college. They work in one shared "platform" realm, separate from every tenant's
own realm.

## 1. Landing, sign-in and dashboard for this audience

**✅ Built today.** A platform user signs in from `http://platform.edu360.localhost:5173`
(or the production platform host). The frontend sends the email/password to
`POST /api/auth/login` (`api/src/main/java/com/edu360/api/auth/AuthController.java:58`).
Which realm the backend checks comes only from the browser's `Host` header
(`tenant/LoginTenantResolver`, per this repo's `CLAUDE.md`) — never from anything in the
login form. Keycloak (the platform realm `edu360-platform`) checks the password and
issues access/refresh tokens; `AuthController` relays them back in the response body. If
MFA is enrolled, the same endpoint can instead return an `MFA_ENROLMENT_REQUIRED` type
answer (`AuthController.java:62-69`) instead of tokens.

Every later request's bearer token is re-verified by
`config/TenantIssuerAuthenticationManagerResolver`: the token's own `iss` claim only picks
which realm's key to check signature against; only after that check does the request
become a `PlatformPrincipal`
(`api/src/main/java/com/edu360/api/config/PlatformPrincipal.java`). A platform request
never sets `TenantContext` — so RLS automatically hides every `app.*` row from it (fail
closed, by design).

Once signed in, the dashboard routes live under `/platform/...` in
`app/src/routes/Routes.tsx:243-289`: Overview (`/platform`), Tenants
(`/platform/tenants`), Plans (`/platform/plans`), Invoices (`/platform/invoices`),
Releases (`/platform/releases`), Reports (`/platform/reports`), Security Policy
(`/platform/security-policy`), Tickets (`/platform/tickets`), Users
(`/platform/users`), Roles (`/platform/roles`), Role Templates
(`/platform/role-templates`), Support Access (`/platform/support`), Audit Log
(`/platform/audit`), Usage Counts (`/platform/usage-counts`), Backup Status
(`/platform/backup-status`), Integration Types (`/platform/integration-types`), Data
Protection (`/platform/data-protection`), Landing CMS (`/platform/landing`). Each is
wrapped in `PrivateRoute` (forces a signed-in, non-forced-password-change session).

🔜 **V4.0 target:** the platform side is specced to show daily-cockpit style dashboards per
role (D7/D10 "Screen 1: Platform Overview & Daily Cockpit"), and the UX package names 16
distinct platform screens (D10 nav list) — more granular dashboards than exist today, and
tied to the full 6-role platform catalogue below, not just whichever two roles a given
build has wired through.

## 2. What exists in code today

Platform authorization never uses `@PreAuthorize`. Every controller method calls
`PlatformPermissionService.authorize(principal, "ACTION_CODE")`
(`api/src/main/java/com/edu360/api/platformops/PlatformPermissionService.java`). That
service is a **hard-coded Java map** (`ALLOWED_ROLES`, lines 31-63) from action code to the
`PlatformRole` enum values allowed to hold it — not a database table. Unknown action codes
and principals without a matching role are denied (`authorize`, lines 81-96). Every denial
is itself audited as a `DENIED` event before the exception is thrown (`deny(...)`, lines
73-79) — this is the platform-side version of "every denied attempt is logged."

Representative create/update/lifecycle flows, verified by reading
`api/src/main/java/com/edu360/api/platformops/PlatformTenantController.java`
(`@RequestMapping({"/v1/platform", "/api/platform"})`, line 47):

| Action | Endpoint | Controller line | Permission check |
|---|---|---|---|
| Create a subscription plan | `POST /api/platform/plans` | line 65 | `authorize(principal, "PLAN_MANAGE")` (line 72) — Product Owner or Platform Admin only |
| Update a plan | `PUT /api/platform/plans/{planId}` | line 87 | `PLAN_MANAGE` (line 96) |
| Suspend a tenant | `POST /api/platform/tenants/{tenantId}/suspension` | line 102 | `TENANT_SUSPEND_ARCHIVE` (line 111) — **Product Owner only**, `OWNER` set |
| Archive a tenant | `POST /api/platform/tenants/{tenantId}/archive` | line 132 | `TENANT_SUSPEND_ARCHIVE` (line 141) |
| Reactivate a tenant | `POST /api/platform/tenants/{tenantId}/reactivation` | line 162 | `TENANT_SUSPEND_ARCHIVE` (line 169) |
| Update tenant settings | `PUT` under `/tenants/{tenantId}/settings` | around line 196 | `TENANT_SETTINGS` (Product Owner + Platform Admin) |
| Create a tenant (onboard a school) | `POST /api/platform/tenants` | line 202 | `TENANT_CREATE` (line 209) — Product Owner or Platform Admin |
| Retry a stuck tenant provisioning run | `POST /api/platform/tenants/{tenantId}/provisioning/retry` | line 238 | `TENANT_CREATE` (line 245) |
| Reset the tenant's first admin password | `POST /api/platform/tenants/{tenantId}/first-admin/password-reset` | line 253 | `TENANT_SETTINGS` (line 259) |
| List tenants | `GET /api/platform/tenants` | line 265 | read, gated by role/`authorizeAny` |
| Activate a tenant | `POST /api/platform/tenants/{tenantId}/activation` | line 283 | `TENANT_ACTIVATE` (line 292) — **Product Owner only** |

Other platform controllers follow the same imperative pattern, each calling
`platformPermissionService.authorize(...)` with its own action code:
`PlatformUserController.java` (platform staff accounts — `PLATFORM_USER_MANAGE`, Product
Owner only), `PlatformBillingController.java` (`INVOICE_MANAGE`/`DISCOUNT_APPLY` — Billing
Manager, with `DISCOUNT_APPLY` explicitly denied to Billing Manager per the comment at
`PlatformPermissionService.java:45`, "Rule [F-4]"), `PlatformSecurityPolicyController.java`
(`SECURITY_POLICY_SET`, Product Owner only), `PlatformSupportController.java` /
`TenantSupportController.java` (support ticket + read-only, time-boxed support-access
sessions — `SUPPORT_TICKET_MANAGE`, `SUPPORT_ACCESS_USE`), `PlatformAuditLogController.java`
(`PLATFORM_AUDIT_VIEW`), `PlatformDataProtectionController.java` /
`PlatformExportController.java` / `PlatformDeletionRequestController.java` (export/
deletion requests — `TENANT_EXPORT_REQUEST`/`TENANT_DELETION_REQUEST` raised by Product
Owner, but **approved only by the Data Protection Officer**, `TENANT_EXPORT_APPROVE` /
`TENANT_DELETION_APPROVE` / `RETENTION_CHANGE_APPROVE`, all mapped to the `DPO` set alone
at `PlatformPermissionService.java:59-61` — a deliberate separation-of-duties design so the
person who requests an export/deletion can never also approve it).

**Support session isolation (fail-closed by construction):** a delegated read-only
support session is handled inside `PermissionEvaluatorService.can(...)` itself
(`api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java:177-190`): if the
authenticated principal is a `SupportPrincipal`, the tenant IDs must match exactly
(`SUPPORT_TENANT_MISMATCH` otherwise), the institution scope must match if one was granted
(`SUPPORT_INSTITUTION_MISMATCH`), and only actions flagged `isSupportReadable()` on
`ref.action_catalog` are ever allowed — anything else is `SUPPORT_READ_ONLY`. This is the
real, working implementation of the spec's "platform/tenant isolation with a time-boxed
read-only exception" rule (see §4 of this file).

Login/session/auth plumbing is shared across platform and tenant principals in
`AuthController.java` (see §1 above) — `me`, `logout`, `change-password`, MFA enrolment and
recovery codes all branch on whether `authentication.getPrincipal()` is a
`TenantPrincipal` or a `PlatformPrincipal` (`AuthController.java:140-146, 159-163,
179-183`).

## 3. What the V4.0 spec plans

- **6 platform roles** (D3 Role Catalogue, §2 of `V4.0_SPEC_SUMMARY.md`): Product Owner,
  Platform Admin, Billing Manager, Support Manager, Release Manager, Data Protection
  Officer. The code's `PlatformRole` enum and `PlatformPermissionService.ALLOWED_ROLES`
  map already name and distinguish all six — this part of the target is actually built,
  which is unusual for this codebase; most of V4.0's breadth elsewhere is not.
- **Platform/tenant isolation (`GUARD_PLATFORM_ISOLATION`, D2/D3):** platform roles never
  see tenant data except through a tenant-granted, ticket-bound, 24-hour-capped read-only
  support session. **✅ Confirmed, built.** The 24-hour cap is a real, enforced constraint,
  not just a spec aspiration:
  `api/src/main/java/com/edu360/api/platformops/support/dto/GrantSupportAccessRequest.java:12`
  declares `durationHours` with `@Min(1) @Max(24)` at the DTO-validation layer, and
  `PlatformSupportService.java:200-201` re-checks it server-side independent of bean
  validation (`if (req.durationHours() < 1 || req.durationHours() > 24) throw new
  BusinessRuleViolationException(...)`), citing "Rule PQ-21 /
  ck_support_access_grant_r1". Line 205 then computes
  `expiresAt = now.plus(Duration.ofHours(req.durationHours()))` and stores it on the grant.
  So the cap is enforced twice (annotation + explicit re-check) before any grant row is
  ever created — a solid double-gate, not a single soft limit.
- **Role roll-down (V4-PD-01)** applies only inside a tenant (Management, Principal,
  Registrar, etc. — see `tenant-admin.md`). It is explicitly a **never-roll-down** area for
  platform roles (`docs/V4.0_SPEC_SUMMARY.md` §2, "never-roll-down list ... platform
  roles").
- **Delegation at the platform level:** `APPROVAL_DELEGATE` is mapped only to the Data
  Protection Officer (`PlatformPermissionService.java:62`) — matching the spec's rule that
  export/deletion/retention approvals are a single named-holder chain, not a general
  delegation pool (D2 action 198, "Locked; named holder only," also enforced tenant-side as
  `LOCKED_ACTIONS` in `PermissionEvaluatorService.java:81-84`).
- **16 platform screens** named in D10 (Overview, Tenants Directory, Subscription Plans,
  Invoices & Billing, Support Tickets Desk, Support Access, Platform Staff Directory, Live
  Platform Roles & Step-In Simulator, Executive Reports, Usage Analytics, Disaster Recovery
  & Backup Health, Integration Types, Audit Trail, and others per the graph's "Screen 1" …
  "Screen 16" hub nodes). The frontend route list in §1 covers the great majority of these
  names already — the gap here is depth of each screen's content and workflow states (D7),
  not missing routes.
- **408-operation OpenAPI contract (D5)** describes a far larger platform API surface
  (tenant lifecycle, billing, support, releases, integrations, with idempotency and
  `x-error-codes` per operation) than the ~20 platform endpoints actually wired today. Do
  not assume an operation named in the OpenAPI yaml exists in `api/` without checking its
  controller directly, the same rule this whole document set follows.

## 4. Role restrictions

### Most senior/complex role: Product Owner

The Product Owner is the only platform role mapped to nearly every action in
`PlatformPermissionService.ALLOWED_ROLES` (`PlatformPermissionService.java:31-63`) — it is
in `OWNER` or `OWNER_AND_ADMIN` for every entry except the Billing-Manager-only and
Support-Manager-only rows, and except the four actions locked to the Data Protection
Officer.

**What it cannot do, and why, with the exact mechanism:**
- **Cannot approve a tenant data export, deletion, or retention-period change it raised
  itself.** `TENANT_EXPORT_APPROVE`, `TENANT_DELETION_APPROVE`, and
  `RETENTION_CHANGE_APPROVE` map only to `DPO` (`PlatformPermissionService.java:59-61`).
  Even though the Product Owner can call `TENANT_EXPORT_REQUEST`/`TENANT_DELETION_REQUEST`
  (`OWNER`, lines 54-55), `authorize(...)` for the approve actions checks `holds(...)`
  against that same map and simply returns false for a Product Owner principal — there is
  no code path where the Product Owner's role is substituted in. If a Product Owner tries
  it anyway, `authorize` throws `UnauthorizedException` (line 89-91) and the attempt is
  recorded as a `DENIED` audit event first (`deny(...)`, lines 73-79). This is a real,
  enforced separation of duties, not a UI-only hint.
- **Cannot access tenant (`app.*`) data directly, ever**, except through the same
  support-session mechanism everyone else uses. A `PlatformPrincipal` never sets
  `TenantContext`, so Postgres RLS (`app.* FORCE ROW LEVEL SECURITY`, per D4/§3 of
  `V4.0_SPEC_SUMMARY.md`) hides every row regardless of role. This is a database-level
  fail-closed rule, not something `PlatformPermissionService` could override even if its
  Java map were changed.
- **Cannot act on a handed-over action without stepping in first.** If another platform
  user has been handed an action the Product Owner also holds,
  `requiresStepIn(...)` (`PlatformPermissionService.java:155-159`) forces a `403` with a
  `STEP_IN_REQUIRED` message telling them to use the Platform Roles "step-in" screen with a
  reason, even though the Product Owner nominally holds every action. This exists so the
  Product Owner cannot silently bypass a deliberate handover.
- **Still subject to the generic fail-closed rules:** an unrecognized action code is always
  denied (`authorize`, line 82) regardless of role, and a `null` principal is always denied
  (line 85) — there is no "superuser bypass" branch anywhere in this class.

**What happens if the Product Owner tries a denied action:** a `403 Unauthorized` via
`UnauthorizedException`, handled uniformly by
`api/src/main/java/com/edu360/api/exception/GlobalExceptionHandler.java` into
`{timestamp, status, error}`, and a `DENIED` audit row is written first
(`PlatformAuditWriter.recordDenied`, referenced at `PlatformPermissionService.java:76`).

### Delta for every other platform role

All other platform roles are checked through the exact same
`PlatformPermissionService.ALLOWED_ROLES` map and the exact same `authorize(...)` method —
the mechanism never changes, only which action codes map to the role:

- **Platform Admin** — in `OWNER_AND_ADMIN` for `TENANT_CREATE`, `TENANT_SETTINGS`,
  `FEATURE_TOGGLE`, `PLAN_MANAGE`, `INTEGRATION_TYPES_MANAGE`, `PLATFORM_AUDIT_VIEW`,
  `USAGE_COUNTS_VIEW`, `BACKUP_STATUS_VIEW`, `LANDING_CMS_MANAGE`, and
  `RELEASE_MANAGE`/`SUPPORT_TICKET_MANAGE`. **Cannot** activate/suspend/archive a tenant
  (`TENANT_ACTIVATE`/`TENANT_SUSPEND_ARCHIVE` are `OWNER`-only, lines 33-34), cannot manage
  platform users (`PLATFORM_USER_MANAGE`, `OWNER`-only, line 42), cannot set security policy
  (`SECURITY_POLICY_SET`, `OWNER`-only, line 43), cannot apply billing discounts
  (`DISCOUNT_APPLY`, `OWNER`-only, line 45), and cannot touch any of the four export/
  deletion/retention actions at all (not in any set it belongs to).
- **Billing Manager** — only `INVOICE_MANAGE`, `OVERDUE_FLAG`, and shares
  `PLATFORM_REPORTS_VIEW` (lines 46-47, 53). **Cannot** apply a discount
  (`DISCOUNT_APPLY` is `OWNER`-only despite billing being its area — the comment at line 45
  calls this out explicitly as Rule F-4), cannot create/suspend/activate tenants, cannot
  touch support tickets, roles, or security policy.
- **Support Manager** — only `SUPPORT_TICKET_MANAGE` and `SUPPORT_ACCESS_USE`, plus shares
  `PLATFORM_REPORTS_VIEW`. **Cannot** raise or grant its own support access
  (`SUPPORT_ACCESS_GRANT`/`SUPPORT_TICKET_RAISE` are deliberately absent from this map
  entirely — the comment at line 49-50 says these belong to the tenant side, in
  `TenantSupportController`, so a platform Support Manager can never self-grant access to a
  tenant).
- **Release Manager** — only `RELEASE_MANAGE` (shared with Product Owner/Platform Admin).
  No tenant, billing, or security actions at all.
- **Data Protection Officer** — the mirror image of the Product Owner restriction above: it
  **alone** holds `TENANT_EXPORT_APPROVE`, `TENANT_DELETION_APPROVE`,
  `RETENTION_CHANGE_APPROVE`, and `APPROVAL_DELEGATE` (lines 59-62), and also
  `RBAC_MAPPING_CHECK` shared with the Product Owner (line 57). It **cannot** request an
  export or deletion itself (`TENANT_EXPORT_REQUEST`/`TENANT_DELETION_REQUEST` are
  `OWNER`-only) — so the same self-approval boundary holds from the other direction: the
  requester and the approver are structurally different roles in this map, with no action
  belonging to both sides of a single chain.

In every case above, the restriction is enforced the same way: the action code either is
or is not a key whose role-set contains the caller's `PlatformRole`. There is no
per-tenant or per-record scoping on the platform side (unlike the tenant side's
`RbacScope`/`RbacTarget` matching) — platform actions are either held tenant-wide-globally
or not held at all.

## Client Pitch Summary

*This section serves two readers at once. The "On screen"/"Speaker notes" parts are the
plain-English client pitch. Each feature is immediately followed by a "Developer detail"
block with full technical depth (exact flow, file:line citations, action codes, DB tables,
edge cases, known gaps) so a new engineer could pick up the feature from this section alone,
without hunting through the rest of the file. Status labels for the client: "Live today",
"Built, screen coming soon", "On the roadmap".*

**On screen — what the Platform team can do today:**
- Onboard new schools/colleges onto the platform
- Manage subscription plans and billing
- Handle support tickets, with time-limited supervised access
- Monitor system health, usage, and audit history

**Speaker notes:** This is EDU 360's own team keeping the lights on for every client. They
create new institution accounts, manage pricing plans and invoices, and run support — all
without ever freely browsing a school's actual student or staff data. Status: Live today.

**Developer detail — Platform authorization mechanism (applies to every feature below,
read this first):**

If you're new to this codebase, the first thing to know is that there is **no
`@PreAuthorize` annotation anywhere** on these controllers. In a lot of Spring Boot
projects you'd see something like `@PreAuthorize("hasRole('ADMIN')")` sitting on top of a
controller method, and Spring's security framework would intercept the call before it even
runs. This codebase does it differently, on purpose: every single controller method
contains an explicit line of Java code that calls
`PlatformPermissionService.authorize(principal, "ACTION_CODE")`
(`api/src/main/java/com/edu360/api/platformops/PlatformPermissionService.java`). This is
called an **imperative** permission check — "imperative" just means "a line of code you can
read and step through," as opposed to a hidden framework annotation. The reason the team
chose this is traceability: if you grep the codebase for an action code like
`"TENANT_CREATE"`, you can see exactly every place it's checked, instead of having to know
Spring's annotation-processing rules.

An **action code** is just a short, fixed string (like `"TENANT_CREATE"` or `"PLAN_MANAGE"`)
that names one specific thing a user might try to do. Think of it as a permission's unique
ID. On the platform side, these codes are checked against a plain Java map in
`PlatformPermissionService`, called `ALLOWED_ROLES` (lines 31-63) — literally a
hard-coded `Map<String, Set<PlatformRole>>` listing which of the 6 platform roles (Product
Owner, Platform Admin, Billing Manager, Support Manager, Release Manager, Data Protection
Officer) is allowed to perform each action code. This is different from the tenant side
(see `tenant-admin.md`), where the equivalent mapping lives in a database table
(`app.role_action`) that can be edited without a code change — on the platform side it's
baked into the Java source, because the 6 platform roles and what they can do are
considered fixed, not something a client ever configures.

`authorize(...)` itself does two important things for every call, and you should expect
this pattern every time you read a new platform endpoint: first, if the action code isn't
recognized at all, or the calling user's role isn't in that code's allowed set, it denies
the request (lines 81-96) — there's no silent "allow by default" path anywhere. Second,
*before* it throws the exception that will turn into an HTTP 403, it writes a `DENIED`
audit event (`deny(...)`, lines 73-79, backed by `PlatformAuditWriter.recordDenied`,
referenced at line 76). This matters for a simple reason: if someone later asks "did anyone
try to do X and get blocked?", there's a permanent record, even for attempts that never
succeeded. The exception itself is a plain `UnauthorizedException`, and every controller in
the app funnels those into the same shape via
`api/src/main/java/com/edu360/api/exception/GlobalExceptionHandler.java`, which converts any
thrown exception into a consistent JSON body `{timestamp, status, error}` — so the frontend
only ever has to handle one error shape, not a different one per endpoint.

Before any of this permission logic runs, the user has to sign in and the server has to
know *who* they are. Here's that flow end to end, because it's shared by every feature in
this file: the frontend sends an email and password to `POST /api/auth/login`
(`AuthController.java:58`). The *very first* thing that happens is picking which Keycloak
"realm" to check the password against — Keycloak is the separate identity system this app
delegates login to, and a "realm" is Keycloak's name for an isolated set of user accounts.
Crucially, the realm is picked purely from the browser's `Host` header (i.e., which web
address the user typed), via `tenant/LoginTenantResolver` — never from anything typed into
the login form itself. This is what makes it physically impossible to log into the wrong
school by mistake: the website address itself determines which account database you're
even allowed to authenticate against. For the platform audience specifically, that realm is
`edu360-platform`. If the password is correct, Keycloak issues an access token and a
refresh token, and `AuthController` just relays them back in the HTTP response body (or, if
the account has multi-factor authentication enrolment pending, it returns an
`MFA_ENROLMENT_REQUIRED` response instead of tokens, lines 62-69).

From then on, every other request the browser makes carries that access token in an
`Authorization: Bearer ...` header, and the token gets independently re-checked on *every
single request* — not just trusted because it was valid once. That re-check happens in
`config/TenantIssuerAuthenticationManagerResolver`: it reads the token's own `iss` (issuer)
claim just to decide *which* realm's public signing key to verify the token's signature
against, and only after that cryptographic signature check passes does the request get
turned into a `PlatformPrincipal` object
(`api/src/main/java/com/edu360/api/config/PlatformPrincipal.java`) representing "this is a
verified platform user." This two-step (read the claim to pick a key, then verify the
signature with that key) is what stops someone from forging a token that just claims to be
from a different realm.

One more concept a junior developer should understand here because it comes up constantly
in this codebase: **Row-Level Security (RLS)** and **tenant context**. The database itself
(Postgres) has a rule built into it — not just application code — that most tables under
the `app.*` schema are only visible to a request that has first set a particular session
variable (a "GUC", Postgres's term for a per-connection setting) telling the database which
tenant it's currently acting for. A platform request, by design, **never sets that
variable** (`TenantContext` is a tenant-only concept), so even if a bug somewhere in the
Java code accidentally tried to read tenant data during a platform request, the database
itself would return nothing. This is called "fail-closed": the default, if something goes
wrong, is to show *nothing* rather than accidentally show *everything*. It's a second,
independent layer of protection underneath the Java permission checks described above —
so even a mistake in `PlatformPermissionService` could not leak a school's actual student
records to a platform user, because the database connection was never told which tenant to
look at in the first place.

**Feature — Bringing a new institution on board**
- On screen: a few clicks creates a brand-new, fully isolated institution; it comes with its
  own login address automatically; it can be paused, archived, or reactivated as needed.
- Speaker notes: when you sign up, our team creates your institution's private space in
  minutes, not days. E.g. a new college signs the contract Monday, and by Tuesday their own
  admin is logging in and setting up their first academic year.
- Status: Live today.
- **Developer detail:** This whole feature lives in one controller,
  `PlatformTenantController.java`, which (like every controller in this codebase) exists
  only to receive the HTTP request, extract the caller's identity, call the one-line
  permission check described above, and then hand off to a service class for the real
  work. The controller is mapped to two different URL prefixes at once —
  `@RequestMapping({"/v1/platform", "/api/platform"})` (line 47) — which simply means the
  same code answers requests under either `/v1/platform/...` or `/api/platform/...`; this
  is a historical/compatibility detail, not two separate implementations.

  Creating a brand-new school/college: `POST /api/platform/tenants` (line 202) is the
  endpoint the "create tenant" screen calls. Before it does anything else, it calls
  `authorize(principal, "TENANT_CREATE")` (line 209) — if the signed-in platform user's role
  isn't Product Owner or Platform Admin, the request is rejected right there and nothing
  is created. This matters because creating a tenant is a heavyweight, consequential
  operation (it provisions a whole new isolated Keycloak realm and a new slice of
  database), so it's deliberately restricted to the two most senior platform roles. Because
  setting up a realm and database space can take longer than a single web request should
  reasonably wait for, provisioning is designed to be retryable: if it gets stuck partway
  through, `POST /api/platform/tenants/{tenantId}/provisioning/retry` (line 238) lets an
  authorized user (same `TENANT_CREATE` gate, line 245) kick it again rather than starting
  over from scratch. `GET /api/platform/tenants` (line 265) is the read-only "list all
  tenants" call behind the Tenants Directory screen — reads are checked too, just with a
  read-oriented `authorize`/`authorizeAny` call, so even looking at the list of schools
  requires a recognized platform role.

  Once a tenant exists, its lifecycle (active / suspended / archived / reactivated) is each
  a *separate* endpoint with its *own* permission check, rather than one generic
  "update tenant status" call — this is a deliberate design choice so that, for example,
  suspending a school (which cuts off its users' access) can be locked down more tightly
  than, say, updating its settings. `POST
  /api/platform/tenants/{tenantId}/activation` (line 283) → `TENANT_ACTIVATE` (line 292),
  restricted to **Product Owner only** — note this is narrower than creation, which also
  allows Platform Admin; activating a tenant is treated as more sensitive than creating it.
  `POST .../suspension` (line 102) and `POST .../archive` (line 132) both check
  `TENANT_SUSPEND_ARCHIVE` (lines 111, 141), also **Product Owner only** — suspending cuts
  off a live, paying client's access, so the system deliberately doesn't let a Platform
  Admin do this alone. `POST .../reactivation` (line 162) re-uses that same action code
  (line 169), since reactivating is the mirror image of suspending/archiving and carries the
  same risk profile. Updating a tenant's general settings is less sensitive and is gated by
  a different, slightly wider action code, `TENANT_SETTINGS` (around line 196), which both
  Product Owner and Platform Admin hold. `POST
  /api/platform/tenants/{tenantId}/first-admin/password-reset` (line 253) — used when a
  brand-new school's very first admin account is locked out before they've even logged in
  once — also uses `TENANT_SETTINGS` (line 259), the reasoning being that resetting a
  first-login password is an operational/settings-type action, not a destructive one.

  Known gap for a KT engineer to be aware of: this document only verified the
  controller-level permission gates above; it does not independently trace the full
  provisioning *state machine* (e.g. exactly which internal states a tenant passes through
  between "created" and "fully active," or what specifically triggers an automatic vs.
  manual retry). Read `PlatformTenantService`/the provisioning-related service classes
  directly before describing that state machine's exact behavior to anyone.

**Feature — Billing and plans**
- On screen: flexible subscription plans per institution; invoices and billing managed
  centrally; discounts require senior sign-off, never a single person's call.
- Speaker notes: pricing and invoicing are handled centrally and transparently. Even applying
  a discount needs a second, more senior approval, so no single staff member can quietly
  give away free service.
- Status: Live today.
- **Developer detail:** Plans (the subscription tiers a tenant signs up for) and billing
  (invoices against a specific tenant) are deliberately split into two different
  controllers, because they're conceptually different things and, as you'll see below, are
  allowed to different roles. `POST /api/platform/plans` (line 65) creates a new plan and
  `PUT /api/platform/plans/{planId}` (line 87) edits one; both check `PLAN_MANAGE` (lines
  72, 96), which only Product Owner or Platform Admin hold — designing a pricing plan is
  treated as a product decision, not a billing-operations task, so even the Billing Manager
  role cannot do it.

  Billing itself lives in `PlatformBillingController.java`, which checks
  `INVOICE_MANAGE`/`DISCOUNT_APPLY`. The interesting, easy-to-miss detail here (worth
  remembering if you're ever asked "why can't the Billing Manager apply a discount, isn't
  that literally their job?") is that `DISCOUNT_APPLY` is *not* in the Billing Manager's
  allowed-role set at all — it's `OWNER`-only, same as the most sensitive tenant-lifecycle
  actions above. The code comment at `PlatformPermissionService.java:45` even names this
  explicitly as "Rule [F-4]," which tells you it's a deliberate business rule someone wrote
  down and assigned an ID to, not an accident that got left in. The reasoning: a discount
  directly reduces revenue, and letting the same role that issues invoices also grant
  itself discounts on those invoices would be a classic fraud/self-dealing risk (think of
  it like a cashier being allowed to give themselves a discount at checkout) — so discount
  approval is pulled up to the most senior role, separate from day-to-day invoice handling.
  If you ever see a support ticket saying "Billing Manager got a 403 trying to apply a
  discount," this is expected behavior, not a bug to fix.

**Feature — Support, without breaking your privacy**
- On screen: support staff can only see your data with your permission; access is tied to a
  specific ticket with a strict time limit; access automatically expires, no standing "back
  door".
- Speaker notes: if you ever raise a support ticket, our support team gets a narrow, temporary
  window into just what's needed to help you, capped at 24 hours, switched off automatically
  afterwards. They cannot browse your data at will, and they can never touch your most
  sensitive records, like confidential grievance cases, even during that window.
- Status: Live today.
- **Developer detail:** This is one of the more carefully designed safety features in the
  codebase, so it's worth walking through slowly. Support tickets and the "grant a support
  person temporary access" action are handled by two controllers:
  `PlatformSupportController.java` (the platform side — raising/managing tickets) and
  `TenantSupportController.java` (the tenant side — a school's own admin is the one who
  actually *grants* the access, not the platform). Both are gated by action codes
  `SUPPORT_TICKET_MANAGE` and `SUPPORT_ACCESS_USE` through the same `authorize(...)`
  mechanism described above.

  The 24-hour time limit is the headline promise to a client ("support can only see your
  data for at most a day, and only with your permission"), and it's worth knowing it is
  checked in **two separate places**, which is a pattern you'll see elsewhere in this
  codebase for anything the team considers high-risk: first, at the API's "front door," the
  request body itself is validated using a Java Bean Validation annotation —
  `api/src/main/java/com/edu360/api/platformops/support/dto/GrantSupportAccessRequest.java:12`
  declares the `durationHours` field with `@Min(1) @Max(24)`, which means Spring
  automatically rejects any request asking for a duration outside 1-24 hours before the
  controller's own code even runs. Second, and this is the important part for a junior dev
  to notice, the *service* layer does not just trust that validation — it re-checks the
  same rule itself, independently: `PlatformSupportService.java:200-201` contains `if
  (req.durationHours() < 1 || req.durationHours() > 24) throw new
  BusinessRuleViolationException(...)`, citing "Rule PQ-21 / ck_support_access_grant_r1" (a
  rule ID the team tracks, tying this check back to a specific written-down business
  requirement). Why bother checking the same thing twice? Because annotation-based
  validation can be skipped in certain code paths (for example, if another piece of code
  ever constructs a `GrantSupportAccessRequest` object directly in Java instead of going
  through the validated HTTP endpoint) — the service-layer re-check is a safety net that
  doesn't depend on every caller going through the same front door. Only after both checks
  pass does line 205 compute `expiresAt = now.plus(Duration.ofHours(req.durationHours()))`
  and save that expiry timestamp onto the grant row — so the cap is baked into the data
  itself, not just enforced at creation time; whatever reads this grant later can check
  `expiresAt` and know the access window closed, without re-running any validation.

  The actual "can this support person see this specific tenant's data" check happens
  inside the shared `PermissionEvaluatorService.can(...)` method
  (`api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java:177-190`) — the
  same class used for ordinary tenant-admin permission checks (see `tenant-admin.md`), just
  with an extra branch for this special case. If the authenticated principal is a
  `SupportPrincipal` (a distinct principal type from `TenantPrincipal`/`PlatformPrincipal`,
  representing "a platform support person currently inside a time-boxed session"), the code
  requires the tenant ID on the grant to match the tenant ID being accessed *exactly*
  (denying with `SUPPORT_TENANT_MISMATCH` otherwise), and if an institution-level scope was
  granted, that must match too (`SUPPORT_INSTITUTION_MISMATCH`). On top of that, even within
  the correct tenant, only actions specifically flagged `isSupportReadable()` in
  `ref.action_catalog` (the master table of every action code and its properties) are ever
  allowed — everything else gets `SUPPORT_READ_ONLY`. In practice this means a support
  session can read some things to help diagnose a problem, but can never, say, approve a
  fee concession or publish exam results, even inside the correct tenant and time window.
  Confidential grievance endpoints go even further and block a `SupportPrincipal` outright,
  before this generic check ever runs at all (see the grievance feature's Developer detail
  in `student.md`'s Client Pitch Summary) — a second, feature-specific wall layered on top
  of the generic one described here, because grievances are considered sensitive enough to
  deserve a hard block with no exceptions, rather than relying only on the generic
  read-only rule.

  Lastly, notice that the Support Manager role cannot grant *itself* access: the action
  codes for raising a ticket and granting access
  (`SUPPORT_ACCESS_GRANT`/`SUPPORT_TICKET_RAISE`) are not in the Support Manager's
  allowed-action map at all (comment at `PlatformPermissionService.java:49-50` explains
  these belong on the *tenant* side, inside `TenantSupportController`). In plain terms: the
  school grants access to the platform's support person, the platform's support person
  cannot grant access to themselves. This closes an obvious potential loophole where a
  support employee could otherwise self-service their way into a tenant's data without
  that tenant ever agreeing.

**Who can do what — Platform team**

| Action | Product Owner | Platform Admin | Billing Manager | Support Manager | Release Manager | Data Protection Officer |
|---|---|---|---|---|---|---|
| Create / suspend institutions | Yes | Limited | No | No | No | No |
| Manage billing & invoices | Yes | No | Yes | No | No | No |
| Apply a billing discount | Yes | No | No | No | No | No |
| Handle support tickets | Limited | No | No | Yes | No | No |
| Approve data export / deletion requests | No | No | No | No | No | Yes |
| Request a data export / deletion | Yes | No | No | No | No | No |

**Why the top role still has limits (plain terms):** the Product Owner — the most senior
platform role — cannot approve the very data export or deletion requests they raise
themselves. That's deliberate: the person who asks for something sensitive to happen is
never the same person who approves it. This stops one person from acting alone on your most
sensitive data, and it's enforced by the system itself, not just a policy on paper. The
Product Owner also can never see a school's actual data directly — only through a
time-limited, logged support session like everyone else, and only once a school allows it.

**Developer detail — self-approval block on data export/deletion/retention:**
This is a good example of how a business rule ("the person who asks for something
sensitive can't also be the one who approves it") turns into a very simple piece of code,
once you understand the `ALLOWED_ROLES` map described above. There are two distinct action
codes for "ask to export/delete a tenant's data" versus "approve that ask":
`TENANT_EXPORT_REQUEST`/`TENANT_DELETION_REQUEST` are mapped to `OWNER` (lines 54-55) — so
the Product Owner is allowed to *raise* the request. But `TENANT_EXPORT_APPROVE`,
`TENANT_DELETION_APPROVE`, and `RETENTION_CHANGE_APPROVE` are mapped *only* to `DPO` (Data
Protection Officer) in that same map (lines 59-61) — the Product Owner's role simply is not
in that set. Because `authorize(...)` works by checking "is the caller's role inside this
action's allowed set," and the Product Owner's role is never in the *approve* actions' set,
there is no special code path to bypass — a Product Owner who calls the approve endpoint
gets exactly the same `UnauthorizedException` (line 89-91) that any unrelated, unauthorized
role would get, and the attempt is logged as a `DENIED` audit event first (`deny(...)`,
lines 73-79). The separation of duties here isn't a special "is this the same person"
check — it's simply the structural fact that no single role appears in both the request
map and the approve map for these three actions.

A second, separate mechanism worth knowing about: `requiresStepIn(...)`
(`PlatformPermissionService.java:155-159`). This handles a different scenario — not
self-approval, but deliberate handovers. If one platform staff member has formally been
handed responsibility for a particular action (for example, while the Product Owner is on
leave), and the Product Owner tries to perform that same action anyway, they get a `403`
with a `STEP_IN_REQUIRED` message, directing them to a "step-in" screen where they have to
explicitly record a reason before taking over. The purpose: even someone who nominally
holds an action shouldn't be able to silently override a handover that was deliberately set
up — if they need to act, they have to leave an explicit trail explaining why they're
stepping back in.

Finally, two defensive checks apply to every role, including the Product Owner, with no
exceptions: an action code the system doesn't recognize at all is always denied (line 82),
and a request with no identified principal (e.g. something went wrong upstream in
authentication) is always denied (line 85). There is no "if role == OWNER then skip all
checks" branch anywhere in this class — a junior developer reading this code for the first
time should specifically look for, and not find, any such superuser bypass; its absence is
itself the guarantee.

**Coming next / planned:**
- Richer, more detailed day-to-day dashboards for each platform role (today there is one
  shared dashboard layout; a fuller per-role "cockpit" view is planned). Status: On the
  roadmap.
- A larger set of platform screens and operations than exist today — the main screens already
  exist, but some are not yet as deep or detailed as the long-term design calls for. Status:
  Built, screen coming soon (for the deeper detail).
