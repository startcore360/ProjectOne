import { test, expect } from "@playwright/test";
import { openPlatformPage } from "./helpers";

const roles = [
  ["Product Owner", "EDU360_E2E_PRODUCT_OWNER_TOKEN"],
  ["Platform Admin", "EDU360_E2E_PLATFORM_ADMIN_TOKEN"],
  ["Billing Manager", "EDU360_E2E_BILLING_MANAGER_TOKEN"],
  ["Support Manager", "EDU360_E2E_SUPPORT_MANAGER_TOKEN"],
  ["Release Manager", "EDU360_E2E_RELEASE_MANAGER_TOKEN"],
  ["Data Protection Officer", "EDU360_E2E_DPO_TOKEN"],
] as const;

for (const [role, envName] of roles) {
  test(`platform landing page loads for ${role}`, async ({ page }) => {
    const token = process.env[envName];
    test.skip(!token, `Set ${envName} using a real local-dev login for the ${role} role.`);
    await openPlatformPage(page, token!, "/platform");
    await expect(page.locator("#root")).toBeVisible();
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
  });
}
