# Production backup scheduling and restore handoff (E10-S7)

**Status: SCHEDULE TEMPLATE ADDED; EXECUTION BLOCKED ON A6.**

The existing `docker/backup/backup-agent.ps1` is explicitly development-only. A8 must not alter or replace the A6 backup agent. The production CronJob below is therefore an integration point for the production-capable image published by A6, not a claim that the current PowerShell script can safely run in production.

## Schedule

- Kubernetes CronJob: `docker/production/backup-cronjob.yaml`
- Schedule: daily at 02:00 Asia/Kolkata
- Concurrency: one job at a time; missed runs have a one-hour start deadline
- Retry: two retries; four-hour active deadline
- Identity: dedicated workload service account with IAM access only to the backup bucket/prefix and required RDS snapshot/metadata APIs
- Secrets: Kubernetes secret `edu360-backup-agent-production`, populated from the deployment secret manager; never commit it

## A6 handoff contract

Before deploying the CronJob, A6 must publish and document a production image and confirm all of the following:

1. The image has a non-root entrypoint and accepts the `BACKUP_ENVIRONMENT=production` setting.
2. It creates an encrypted, integrity-verifiable backup using the approved RDS snapshot or PostgreSQL dump strategy.
3. It writes the backup artifact to the private backup bucket using workload IAM credentials (no static AWS keys in the image).
4. It posts the outcome, timestamp, checksum, restore point, and failure details to the existing backup-status integration or its approved API/DB writer.
5. It supports idempotent retries, retention/lifecycle rules, failure alerting, and a restore rehearsal.
6. It does not use development-only database credentials, LocalStack endpoints, or the local PowerShell scheduler.

The current repository does not establish the final A6 image name, secret keys, status callback contract, retention policy, or production RTO/RPO. Replace the explicit image placeholder and secret reference only after A6 supplies that contract. Do not deploy the placeholder image.

## Operations

- Verify daily job completion and alert on missed/failed backups.
- Retain immutable/versioned backup artifacts according to the approved retention schedule.
- Perform a restore rehearsal to an isolated target at least at the cadence approved by the data protection and platform operations owners.
- Keep the backup workload IAM role separate from the API's tenant-file S3 role.
- See `docs/PRODUCTION_VERIFICATION_PLAN.md` for the restore evidence checklist.
