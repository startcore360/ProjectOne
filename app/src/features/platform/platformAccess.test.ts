import { beforeEach, describe, expect, it, vi } from "vitest";
import api from "../../services/axios";
import { loadPlatformPermissions, filterPlatformNavigation, platformLandingPath } from "./platformAccess";

vi.mock("../../services/axios", () => ({ default: { get: vi.fn() } }));

const mockedApi = vi.mocked(api);

describe("platform navigation permissions", () => {
  beforeEach(() => vi.clearAllMocks());

  it("filters every page against the server-provided action set", () => {
    const items = [
      { label: "Overview", anyOf: null },
      { label: "Tenants", anyOf: ["TENANT_CREATE", "TENANT_SETTINGS"] },
      { label: "Invoices", anyOf: ["INVOICE_MANAGE"] },
      { label: "Releases", anyOf: ["RELEASE_MANAGE"] },
      { label: "Data protection", anyOf: ["TENANT_EXPORT_APPROVE", "TENANT_DELETION_APPROVE"] },
    ];
    expect(filterPlatformNavigation(items, ["INVOICE_MANAGE"])).toEqual([items[0], items[2]]);
    expect(filterPlatformNavigation(items, [])).toEqual([items[0]]);
  });

  it.each([
    ["PRODUCT_OWNER", "/platform"],
    ["PLATFORM_ADMIN", "/platform"],
    ["BILLING_MANAGER", "/platform"],
    ["SUPPORT_MANAGER", "/platform"],
    ["RELEASE_MANAGER", "/platform/releases"],
    ["DATA_PROTECTION_OFFICER", "/platform/data-protection"],
  ])("sends %s to its role landing page", (role, path) => {
    expect(platformLandingPath(role)).toBe(path);
  });

  it("loads the server permission list and dispatches it without substituting role defaults", async () => {
    mockedApi.get.mockResolvedValue({ data: { actions: ["INVOICE_MANAGE"] } } as never);
    const dispatch = vi.fn();
    await loadPlatformPermissions(dispatch as never, "platform-user-1");
    expect(mockedApi.get).toHaveBeenCalledWith("/platform/me/permissions");
    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ userId: "platform-user-1", actions: ["INVOICE_MANAGE"] }),
    }));
  });

  it("fails closed for navigation when the server permission request fails", async () => {
    mockedApi.get.mockRejectedValue(new Error("offline"));
    const dispatch = vi.fn();
    await loadPlatformPermissions(dispatch as never, "platform-user-2");
    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({ userId: "platform-user-2", actions: [] }),
    }));
  });
});
