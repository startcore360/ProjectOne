import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { apiFor, json, openPlatformPage, requiredToken } from "./helpers";

test("DPO decides a deletion request only after export and retention readiness", async ({ page }) => {
  test.skip(
    !process.env.EDU360_E2E_DPO_TOKEN || !process.env.EDU360_E2E_DPO_READY_REQUEST_ID,
    "Requires a disposable PENDING_DPO request whose export and retention gates are already satisfied.",
  );
  const token = requiredToken("EDU360_E2E_DPO_TOKEN");
  const api = await apiFor("platform", token);
  try {
    const requestId = process.env.EDU360_E2E_DPO_READY_REQUEST_ID!;
    const decision = await api.post(`/api/platform/deletion-requests/${requestId}/decision`, {
      decision: "APPROVED",
      remark: "A8 E2E DPO approval on an isolated, export-ready test tenant.",
    }, { headers: { "Idempotency-Key": randomUUID() } });
    const result = await json<{ status: string }>(decision);
    expect(result.status).toBe("APPROVED");

    await openPlatformPage(page, token, "/platform/data-protection");
    await expect(page.locator("#root")).toBeVisible();
  } finally {
    await api.dispose();
  }
});

test("Billing cycle generates a renewal invoice and maker-checker records payment", async ({ page }) => {
  test.skip(
    !process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN ||
    !process.env.EDU360_E2E_BILLING_MANAGER_TOKEN ||
    !process.env.EDU360_E2E_BILLING_TENANT_ID,
    "Requires Product Owner/Billing Manager tokens and an active tenant due for renewal.",
  );
  const ownerToken = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const owner = await apiFor("platform", ownerToken);
  const billing = await apiFor("platform", requiredToken("EDU360_E2E_BILLING_MANAGER_TOKEN"));
  try {
    const today = new Date();
    const periodStart = today.toISOString().slice(0, 10);
    const future = new Date(Date.UTC(today.getUTCFullYear() + 5, today.getUTCMonth(), today.getUTCDate()));
    const cycleResponse = await billing.post("/api/platform/billing-cycles", {
      periodStart,
      periodEnd: future.toISOString().slice(0, 10),
    }, { headers: { "Idempotency-Key": randomUUID() } });
    const cycle = await json<{ created: Array<{ invoiceId: string; tenantId: string }>; skipped: Array<{ tenantId: string; reason: string }> }>(cycleResponse);
    const renewalInvoice = cycle.created.find((item) => item.tenantId === process.env.EDU360_E2E_BILLING_TENANT_ID);
    expect(renewalInvoice, "The seeded active tenant must be due for renewal in this billing-cycle window.").toBeTruthy();

    await json(await owner.post(`/api/platform/invoices/${renewalInvoice!.invoiceId}/approval`, {}, {
      headers: { "Idempotency-Key": randomUUID() },
    }));
    await json(await billing.post(`/api/platform/invoices/${renewalInvoice!.invoiceId}/send`));
    await json(await billing.post(`/api/platform/invoices/${renewalInvoice!.invoiceId}/record-payment`, {
      paymentMode: "BANK_TRANSFER",
      paymentReference: `A8-CYCLE-${randomUUID()}`,
    }, { headers: { "Idempotency-Key": randomUUID() } }));

    const invoices = await json<Array<{ invoiceId: string; status: string }>>(
      await billing.get(`/api/platform/invoices?tenantId=${process.env.EDU360_E2E_BILLING_TENANT_ID}`),
    );
    expect(invoices.find((item) => item.invoiceId === renewalInvoice!.invoiceId)?.status).toBe("PAID");
    await openPlatformPage(page, ownerToken, "/platform/invoices");
    await expect(page.locator("#root")).toBeVisible();
  } finally {
    await owner.dispose();
    await billing.dispose();
  }
});
