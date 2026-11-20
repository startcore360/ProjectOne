import { test, expect } from "@playwright/test";
import { activateTenant, apiFor, createTenant, json, makeInvoicePaid, openPlatformPage, requiredToken } from "./helpers";

test("E2E-001: onboard a tenant, pay its first invoice and activate it", async ({ page }) => {
  test.skip(!process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN || !process.env.EDU360_E2E_BILLING_MANAGER_TOKEN, "Requires Product Owner and Billing Manager access tokens from the local dev stack.");
  const token = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const api = await apiFor("platform", token);
  const billingApi = await apiFor("platform", requiredToken("EDU360_E2E_BILLING_MANAGER_TOKEN"));
  try {
    const tenant = await createTenant(api, "STANDALONE_SCHOOL");
    expect(tenant.firstAdminTemporaryPassword).toBeTruthy();
    await makeInvoicePaid(billingApi, api, tenant);
    await activateTenant(api, tenant);

    await openPlatformPage(page, token, "/platform/tenants");
    await expect(page.getByText(tenant.tenantName, { exact: false })).toBeVisible();
    await expect(page.getByText("Active", { exact: false })).toBeVisible();
  } finally {
    await api.dispose();
    await billingApi.dispose();
  }
});

test("E2E-035: standalone college reaches ACTIVE only after its invoice is paid", async ({ page }) => {
  test.skip(!process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN || !process.env.EDU360_E2E_BILLING_MANAGER_TOKEN, "Requires Product Owner and Billing Manager access tokens from the local dev stack.");
  const token = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const api = await apiFor("platform", token);
  const billingApi = await apiFor("platform", requiredToken("EDU360_E2E_BILLING_MANAGER_TOKEN"));
  try {
    const tenant = await createTenant(api, "STANDALONE_COLLEGE");
    const invoicesBeforePayment = await api.get(`/api/platform/invoices?tenantId=${tenant.tenantId}`);
    const existing = await json<Array<{ status: string }>>(invoicesBeforePayment);
    expect(existing.every((invoice) => invoice.status !== "PAID")).toBeTruthy();

    await makeInvoicePaid(billingApi, api, tenant.tenantId);
    await activateTenant(api, tenant);

    await openPlatformPage(page, token, "/platform/tenants");
    await expect(page.getByText(tenant.tenantName, { exact: false })).toBeVisible();
  } finally {
    await api.dispose();
    await billingApi.dispose();
  }
});
