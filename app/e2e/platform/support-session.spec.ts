import { test, expect } from "@playwright/test";
import { apiFor, json, requiredToken } from "./helpers";

test("E2E-033: tenant grants ticket-bound support access and platform opens a read-only session", async ({ page }) => {
  test.skip(
    !process.env.EDU360_E2E_TENANT_TOKEN || !process.env.EDU360_E2E_TENANT_ID || !process.env.EDU360_E2E_PRODUCT_OWNER_TOKEN,
    "Requires tenant and Product Owner tokens plus a tenant ID from the local dev stack.",
  );
  const tenantToken = requiredToken("EDU360_E2E_TENANT_TOKEN");
  const tenantId = requiredToken("EDU360_E2E_TENANT_ID");
  const ownerToken = requiredToken("EDU360_E2E_PRODUCT_OWNER_TOKEN");
  const tenantApi = await apiFor(process.env.EDU360_E2E_TENANT_SUBDOMAIN ?? "abc", tenantToken);
  const platformApi = await apiFor("platform", ownerToken);
  try {
    const ticketResponse = await tenantApi.post(`/api/tenants/${tenantId}/support-tickets`, {
      subject: `A8 support test ${Date.now()}`,
      description: "Automated E2E validation of ticket-bound support access.",
      priority: "MEDIUM",
      category: "OTHER",
    });
    const ticket = await json<{ supportTicketId: string; ticketNumber: string }>(ticketResponse);

    const grantResponse = await tenantApi.post(`/api/tenants/${tenantId}/support-access-grants`, {
      ticketId: ticket.supportTicketId,
      durationHours: 1,
    });
    const grant = await json<{ supportAccessGrantId: string }>(grantResponse);
    expect(grant.supportAccessGrantId).toBeTruthy();

    const sessionResponse = await platformApi.post("/api/platform/support-sessions", {
      ticketId: ticket.supportTicketId,
    });
    const session = await json<{ token: string; expiresAt: string; tenantLoginAddress: string }>(sessionResponse);
    expect(session.token).toBeTruthy();
    expect(new Date(session.expiresAt).getTime()).toBeGreaterThan(Date.now());

    const tenantHost = process.env.EDU360_E2E_TENANT_SUBDOMAIN ?? "abc";
    const tenantOrigin = session.tenantLoginAddress || `http://${tenantHost}.edu360.localhost:5173`;
    const fragment = new URLSearchParams({
      support_session: session.token,
      expires_at: session.expiresAt,
      ticket_number: ticket.ticketNumber,
      platform_origin: process.env.EDU360_E2E_PLATFORM_URL ?? "http://platform.edu360.localhost:5173",
    });
    await page.goto(`${tenantOrigin}/support#${fragment.toString()}`);
    await expect(page.locator("#root")).toBeVisible();
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem("edu360.support-session.token")))
      .toBe(session.token);
  } finally {
    await tenantApi.dispose();
    await platformApi.dispose();
  }
});
