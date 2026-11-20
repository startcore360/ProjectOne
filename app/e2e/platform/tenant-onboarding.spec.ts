import { test, expect } from "@playwright/test";
import { activateTenant, apiFor, createTenant, json, makeInvoicePaid, openPlatformPage, requiredToken } from "./helpers";

test("E2E-001: onboard a tenant, pay its first invoice and activate it", async ({ page }) => {
  test.skip(!process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN, "Requires a Product Owner access token from the local dev stack.");
  const token = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const api = await apiFor("platform", token);
  try {
    const tenant = await createTenant(api, "UNIVERSITY_GROUP" as never);
    expect(tenant.firstAdminTemporaryPassword).toBeTruthy();
    await makeInvoicePaid(api, tenant.tenantId);
    await activateTenant(api, tenant);

    await openPlatformPage(page, token, "/platform/tenants");
    await expect(page.getByText(tenant.tenantName, { exact: false })).toBeVisible();
    await expect(page.getByText("Active", { exact: false })).toBeVisible();
  } finally {
    await api.dispose();
  }
});

test("E2E-035: standalone college reaches ACTIVE only after its invoice is paid", async ({ page }) => {
  test.skip(!process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN, "Requires a Product Owner access token from the local dev stack.");
  const token = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const api = await apiFor("platform", token);
  try {
    const tenant = await createTenant(api, "STANDALONE_COLLEGE");
    const invoicesBeforePayment = await api.get(`/platform/invoices?tenantId=${tenant.tenantId}`);
    const existing = await json<Array<{ status: string }>>(invoicesBeforePayment);
    expect(existing.every((invoice) => invoice.status !== "PAID")).toBeTruthy();

    await makeInvoicePaid(api, tenant.tenantId);
    await activateTenant(api, tenant);

    await openPlatformPage(page, token, "/platform/tenants");
    await expect(page.getByText(tenant.tenantName, { exact: false })).toBeVisible();
  } finally {
    await api.dispose();
  }
});
