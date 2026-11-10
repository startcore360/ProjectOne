# EDU 360 UAT test server (Ubuntu 24.04 / Oracle Cloud)

This directory contains a **test-only** deployment layout. It intentionally uses the
existing Spring `dev` profile, Keycloak dev mode, and LocalStack. Do not use this
configuration with real student, parent, staff, financial, or other personal data.
Use synthetic test records only.

## Layout

- Caddy terminates HTTPS on ports 80/443 and serves `/srv/edu360/dist`.
- Caddy proxies only `/api/*` to Spring Boot at `127.0.0.1:8080`.
- PostgreSQL, Keycloak and LocalStack are bound to loopback in Compose.
- Spring Boot runs as the dedicated `edu360` OS user via systemd and binds to loopback.
- Tenant hostnames must be listed explicitly in Caddy's `PLATFORM_HOSTNAME`
  and `TENANT_HOSTNAMES` settings. This is a UAT constraint, not production
  wildcard tenant provisioning.

## Required environment

Create `/etc/edu360/edu360.env` with root ownership and a restricted group readable
by the `edu360` deploy user (for example root:edu360, mode 0640). The deploy user
must be in the `docker` group to run Docker commands. Use strong, unique generated
test-only secrets; never commit this file.

```dotenv
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=127.0.0.1
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/edu360db
SPRING_DATASOURCE_USERNAME=edu_app
SPRING_DATASOURCE_PASSWORD=REPLACE_WITH_GENERATED_VALUE
PLATFORM_ADMIN_DATASOURCE_USERNAME=edu_platform
PLATFORM_ADMIN_DATASOURCE_PASSWORD=REPLACE_WITH_GENERATED_VALUE
POSTGRES_BOOTSTRAP_PASSWORD=REPLACE_WITH_UNIQUE_TEST_PASSWORD
KEYCLOAK_BASE_URL=http://127.0.0.1:8081
KEYCLOAK_PLATFORM_REALM=edu360-platform
KEYCLOAK_FRONTEND_CLIENT_ID=edu360-frontend
KEYCLOAK_MASTER_ADMIN_USERNAME=kcadmin
KEYCLOAK_MASTER_ADMIN_PASSWORD=REPLACE_WITH_UNIQUE_TEST_PASSWORD
KC_BOOTSTRAP_ADMIN_USERNAME=kcadmin
KC_BOOTSTRAP_ADMIN_PASSWORD=REPLACE_WITH_UNIQUE_TEST_PASSWORD
LOCAL_DEV_HOST_SUFFIX=.duckdns.org
PLATFORM_HOST_LABEL=platform
SECRETS_ENDPOINT_OVERRIDE=http://127.0.0.1:4566
CORS_ALLOWED_ORIGIN_PATTERNS=
```

Set `LOCAL_DEV_HOST_SUFFIX` to the suffix shared by the configured tenant hostnames.
For example, with `abc.duckdns.org` and `xyz.duckdns.org`, use `.duckdns.org`.
The leading dot is significant. The platform hostname must use the same suffix.

Create a separate `/etc/edu360/caddy.env`, root-owned and mode 0600:

```dotenv
ACME_EMAIL=admin@example.com
PLATFORM_HOSTNAME=platform.duckdns.org
TENANT_HOSTNAMES=abc.duckdns.org,xyz.duckdns.org
```

Replace the example DuckDNS hostnames with the exact records configured for the VM.
Set `PLATFORM_HOSTNAME` and every tenant hostname to the actual full DNS name; do
not include URL schemes or paths. DuckDNS records must resolve to the VM before Caddy
can obtain certificates.

## Build and run

1. Provision Ubuntu 24.04, a dedicated `edu360` user, Java 21, Node.js 22,
   Docker Engine/Compose v2, Maven, Caddy and rsync.
2. Add the `edu360` deploy user to the `docker` group and configure sudo for the
   required systemctl operations.
3. Clone this repository using a read-only deploy key; check out `uat`.
4. Create the two protected environment files above. Make `edu360.env` readable
   only by root and the restricted deploy group; keep `caddy.env` root-only.
5. Copy `docker/uat/Caddyfile` to `/etc/caddy/Caddyfile`.
6. Install `docker/uat/caddy.service.d/override.conf` under
   `/etc/systemd/system/caddy.service.d/override.conf`, then install
   `docker/uat/edu360-api.service` under
   `/etc/systemd/system/edu360-api.service`. Create `/var/log/edu360`
   and enable the services. Create writable runtime paths before starting the API:
   `sudo install -d -o edu360 -g edu360 /var/log/edu360 /var/lib/edu360/uploads`.
   Install and enable the units with `sudo systemctl daemon-reload`,
   `sudo systemctl enable caddy edu360-api`, and start Caddy after its environment
   override is installed. The deploy script restarts `edu360-api` after a successful build.
7. Start Compose and provision the database roles/schema and Keycloak realms/users.
   **This is currently a manual prerequisite on Ubuntu:** the checked-in database and
   Keycloak provisioning scripts are PowerShell-only. Do not proceed to public UAT
   sign-in until equivalent Linux provisioning has been implemented and verified.
   Apply the SQL migration sequence from `sql/run_all.sh` to a fresh UAT database,
   then provision the application roles with unique credentials. Do not reuse development
   passwords or apply `sql/run_all.sh` directly to the live UAT database: it creates and
   drops throw-away test databases.
8. Run `docker/uat/deploy-ubuntu.sh` as `edu360`. It refuses to run outside the
   `uat` branch or with a dirty worktree, fetches only `uat`, executes backend
   verification and frontend lint/tests/build, then deploys the build.

## Network restrictions

Allow inbound TCP 80 and 443 only. Do not expose 8080, 8081, 5433 or 4566.
Apply equivalent rules in Oracle Cloud networking and Ubuntu firewall. Verify
IPv4, IPv6 and Docker-managed firewall chains; Docker published ports can bypass
some host firewall configurations if bound to a public interface.

## Smoke checks

- `docker compose ps` reports PostgreSQL, Keycloak and LocalStack healthy.
- `ss -lntp` shows 5433, 8081, 4566 and 8080 bound only to 127.0.0.1.
- `systemctl status edu360-api caddy` reports both services active.
- Browser network panel shows API requests on the same HTTPS hostname, not
  direct calls to port 8080.
- Test each configured tenant hostname and confirm tenant-specific sign-in.
- Verify logs contain no secrets, and rotate all seeded/test credentials.

## Operational limitations

- This setup does not implement wildcard tenant DNS/certificate issuance,
  custom domains, production tenant-suffix configuration, reserved-subdomain
  enforcement, or token-host/tenant binding. See
  `docker/PRODUCTION_TENANT_ROUTING.txt` (P1-P11) before any production launch.
- The backend is deliberately run with `SPRING_PROFILES_ACTIVE=dev`, Keycloak uses
  `start-dev`, and LocalStack replaces AWS Secrets Manager. This is for isolated
  UAT only, not a production security configuration.
- Backups must be encrypted, off-host, access-controlled and restore-tested.
  A same-VM dump is not a production disaster-recovery plan.
