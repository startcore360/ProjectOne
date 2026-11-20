# Platform Playwright E2E suite

The suite is in `app/e2e/platform/` and is designed to run against the local dev stack started by `dev-up.ps1` (or an equivalent staging stack).

## Install and run

```powershell
cd app
npm ci
npm install --no-save --package-lock=false @playwright/test@1.56.1
npx playwright install chromium
npm run e2e
```

The Playwright runner is pinned in CI and installed without changing the application's production dependency lockfile. The application itself does not ship Playwright.

## Required test credentials and fixtures

Use dedicated test accounts and disposable tenants only. Export short-lived bearer tokens after completing the normal local login/MFA flow. Never commit tokens or storage state.

- `EDU360_E2E_PLATFORM_URL`: defaults to `http://platform.edu360.localhost:5173`.
- `EDU360_E2E_API_PORT`: defaults to `8080`.
- `EDU360_E2E_PRODUCT_OWNER_TOKEN`: Product Owner bearer token (tenant creation, activation, deletion request, invoice approval).
- `EDU360_E2E_BILLING_MANAGER_TOKEN`: Billing Manager bearer token (invoice creation, sending and payment).
- `EDU360_E2E_PLATFORM_ADMIN_TOKEN`, `EDU360_E2E_SUPPORT_MANAGER_TOKEN`, `EDU360_E2E_RELEASE_MANAGER_TOKEN`, `EDU360_E2E_DPO_TOKEN`: one token per platform role for landing-page authorization checks.
- `EDU360_E2E_TENANT_TOKEN`, `EDU360_E2E_TENANT_ID`, `EDU360_E2E_TENANT_SUBDOMAIN`: tenant administrator token and matching tenant identifiers for the support-session journey.
- `EDU360_E2E_DPO_READY_REQUEST_ID`: disposable deletion request already in `PENDING_DPO`, with export produced and retention/waiver gates satisfied. The test approves it and marks its tenant deleted.
- `EDU360_E2E_BILLING_TENANT_ID`: active tenant with a renewal date inside the next five years. The billing-cycle test creates and pays its renewal invoice.
- For the gated self-hosted GitHub Actions job, set repository variable `EDU360_E2E_ENABLED=true`, configure the `edu360-e2e` self-hosted runner with the dev stack available, and add the corresponding secrets/variables.

## Coverage

- E2E-001: create a standalone school, verify the first-admin temporary credential is returned, invoice/pay, activate, and verify the active tenant in the platform UI.
- E2E-033: tenant creates a support ticket and grants access; platform opens a time-limited read-only support session; the tenant support route consumes the session token.
- E2E-035: standalone college go-live is gated by payment and activation.
- DPO decision: a separate DPO account approves a request only after export and retention gates are satisfied.
- Billing cycle to paid: a due active tenant receives a cycle invoice; Product Owner approval and Billing Manager send/payment complete the maker-checker flow.
- Platform landing-page authorization: one independent account for each of the six platform roles.

Tests requiring external fixture tokens/IDs are skipped when those variables are absent. A skipped test is not a pass. The gated CI job runs only on manual dispatch with `EDU360_E2E_ENABLED=true`.

The E2E suite creates tenants, invoices and deletion decisions. Run it only against an isolated development/staging database and Keycloak realm. Do not point it at production.
