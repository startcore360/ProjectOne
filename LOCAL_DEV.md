# EDU 360 — Local Development Guide

How to run the full stack locally: Postgres (with the approved V4.0 schema + RLS), three
Keycloak realms (two tenants + platform), LocalStack (Secrets Manager stand-in for `ISOLATED`
tenants plus the development S3 object-storage bucket), the Spring Boot backend, and the Vite frontend.

## Prerequisites

- Docker Desktop running
- Java 21, Node.js, PowerShell
- **No hosts file needed.** Use `*.edu360.localhost` addresses, such as
  `http://abc.edu360.localhost:5173`. Chrome, Edge and Firefox send every `*.localhost` name to
  `127.0.0.1` by themselves, so a new tenant opens the moment it is created. The backend tells
  tenants apart by the `Host` header, so each tenant still needs its own address. `.localhost`
  just removes the per-tenant setup step.
- *Optional, older style:* `*.edu360.local` addresses still work, but each one needs a hosts-file
  line. Run `.\docker\dev-hosts-setup.ps1` as Administrator after creating a tenant.
- Tools outside the browser (curl, Postman) may not resolve `*.localhost` on Windows. For them,
  use `.edu360.local`, or call `http://127.0.0.1:8080` with a `Host: abc.edu360.localhost` header.
- **Production** uses neither. A wildcard DNS record (`*.edu360.com`) plus a wildcard TLS
  certificate cover every tenant with no per-tenant step. See `docker/PRODUCTION_TENANT_ROUTING.txt`.

## Commands

| Command | What it does |
|---|---|
| `.\dev-up.ps1` | Starts Postgres/Keycloak/LocalStack in Docker, applies the SQL bundle and provisions `edu_app`/`edu_platform` credentials if needed, fixes Keycloak's per-realm user-profile config (needed every start — see below), then opens the backend and frontend each in their own window. |
| `.\dev-down.ps1` | Stops the backend/frontend windows and the Docker containers. Data volumes are preserved (no `-v`), so Postgres data survives. |
| `.\dev-restart.ps1` | `dev-down.ps1` then `dev-up.ps1`. Use when something's in a confused state. |
| `.\dev-status.ps1` | Checks Docker containers plus whether Postgres/Keycloak/LocalStack/backend/frontend are actually answering requests. |
| `.\dev-logs.ps1 <postgres\|keycloak\|localstack\|backend\|frontend> [-Follow]` | Tails that service's logs. Docker services go through `docker compose logs`; backend/frontend read `logs\backend.log` / `logs\frontend.log` (written by `dev-up.ps1`). |

Backend alone: `cd api; $env:SPRING_PROFILES_ACTIVE='dev'; $env:SPRING_DATASOURCE_USERNAME='edu_app'; $env:SPRING_DATASOURCE_PASSWORD=<from docker\postgres\.env.local>; .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` (plus `PLATFORM_ADMIN_DATASOURCE_PASSWORD` if you need `DevAdminSeeder` to run — see `.env.local`). `dev-up.ps1` does all of this for you. Note: the backend fails fast at startup if no Spring profile is active ("Set SPRING_PROFILES_ACTIVE").

Frontend alone: `cd app; npm run dev`.

## Accessing the three tenants

| | Hostname | Keycloak realm |
|---|---|---|
| ABC University | `http://abc.edu360.localhost:5173` | `abc-university` |
| XYZ University | `http://xyz.edu360.localhost:5173` | `xyz-university` |
| Platform Admin | `http://platform.edu360.localhost:5173` | `edu360-platform` |
| Any new tenant | `http://<subdomain>.edu360.localhost:5173` | its own realm |

The `.edu360.local` form of each address also works if your hosts file has it.

Plain `http://localhost:5173` will **not** work for login — `localhost` matches no tenant
subdomain or the platform label, so you'll get `"Unknown sign-in address"`. You must use one of
the three hostnames above.

The frontend derives its API base URL from whatever hostname you loaded the page from
(`app/src/services/axios.ts`) — if you ever see login calls going to plain `localhost:8080`
instead, check `app/.env`/`app/.env.development` haven't had `VITE_API_BASE_URL` re-added; it
overrides the dynamic derivation.

**`admin@edu360-platform.local`** (Platform Admin) and **`owner@edu360-platform.local`** (Product Owner), both with password `ChangeMe123!`, are reseeded automatically by `DevAdminSeeder` on every backend
startup if missing (password logged to the backend console on first seed — grep the log for
"Seeded default dev"). There is no seeded tenant user by default; create one via
Keycloak's admin API (`http://127.0.0.1:8081`, master realm, `kcadmin`/`kcadmin`) plus matching
`app.user_account`/`app.user_identity_link` rows if you need one for testing.

## LocalStack object storage

The dev profile selects `S3FileStorage` and connects to LocalStack at `http://localhost:4566`. The `edu360-files` bucket is created automatically by the LocalStack ready hook when the service starts. The hook is packaged executable in the LocalStack image; no manual bucket creation is needed. Development credentials are test-only and must never be copied into production.

Support-session hand-off URLs use `http://<subdomain>.edu360.localhost:5173` and a development-only signing key. Production requires its own `SUPPORT_SESSION_SIGNING_SECRET` and apex domain configuration.

## Known quirks (found the hard way — save yourself the debugging time)

- **Keycloak has no persistent volume.** Every `dev-up.ps1`/container recreate re-imports the
  three realms from scratch, including re-running `docker\keycloak\provision-realms.ps1`'s fix
  for Keycloak 26's Declarative User Profile (which otherwise silently drops the
  `must_change_password` custom attribute — see that script's own comment). `dev-up.ps1` already
  does this for you; only matters if you're starting Keycloak some other way.
- **`edu_app` (the backend's Postgres role) is read-only on `platform.*`**, by the approved
  schema's own design. Writes to `platform.platform_user` (e.g. `DevAdminSeeder`'s dev seed) go
  through `edu_platform` instead — see `DevAdminSeeder`'s class comment. If you add a new
  platform-schema write path, it needs `edu_platform` too, not a widened `edu_app` grant.
- **Postgres region matters for LocalStack secrets.** `app.secrets.region` defaults to
  `ap-south-1`; if you create a secret manually via `awslocal` without `--region ap-south-1`, it
  silently lands in `us-east-1` and the app won't find it.

## Troubleshooting

- `.\dev-status.ps1` first — tells you which piece is actually down.
- `.\dev-logs.ps1 backend -Follow` while reproducing — most real errors show up there with a full
  stack trace (`GlobalExceptionHandler` logs every unhandled exception; it never just swallows one).
- "Unknown sign-in address" — you're not using one of the three tenant hostnames above.
- Backend won't start / `permission denied for schema/table ...` — rerun
  `.\docker\postgres\provision-edu-app.ps1`; it's idempotent and re-applies all required grants.
