# Keycloak Migration Plan

**Status (2026-11-02): implemented in code**, Phases 0–2 below, in both `api/` and `app/`. The
local-JWT/cookie auth described in "current state" no longer exists in the codebase — it has been
fully replaced by the OAuth2-resource-server model in "target state". A running Keycloak instance
(see `docker/keycloak/`) is required for this app to start in `dev` or to log in at all; without
one, `./mvnw spring-boot:run` will fail at `DevAdminSeeder` and the frontend login redirect will
fail to reach an identity provider. Phase 3 (dropping local credential columns — already done,
since `User` never regained a `password` column in this implementation) and the
`/v1/internal/identity-events` relay are not built; see §4 for what's left.

## 1. Former state (superseded — kept for history)

- `AuthController`/`AuthService` issued and verified JWTs locally (`JwtUtil`, HMAC-signed, Base64
  secret). The API owned passwords directly — it hashed and checked them itself (BCrypt).
- The token travels as an `HttpOnly`/`SameSite=Lax` cookie (`EDU360_SESSION`), not a bearer header.
- Authorization is `Role` (`PLATFORM_ADMIN` / `TEACHER` / `STUDENT`) via
  `@PreAuthorize("hasRole(...)")` — no tenant, no institution, no per-action code.
- Account creation is a single hard-coded rule: only `PLATFORM_ADMIN` can call
  `POST /api/auth/register`, and only a dev-only seeder (`DevAdminSeeder`) can create that first
  admin.
- New: account lockout (5 attempts / 15 min) and an authentication audit log
  (`AuditLog`/`AuditLogService`), both implemented locally against the app's own `users` table.

## 2. Target state (per `docs/V4.0_SPEC_SUMMARY.md` §1/§4)

- Keycloak is the identity provider. The API **never touches a password** — it only receives and
  validates a Keycloak-issued OIDC access token on the `bearerAuth` security scheme (used by 407
  of 408 OpenAPI operations).
- JWT claims carry `role`, `tenant_id`, `scope`, `permissions` — authorization becomes
  tenant-scoped and action-code-based (the 278-action list), not a flat Spring Security role.
- The API's only role in identity events is passive: `POST /v1/internal/identity-events` just
  records what Keycloak tells it (via the `serviceAuth` OAuth2 client-credentials scheme), it
  doesn't drive login.
- Account creation moves from "one role creates everything" to the real hierarchy already found
  in the governance register's "Role x Action" sheet: Platform Admin creates **tenants**;
  each institution's own Admin (University/College/School Admin, or Registrar/Vice-Principal for
  creation specifically) creates **users** within that institution — see
  `docs/V4.0_SPEC_SUMMARY.md` §2 and §7 for the full breakdown.

## 3. Open decisions — resolved (2026-11-02)

Both were marked blocking ("B") in the governance register and are now decided:

- **OD-10 — "Keycloak model and account linkage" (Doc 1 §4.34.1).** **Decided:** link by
  Keycloak's own user ID. Keycloak issues a unique, stable ID (the token's `sub` claim) for every
  user it manages. The local `User` table gets a new column (`keycloak_id` / equivalent) storing
  that ID. On every authenticated request, the backend reads `sub` from the validated token and
  looks up the local `User` row by that column — never by email/username matching — to attach
  institutional data (role assignment, scope, institution) to the request.
- **OD-80 — how `/auth/*`-style endpoints front Keycloak.** **Decided:** Keycloak performs the
  actual login via standard OpenID Connect (Authorization Code flow): the frontend redirects the
  user to Keycloak's hosted login page rather than embedding a password form; the backend never
  sees a password. The backend's job is to validate the returned access token (signature + issuer,
  via the realm's JWKS endpoint) and resolve it to a local user via OD-10's `keycloak_id` lookup.
  The existing `/api/auth/*` endpoints are **kept**, but repurposed: they stay for
  application-specific operations (registration/account-provisioning, profile retrieval, account
  management) rather than for password authentication, which moves to Keycloak entirely.

These decisions unblock Phase 1 below. The exact column name, the realm/client configuration, and
how `register` provisions a matching Keycloak-side account still need to be nailed down as
implementation detail, but the shape of the migration is no longer an open question.

## 4. Proposed phased approach

**Phase 0 — prep (can start now, no Keycloak dependency):**
- Introduce a `tenant_id` concept into the schema even before multi-tenancy is real (nullable,
  defaulting to a single implicit tenant), so later RLS/claim work doesn't require a second
  migration on top of this one.
- Replace the flat `Role` enum authorization checks with an explicit permission-check layer (even
  if still backed by the same 3 roles today) so swapping the *source* of that permission (local
  role vs. Keycloak `permissions` claim) is a single-layer change later, not a rewrite of every
  `@PreAuthorize`.

**Phase 1 — stand up Keycloak (now unblocked — OD-10/OD-80 resolved):**
- Run a Keycloak realm (dev: local container; prod: managed service per the AWS deployment notes
  in the product spec).
- Define the realm's client for this API (confidential client for the backend; public client +
  Authorization Code flow with PKCE for the frontend).
- Add a `keycloak_id` column to `User` (OD-10) — nullable during transition, unique once populated.
- Keep the local `users` table during transition as the source of institutional data (name,
  role-assignment, institution scope) while Keycloak owns credentials — do not duplicate password
  storage once this phase starts.

**Phase 2 — swap the auth filter:**
- Replace `JwtAuthFilter`'s local HMAC verification with Spring Security's OAuth2 resource-server
  support validating Keycloak-issued tokens against the realm's JWKS endpoint, then resolving the
  token's `sub` claim to a local `User` via the new `keycloak_id` column (OD-10).
- Switch the frontend from cookie-session (`axios` `withCredentials`) to the OIDC Authorization
  Code flow: redirect to Keycloak's hosted login page, handle the callback, and store/refresh the
  resulting access token per Keycloak's flow (OD-80) — this is a real frontend architecture change,
  not a drop-in swap.
- Retire `AuthController.login`'s local password check entirely (password auth moves to Keycloak).
- Keep `AuthController.register` and related `/api/auth/*` endpoints, but repurpose them (OD-80):
  `register` now provisions a matching Keycloak-side account (via Keycloak's admin API) alongside
  the local `User` row instead of hashing/storing a local password; profile/account-management
  endpoints are unaffected since they never did password work.

**Phase 3 — retire local credentials:**
- Drop `password`, `failedLoginAttempts`, `lockedUntil` from `User` once Keycloak owns login
  entirely — Keycloak has its own lockout policy (map it to the same 5-attempt/15-minute rule via
  realm brute-force settings) and the local `AuditLog` login events get replaced/supplemented by
  Keycloak's own event listener feeding `/v1/internal/identity-events`.
- Expand the single `PLATFORM_ADMIN`-creates-everything rule into the real
  tenant-creates-then-institution-admin-creates-users hierarchy once the tenant/institution schema
  from `docs/V4.0_SPEC_SUMMARY.md` §3 actually exists.

## 5. What NOT to do

- Don't try to run local password auth *and* Keycloak side by side as a permanent state — pick a
  cutover point per environment (dev can flip first) rather than maintaining two auth systems
  indefinitely.
- Don't let the `/api/auth/*` endpoints that remain (register, profile, account management) fall
  back into doing password work — OD-80 explicitly moves password authentication to Keycloak only.
- Don't match a local `User` to a Keycloak identity by email or username — OD-10 decided the match
  is by Keycloak's stable `sub`/user ID (`keycloak_id` column), specifically to avoid breakage if
  an email changes.
