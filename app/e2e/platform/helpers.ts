import { expect, type APIRequestContext, type Page, request } from "@playwright/test";
import { randomUUID } from "node:crypto";

export const platformOrigin = process.env.EDU360_E2E_PLATFORM_URL ?? "http://platform.edu360.localhost:5173";
export const apiPort = process.env.EDU360_E2E_API_PORT ?? "8080";

export function requiredToken(name: string): string {
  const token = process.env[name];
  expect(token, `Set ${name} after signing in to the local dev stack (including TOTP where required).`).toBeTruthy();
  return token!;
}

export async function apiFor(hostLabel: string, token: string): Promise<APIRequestContext> {
  return request.newContext({
    baseURL: `http://${hostLabel}.edu360.localhost:${apiPort}`,
    extraHTTPHeaders: { Authorization: `Bearer ${token}`, Accept: "application/json" },
    ignoreHTTPSErrors: true,
  });
}

export async function json<T>(response: Awaited<ReturnType<APIRequestContext["get"]>>): Promise<T> {
  expect(response.ok(), `API returned ${response.status()}: ${await response.text()}`).toBeTruthy();
  return await response.json() as T;
}

export type CreatedTenant = {
  tenantId: string;
  tenantName: string;
  subdomain: string;
  keycloakRealm: string;
  state: string;
  firstAdminEmail?: string;
  firstAdminTemporaryPassword?: string;
  rowVersion?: number;
};

export async function createTenant(api: APIRequestContext, kind: "UNIVERSITY_GROUP" | "STANDALONE_SCHOOL" | "STANDALONE_COLLEGE" = "STANDALONE_SCHOOL"): Promise<CreatedTenant> {
  const plansResponse = await billingApi.get("/api/platform/plans");
  const plans = await json<Array<{ planId: string; priceAmount: number; currencyCode: string; status: string }>>(plansResponse);
  const plan = plans.find((item) => item.status === "ACTIVE") ?? plans[0];
  expect(plan, "A seeded active plan is required for tenant provisioning.").toBeTruthy();

  const suffix = randomUUID().replaceAll("-", "").slice(0, 10);
  const name = `A8 E2E ${suffix}`;
  const response = await api.post("/api/platform/tenants", {
    tenantName: name,
    tenantType: kind,
    planId: plan.planId,
    subdomain: `a8-${suffix}`,
    isolationMode: "SHARED",
    firstAdminName: "EDU360 E2E Administrator",
    firstAdminEmail: `a8-${suffix}@example.test`,
    firstAdminMobile: "+919876543210",
    retentionMonths: 36,
    backupRetentionDays: 90,
    timeZone: "Asia/Kolkata",
    dataRegion: "IN",
  }, { headers: { "Idempotency-Key": randomUUID() } });
  const tenant = await json<CreatedTenant>(response);
  expect(tenant.tenantId).toBeTruthy();
  expect(tenant.state).toBe("TRIAL");
  expect(tenant.rowVersion).toBeTruthy();
  return tenant;
}

export async function makeInvoicePaid(billingApi: APIRequestContext, ownerApi: APIRequestContext, tenantId: string): Promise<string> {
  const plansResponse = await api.get("/api/platform/plans");
  const plans = await json<Array<{ planId: string; priceAmount: number; currencyCode: string }>>(plansResponse);
  const tenantsResponse = await billingApi.get("/api/platform/tenants?page=0&size=100");
  const tenants = await json<{ items: Array<{ tenantId: string; planId: string }> }>(tenantsResponse);
  const tenant = tenants.items.find((item) => item.tenantId === tenantId);
  expect(tenant, "Created tenant must be listed before invoicing.").toBeTruthy();
  const plan = plans.find((item) => item.planId === tenant!.planId);
  expect(plan).toBeTruthy();

  const now = new Date();
  const periodEnd = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 0));
  const periodStart = new Date(Date.UTC(periodEnd.getUTCFullYear(), periodEnd.getUTCMonth(), 1));
  const invoiceResponse = await billingApi.post("/api/platform/invoices", {
    tenantId,
    periodStart: periodStart.toISOString().slice(0, 10),
    periodEnd: periodEnd.toISOString().slice(0, 10),
    subtotalAmount: plan!.priceAmount,
    currencyCode: plan!.currencyCode ?? "INR",
  }, { headers: { "Idempotency-Key": randomUUID() } });
  const invoice = await json<{ invoiceId: string }>(invoiceResponse);

  await json(await ownerApi.post(`/api/platform/invoices/${invoice.invoiceId}/approval`, {}, {
    headers: { "Idempotency-Key": randomUUID() },
  }));
  await json(await billingApi.post(`/api/platform/invoices/${invoice.invoiceId}/send`));
  await json(await billingApi.post(`/api/platform/invoices/${invoice.invoiceId}/record-payment`, {
    paymentMode: "BANK_TRANSFER",
    paymentReference: `A8-E2E-${randomUUID()}`,
  }, { headers: { "Idempotency-Key": randomUUID() } }));
  return invoice.invoiceId;
}

export async function activateTenant(api: APIRequestContext, tenant: CreatedTenant): Promise<void> {
  const paymentReference = `A8-E2E-ACTIVATION-${randomUUID()}`;
  const response = await api.post(`/api/platform/tenants/${tenant.tenantId}/activation`, {
    paymentReference,
    note: "A8 automated end-to-end verification",
  }, { headers: { "Idempotency-Key": randomUUID(), "If-Match": `"${tenant.rowVersion}"` } });
  const activated = await json<{ state: string }>(response);
  expect(activated.state).toBe("ACTIVE");
}

export async function attachSessionToken(page: Page, token: string): Promise<void> {
  await page.addInitScript((value) => {
    sessionStorage.setItem("edu360.accessToken", value);
  }, token);
}

export async function openPlatformPage(page: Page, token: string, path: string): Promise<void> {
  await attachSessionToken(page, token);
  await page.goto(new URL(path, platformOrigin).toString());
  await expect(page.locator("#root")).toBeVisible();
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
}
