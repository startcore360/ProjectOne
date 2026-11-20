import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { apiFor, createTenant, json, makeInvoicePaid, openPlatformPage, requiredToken } from "./helpers";

test("DPO decides a deletion request after Product Owner confirmation", async ({ page }) => {
  test.skip(
    !process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN || !process.env.EDU360_E2E_DPO_TOKEN,
    "Requires separate Product Owner and DPO tokens; TPA-04 separation is intentional.",
  );
  const owner = await apiFor("platform", requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN"));
  const dpo = await apiFor("platform", requiredToken("EDU360_E2E_DPO_TOKEN"));
  try {
    const tenant = await createTenant(owner);
    const requestResponse = await owner.post("/api/platform/deletion-requests", {
      tenantId: tenant.tenantId,
      origin: "PRODUCT_OWNER",
    }, { headers: { "Idempotency-Key": randomUUID() } });
    const deletion = await json<{ deletionRequestId: string; rowVersion?: number }>(requestResponse);
    const confirmResponse = await owner.post(`/api/platform/deletion-requests/${deletion.deletionRequestId}/confirm`, {
      waiveRetentionWait: false,
    }, {
      headers: {
        "Idempotency-Key": randomUUID(),
        ...(deletion.rowVersion ? { "If-Match": `"${deletion.rowVersion}"` } : {}),
      },
    });
    await json(confirmResponse);

    const decision = await dpo.post(`/api/platform/deletion-requests/${deletion.deletionRequestId}/decision`, {
      decision: "APPROVED",
      remark: "A8 E2E DPO approval; purge remains deferred until retention expiry.",
    }, { headers: { "Idempotency-Key": crypto.randomUUID() } });
    const result = await json<{ status: string }>(decision);
    expect(result.status).toBe("APPROVED");

    await openPlatformPage(page, requiredToken("EDU360_E2E_DPO_TOKEN"), "/platform/data-protection");
    await expect(page.locator("#root")).toBeVisible();
  } finally {
    await owner.dispose();
    await dpo.dispose();
  }
});

test("Billing cycle creates a payable invoice and payment moves it to PAID", async ({ page }) => {
  test.skip(!process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN || !process.env.EDU360_E2E_BILLING_MANAGER_TOKEN, "Requires Product Owner and Billing Manager tokens from the local dev stack.");
  const token = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const api = await apiFor("platform", token);
  const billingApi = await apiFor("platform", requiredToken("EDU360_E2E_BILLING_MANAGER_TOKEN"));
  try {
    const tenant = await createTenant(api);
    const invoiceId = await makeInvoicePaid(billingApi, api, tenant.tenantId);
    const response = await billingApi.get(`/api/platform/invoices?tenantId=${tenant.tenantId}`);
    const invoices = await json<Array<{ invoiceId: string; status: string }>>(response);
    expect(invoices.find((item) => item.invoiceId === invoiceId)?.status).toBe("PAID");

    await openPlatformPage(page, token, "/platform/invoices");
    await expect(page.locator("#root")).toBeVisible();
  } finally {
    await api.dispose();
    await billingApi.dispose();
  }
});
