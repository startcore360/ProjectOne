# New Developer Guide

Welcome. This is a plain-English walkthrough of the EDU 360 codebase, written for someone
opening it for the first time â€” an intern, a new hire, anyone. Every source file mentioned below
also has a short comment at the top of it in the actual code, so once you're oriented here you can
keep reading straight from the files.

This guide covers the **real app** â€” `api/` (backend) and `app/` (frontend). There's a separate,
much bigger folder at `docs/EDU360_V4.0_Final/` that describes a *future, much larger* target
design for this product (see `docs/V4.0_SPEC_SUMMARY.md`) â€” don't confuse the two. What's
described here is what actually runs today.

## 1. What this project is

EDU 360 is a small school app with two kinds of day-to-day users â€” **teachers** and
**students** â€” plus one special account type, **platform admin**, whose only job is creating
accounts for everyone else. Right now it can:

- Log people in and out.
- Let a teacher create a course and enroll students.
- Let a teacher mark attendance and enter scores.
- Let a student see their own attendance, scores, and a performance summary.

That's it. It's deliberately small â€” think of it as an early slice of a much bigger product idea
(the V4.0 docs), built just far enough to prove the basics work.

## 2. The big picture: how backend and frontend talk

```
Browser (app/)  --HTTP requests with Authorization: Bearer <token>-->  Spring Boot server (api/)
      |                                                                    |
   React UI                                                          Spring Security
   (login screen, dashboard, forms)                                  validates Keycloak JWTs
                                                                       and runs the business logic
```

- The **frontend** (`app/`) is a React + Vite SPA. It never talks to the database directly —
  every piece of data comes from the backend API.
- The **backend** (`api/`) is a Spring Boot app. It owns the actual business logic and the app's
  data model.
- They talk over plain HTTP. The frontend sends JSON requests, and the backend responds with JSON.
- The app is currently using **Keycloak-issued JWTs** instead of a custom JWT implementation. The
  frontend stores the returned access/refresh tokens in `sessionStorage`, and every authenticated
  request adds `Authorization: Bearer <token>`.

This is the key difference from the older cookie-based setup: there is no `EDU360_SESSION` cookie
being set by the backend today. The current app treats the tokens themselves as the session state.

## 3. Running it locally

- Backend: `cd api && ./mvnw spring-boot:run` (dev profile defaults to H2 unless you override it).
  Full details are in `api/README.md`.
- Frontend: `cd app && npm install && npm run dev`, then open `http://localhost:5173`.
- For the matching auth stack locally, run Keycloak/Postgres from the Docker files in
  `docker/keycloak/` and `docker/postgres/`.
- In dev, the app may also have a seeded platform-admin account via `DevAdminSeeder`; check that
  class before assuming a permanent username/password or a production setup.

## 4. How login, accounts, and roles actually work

This is the part most worth understanding well, because almost everything else depends on it.

**Step by step:**
1. The user enters email + password in the React login page (`app/src/features/auth/pages/LoginPage.tsx`).
2. The frontend calls `POST /api/auth/login`.
3. `AuthController.login` forwards the credentials to `AuthService.login`.
4. `AuthService.login` calls `KeycloakAuthClient.login(...)`, which sends a direct-grant request to
   Keycloak's token endpoint. Keycloak checks the password and issues an **access token** and
   **refresh token**.
5. The backend reads the Keycloak-issued `sub` claim to identify the Keycloak user and looks up the
   matching local `User` row by `keycloakId`.
6. The backend returns a JSON payload (`AuthTokenResponse`) containing the JWTs plus the user summary.
   The frontend saves them in `sessionStorage` via `tokenStorage.ts`.
7. Later requests attach `Authorization: Bearer <token>` using the shared axios instance.
8. `SecurityConfig` is configured as an OAuth2 resource server with a custom JWT converter. That
   converter turns the Keycloak JWT into Spring `Authentication`, so `@PreAuthorize` checks can run.
9. Methods such as `@PreAuthorize("hasRole('TEACHER')")` or
   `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` decide what the caller is allowed to do.

**Who can create an account?** Only a `PLATFORM_ADMIN` can call `POST /api/auth/register` to create a
`TEACHER` or `STUDENT`. A platform-admin account is not meant to be created through the public API.
The normal dev bootstrap path is through `DevAdminSeeder` or a controlled provisioning setup.

**What happens if someone types the wrong password too many times?** The actual credential check is
now delegated to Keycloak. This app still records the outcome in its local audit log and maintains
local user account data, but the password verification itself happens in Keycloak rather than in a
custom BCrypt flow.

**Is any of this written down anywhere?** Yes. Login attempts, failures, password changes, and logout
activity are tracked through the app's audit flow (`audit/` package), while Keycloak handles the
actual credential validation and token issuance.

## 5. Backend folder tour (`api/src/main/java/com/edu360/api/`)

| Folder | What lives there |
|---|---|
| *(root)* | `ApiApplication.java` — starts the Spring Boot app. |
| `auth/` | Login, refresh, logout, register, and account/password-change endpoints. `AuthController` exposes the web API; `AuthService` orchestrates the Keycloak call and local user lookup; `auth/dto/` holds the request/response payloads. |
| `config/` | App-wide security setup. `SecurityConfig` controls the routes and JWT validation; `KeycloakJwtAuthenticationConverter` turns Keycloak tokens into Spring authentication; `KeycloakAuthClient` and `KeycloakAdminClient` talk to Keycloak; `DevAdminSeeder` handles dev bootstrap data. |
| `user/` | The `User` entity and repository, plus the role enum (`TEACHER`, `STUDENT`, `PLATFORM_ADMIN`). |
| `course/` | Teacher-facing course creation and enrollment logic. |
| `attendance/` | Marking and viewing attendance. |
| `score/` | Entering and viewing grades. |
| `performance/` | Summary calculations for student/teacher performance views. |
| `audit/` | Auth and system event logging. |
| `exception/` | Custom exceptions and the centralized `GlobalExceptionHandler`. |

## 6. Frontend folder tour (`app/src/`)

| Folder | What lives there |
|---|---|
| `main.tsx` | The very first thing that runs: sets the light/dark theme, checks if you're already logged in, *then* renders the app â€” in that order, so there's no visual flash of the wrong state. |
| `App.tsx` | The top-level wrapper. Renders all the pages/routes, and always shows the floating theme-toggle button on every screen. |
| `routes/` | `Routes.tsx` is the map of URL â†’ page. `PrivateRoute`/`PublicRoute` are small gatekeepers that send you to/from the login page depending on whether you're signed in. |
| `layout/` | `AuthLayout` (the plain frame around the login page) and `AppLayout` (the sidebar + top bar frame around everything after login). |
| `features/auth/pages/LoginPage.tsx` | The actual sign-in screen and form. |
| `features/dashboard/pages/DashboardPage.tsx` | The home page after login â€” currently mostly placeholder content. |
| `services/` | Everything that talks to the backend: `axios.ts` (the shared HTTP client), `authManager.tsx`/`authService` (login/logout/register calls), `bootstrapAuth.tsx` (the "am I logged in?" check on startup), `axiosInterceptor.ts` (auto-logout if the server says your session expired), `inactivityTracker.ts` (auto-logout after 30 idle minutes), `forceLogout.ts`/`logoutNotice.ts` (forcing a logout and showing why). |
| `store/` | Redux state â€” right now just `authSlice.ts`, which remembers who's logged in. (Note: `redux-persist` is installed as a dependency but isn't actually wired into `store/index.tsx` yet â€” nothing breaks because of this, but it means the auth slice doesn't survive outside this session's own `bootstrapAuth` re-check.) |
| `components/ui/` | Small reusable pieces: `Button`, `Badge`, `Spinner`, `ThemeToggle` (the light/dark switch). |
| `components/feedback/` | `EmptyState` and `ErrorState` â€” reusable "nothing here" / "something broke" messages. |
| `components/layout/` | `PageHeader`, a reusable page-title bar. |
| `lib/theme.ts` | Remembers your light/dark choice in the browser's local storage. |
| `hooks/` | Small reusable pieces of logic: `useDebounce`, `useClickOutside`, `useUrlFilters`. |
| `utils/` | Small helpers, e.g. `formErrorMapping.ts` for cleaning up backend validation error messages. |

## 7. Known rough edges worth knowing about

These aren't secrets â€” they're just things a new developer should know aren't finished, so you
don't assume they're bugs in *your* code:

- `OpenApiConfig.java`'s Swagger "Bearer Auth" setup doesn't match the real cookie-based login â€”
  the Swagger docs page may describe auth incorrectly until someone fixes that.
- `redux-persist` is a listed dependency in `app/package.json` but isn't wired up anywhere â€” the
  Redux store doesn't actually persist across page loads by itself (login state is re-checked via
  `bootstrapAuth` instead, which covers the same need differently).
- The dashboard page is mostly placeholder â€” no real attendance/score data is wired into it yet,
  even though the backend endpoints for that data already exist.
- This whole app is a small, 3-role, single-tenant slice of a much bigger planned product. See
  `docs/V4.0_SPEC_SUMMARY.md` if you ever need to understand where this is all eventually headed â€”
  but don't assume anything in that document is already built.

