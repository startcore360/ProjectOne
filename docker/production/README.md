# Production deployment (wildcard tenant domain)

This deployment bundle uses `edu360.com` only as a placeholder. Replace it with the registered production domain; no DNS name or certificate is assumed to exist.

## Traffic and trust boundaries

- Publish only nginx on TCP 80/443. The API listens on 8080 inside the Compose network and has no host-published port.
- Create a wildcard DNS record for `*.<EDU360_DOMAIN>` pointing to the public load balancer/host. Create the platform label `platform.<EDU360_DOMAIN>` on the same endpoint.
- Issue a wildcard TLS certificate for `*.<EDU360_DOMAIN>` using DNS-01 validation (Let's Encrypt DNS plugin or AWS Certificate Manager on an ALB). A wildcard certificate covers one label only; the nginx host regex rejects nested names and bare IPs.
- Mount the certificate chain as `fullchain.pem` and private key as `privkey.pem` under `EDU360_TLS_DIR`. Protect the private key and rotate it before expiry.
- The proxy forwards the original `Host` header. The app's production Axios base URL is same-origin. Swagger, OpenAPI docs and H2 console paths return 404.
- Never publish the API's 8080 port to the host or internet. Restrict database and Keycloak ingress to the API workload/network.

## Build and launch

1. Build the frontend with Node 22: `cd app && npm ci && npm run lint && npm test && npm run build`.
2. Build the API image from the repository root: `docker build -f api/Dockerfile -t edu360-api:production .`.
3. Copy `docker/production/deployment.env.example` and set `EDU360_DOMAIN`, `EDU360_API_ENV_FILE`, `EDU360_APP_DIST_DIR`, and `EDU360_TLS_DIR`. The API environment file must be stored outside Git with mode 0600 and supplied by the deployment secret manager.
4. The API env file must include the required variables in `application-prod.properties`: Postgres app and platform-admin datasource URLs/credentials; `KEYCLOAK_BASE_URL`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_REALM`, `KEYCLOAK_PLATFORM_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET`, `KEYCLOAK_FRONTEND_CLIENT_ID`, `KEYCLOAK_FRONTEND_REDIRECT_URIS`, `KEYCLOAK_IDENTITY_EVENTS_CLIENT_ID`, `KEYCLOAK_IDENTITY_EVENTS_CLIENT_SECRET`, `TENANT_HOST_SUFFIX`, `PLATFORM_HOST_LABEL`, `CORS_ALLOWED_ORIGIN`, `KEYCLOAK_PUBLIC_BASE_URL`, `SUPPORT_SESSION_SIGNING_SECRET`, `SUPPORT_SESSION_TENANT_BASE_DOMAIN` (the apex domain without a leading dot), and `S3_FILE_BUCKET`. Configure the AWS SDK default credential chain with a workload IAM role granting access only to the required bucket.
5. Use HTTPS redirect URI patterns, e.g. `https://*.<EDU360_DOMAIN>/*`; never include local HTTP development addresses in production.
6. Start with `docker compose --env-file /path/to/deployment.env -f docker/production/docker-compose.yml up -d --build`. Confirm both containers are running with `docker compose ... ps`, inspect API/nginx logs, and perform the staging smoke checks before opening DNS traffic.

## AWS / DNS setup checklist

- Create the DNS wildcard `*.<EDU360_DOMAIN>` and the platform host record.
- Request/validate the wildcard certificate using DNS-01. Keep the certificate and private key outside the image.
- Create a private S3 bucket for file storage. Enable block-public-access, versioning, server-side encryption and lifecycle retention. Grant the API workload `s3:PutObject`, `s3:GetObject`, and `s3:DeleteObject` only on the `tenants/*` prefix; bucket listing is not required by the application.
- Create a separate private backup bucket/prefix and a distinct backup workload role. Do not reuse the API file-storage role.
- Ensure Postgres and Keycloak are reachable only from the API workload. Do not expose port 8080.
- Configure Keycloak realm service accounts and redirect URIs using the production procedure documented in `KEYCLOAK_PRODUCTION_SETUP.md`.

## Operational caveats

The real domain, DNS provider, certificate issuer, AWS account/region, RDS instance, Keycloak deployment and production secrets are not present in this repository context. This bundle is deployable configuration, not evidence that DNS, TLS, IAM, RDS or Keycloak infrastructure has been provisioned. Do not mark the staging-only Done-when checklist complete until the corresponding external checks pass.

## Backup and release verification

The production backup CronJob template is `docker/production/backup-cronjob.yaml`. It is intentionally not deployable until A6 publishes a production-capable backup-agent image and the `edu360-backup-agent-production` secret/IAM role contract. The current A6 PowerShell script is development-only. See `docs/PRODUCTION_BACKUP_RUNBOOK.md`.

Before go-live, execute the security, 10× peak load, and restore rehearsal plan in `docs/PRODUCTION_VERIFICATION_PLAN.md`. The current repository state does not represent evidence that those external tests passed.
