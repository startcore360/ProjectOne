# EDU 360 CI/CD Engineering Standard

## 1. Purpose and delivery model

This standard defines the engineering controls for building, testing, securing and eventually releasing EDU 360. The application is a React/Vite/TypeScript frontend and a Java 21/Spring Boot backend, with PostgreSQL as the target relational database.

The initial implementation is CI-first. It validates changes and retains build outputs; it does not deploy to an environment. Production deployment must not be enabled until hosting, database operations, environment ownership, secrets, backup and recovery, and release approval are agreed.

## 2. Pipeline stages

### Stage 1 — Continuous integration

For every push and pull request:
- Frontend: Node.js 22, `npm ci`, ESLint, the complete Vitest suite, and the TypeScript/Vite production build.
- Backend: Java 21 and Maven `verify`.
- Retain frontend distribution and backend JAR as commit-associated workflow artifacts.
- Cancel superseded runs for the same workflow/ref.

The frontend build script already runs `tsc -b` before Vite, so the build includes TypeScript project checks. Do not replace the full test suite with changed-file-only tests.

### Stage 2 — Security and dependency assurance

The workflow includes:
- npm audit with a high-severity failure threshold.
- OWASP Dependency-Check for Maven dependencies with a CVSS 7 failure threshold.
- Gitleaks secret scanning.
- GitHub Dependency Review on pull requests.
- CodeQL static analysis for Java/Kotlin and JavaScript/TypeScript.
- Dependabot should be enabled for npm, Maven and GitHub Actions updates (see repository settings / follow-up configuration).

Review findings and exceptions explicitly. Do not suppress a vulnerability without recording the affected dependency, reason, owner and expiry/review date. Security checks are not a substitute for GitHub secret-scanning/push-protection repository settings.

## 3. PostgreSQL migration validation

The database validation job provisions an ephemeral PostgreSQL 16 service and invokes `sql/run_all.sh` when the reviewed EDU 360 SQL package is present. The supplied V4.0 SQL bundle contains a runner that creates two disposable databases, applies schema/rule/binding/index and V4.0 scripts, runs acceptance tests, validates reference data counts and checks idempotent reference-data loading.

**Repository integration gap:** the SQL bundle supplied during design review is not currently present under `sql/` in the GitHub source branch. Until `sql/run_all.sh` and all referenced SQL files are committed to the repository, the job emits a visible warning and does not claim that database migrations were tested. Do not make this job a required merge check until the SQL assets are versioned and the job executes successfully.

Database change policy:
- Run validation only against disposable CI databases, never production.
- Test fresh installation and supported upgrade paths.
- Keep schema changes and rollback/forward-fix guidance under version control.
- Run production migrations as a separately controlled release operation, not automatically on every application startup.
- Never seed demo/reference data into production implicitly.
- Back up and verify recoverability before high-risk production schema changes.
- Reconcile the existing SQL release scripts before selecting Flyway or Liquibase; do not introduce a second migration authority without a deliberate conversion plan.

## 4. Pull-request and branch protection

Configure GitHub repository rules for the protected integration branch:
- Require pull requests; disallow direct pushes for normal contributors.
- Require at least one approving review.
- Dismiss stale approvals when new commits are pushed, where appropriate.
- Require all conversations to be resolved.
- Require the frontend, backend, security, dependency-review (for PRs), CodeQL and database-validation checks after each check is stable and consistently reported.
- Require branches to be up to date before merge if the team's merge strategy supports it.
- Block force pushes and branch deletion.
- Limit bypass permissions to explicitly designated repository administrators.

These are repository-level GitHub settings and cannot be enforced by adding workflow YAML alone. The repository administrator must configure and verify the ruleset. Do not mark the database job as required while the SQL package is absent.

## 5. Artifact and release integrity

- Associate artifacts with the source commit SHA.
- Build once and promote the same artifact through environments; do not rebuild separately for production.
- Add an SBOM and artifact provenance/attestation before formal production releases.
- Use immutable image digests if container publishing is introduced.
- Keep deployment credentials out of source control; prefer short-lived workload identity federation where supported.
- Use protected GitHub Environments and required reviewers for production.

## 6. Deployment and operations — intentionally not enabled

No hosting provider, container registry, production environment or deployment credentials are assumed. Once those are selected, implement:
1. Staging deployment from a successful CI artifact.
2. Smoke tests and readiness/health checks.
3. Protected production deployment with explicit approval.
4. Deployment concurrency controls.
5. Rollback or forward-fix runbook.
6. Post-deployment monitoring, alerting, log retention, database backup verification and recovery exercises.

A successful CI run is not production authorization.

## 7. Follow-up acceptance checklist

- [ ] Confirm workflow commands pass against the current repository.
- [ ] Commit the reviewed SQL bundle and remove the conditional migration-validation warning.
- [ ] Verify PostgreSQL 16 migration/test execution from a clean CI database.
- [ ] Configure GitHub branch rules and required checks.
- [ ] Enable Dependabot for npm, Maven and Actions.
- [ ] Review and triage security findings.
- [ ] Establish coverage baseline and thresholds.
- [ ] Decide hosting, registry, environments, release approvals and operational ownership before adding deployment.
