# EDU 360 production verification plan (E10-S8)

**Execution status: NOT RUN.** This document defines the staging verification protocol. No production domain, staging credentials, AWS account, RDS instance, Keycloak endpoint, load profile, or restore target was supplied in this repository session. Do not record a pass until evidence is attached.

## 1. Entry criteria

- Deploy the production profile behind the nginx reverse proxy in an isolated staging environment.
- Configure the real wildcard DNS record and DNS-01 wildcard TLS certificate for the registered domain.
- Provision Keycloak service accounts using `docs/KEYCLOAK_PRODUCTION_SETUP.md`.
- Configure S3 file storage with a private bucket and workload IAM role.
- Configure the A6 production backup agent and scheduled job; the current PowerShell backup script is development-only.
- Obtain written approval for the environment's RTO/RPO, peak traffic baseline, test data, test accounts, and maintenance window. These values are not inferred here.
- Seed at least two tenant realms, one platform realm, one custom-domain tenant only if custom domains are in the approved test scope, and test users for each platform role.

## 2. Security and penetration verification

Run from an approved external test runner against staging only.

| ID | Test | Expected result / evidence |
|---|---|---|
| SEC-01 | Tenant host boundary: tenant A token on tenant B host; tenant token on platform host; platform token on tenant host | HTTP 403 `TENANT_HOST_MISMATCH`; no tenant data returned |
| SEC-02 | Host parsing: nested labels, bare apex, look-alike suffix, mixed case, trailing dot, untrusted forwarded-host | Only exact configured one-label tenant hosts resolve; all invalid forms fail closed |
| SEC-03 | JWT issuer and realm binding | Unknown issuer, disabled realm, invalid signature, expired token, and wrong tenant all rejected |
| SEC-04 | RLS/IDOR: enumerate tenant IDs, file IDs, support ticket IDs and institution IDs across tenants | No cross-tenant read/write; database RLS remains enforced under the application role |
| SEC-05 | CORS and browser origin | Only the exact configured platform origin is allowed; wildcard origins are not emitted |
| SEC-06 | Keycloak redirect URI | Only HTTPS wildcard-domain redirect URIs are accepted; localhost and unregistered domains rejected |
| SEC-07 | Keycloak service-account least privilege | Master client can create/delete realms only; tenant client can administer users only in its own realm; identity-events client cannot administer users |
| SEC-08 | File storage | S3 bucket is private; object keys remain under `tenants/{tenantId}/{fileId}`; traversal and cross-tenant access denied |
| SEC-09 | Secrets and headers | No secrets in image, source, logs, or browser bundles; HSTS, nosniff, frame denial and TLS 1.2+ observed |
| SEC-10 | API exposure | Port 8080 is not publicly reachable; Swagger, OpenAPI docs and H2 console return 404 |
| SEC-11 | Rate limiting / abuse | Auth, OTP, support-session, upload and administrative endpoints resist agreed abuse scenarios |
| SEC-12 | Audit trail | Privileged actions, tenant lifecycle, support access and DPO decisions emit attributable audit records |

Record tool/version, test date, target, tester, finding severity, request/response evidence (redacted), remediation owner, and retest status. Any critical/high finding blocks go-live.

## 3. Load and resilience verification

**Required load target:** sustain 10× the approved measured peak traffic for 60 minutes in staging.

1. Establish baseline peak RPS and concurrency from representative telemetry; record the source and measurement window.
2. Prepare a staging dataset with realistic tenant count, user count, file sizes, and platform/tenant request mix.
3. Warm up for 10 minutes, ramp from baseline to 10× peak over 15 minutes, hold 10× for 60 minutes, then ramp down over 10 minutes.
4. Include tenant login/token validation, tenant-scoped reads/writes, platform tenant lookup, file metadata and S3 upload/download, support ticket/grant read paths, and invoice listing. Do not use real payment providers or real personal data.
5. Monitor API p50/p95/p99 latency, error rate, CPU, heap/GC, database connections/locks, Keycloak token latency, S3 latency/5xx, nginx 4xx/5xx, queue depth, and saturation.
6. Inject controlled dependency degradation (Keycloak latency, S3 throttling, one database connection pool exhaustion) and verify bounded failure, no cross-tenant fallback, and recovery.
7. Compare results with the approved SLOs. Do not invent latency or error-rate targets; record the signed-off thresholds before the run.

The 10×/60-minute requirement is a test target, not a claim that the current environment has passed it.

## 4. Backup and restore rehearsal

Run only after the A6 production backup agent and schedule are available.

1. Capture the backup job run ID, database snapshot/dump identifier, S3 object URI, checksum, encryption mode, and completion timestamp.
2. Restore into a newly provisioned isolated database and isolated object-store prefix. Never overwrite staging or production.
3. Apply no new migrations until the restored schema version has been recorded; verify migration checksums and schema version first.
4. Verify tenant/platform schema, RLS policies, role grants, indexes, reference data, tenant metadata, Keycloak realm configuration, and file object metadata.
5. Restore representative S3 objects and compare SHA-256 checksums against pre-backup checksums.
6. Verify a tenant user can access only their own restored tenant data; verify a platform user cannot bypass tenant isolation.
7. Execute smoke tests for login, tenant resolution, a read/write transaction, file download, invoice retrieval, support ticket retrieval, and DPO deletion status.
8. Measure actual recovery time and data loss window. Compare against approved RTO/RPO; if no approved values exist, the rehearsal cannot pass.
9. Record cleanup of the isolated restore target and evidence retention location.

## 5. Go-live evidence record

Copy this table into the release record and attach evidence.

| Field | Value |
|---|---|
| Environment / domain | NOT RUN |
| Build commit | NOT RUN |
| Test window / timezone | NOT RUN |
| Security tester / report | NOT RUN |
| Peak RPS baseline source | NOT APPROVED |
| 10× load result / report | NOT RUN |
| Backup run ID / object URI / checksum | NOT RUN |
| Restore target / elapsed time / data loss | NOT RUN |
| Approved RTO / RPO | GOVERNANCE SIGN-OFF REQUIRED |
| Critical/high findings | NOT RUN |
| Release decision / approver | NOT RUN |

Go-live is blocked until all required tests pass, evidence is attached, and external infrastructure owners sign off.
