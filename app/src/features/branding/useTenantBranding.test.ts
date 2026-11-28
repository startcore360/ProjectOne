import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useTenantBranding, clearBrandingCache } from "./useTenantBranding";
import api from "../../services/axios";

vi.mock("../../services/axios");

describe("useTenantBranding", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearBrandingCache();
    document.documentElement.removeAttribute("style");
  });

  it("fetches branding and logo on initial mount and caches them for subsequent mounts", async () => {
    const mockBranding = {
      hasLogo: true,
      logoVersion: "1",
      primary: "#123456",
      accent: "#abcdef",
    };
    const mockBlob = new Blob(["fake-logo"], { type: "image/png" });

    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url.includes("/branding/logo")) {
        return Promise.resolve({ data: mockBlob });
      }
      if (url.includes("/branding")) {
        return Promise.resolve({ data: mockBranding });
      }
      return Promise.reject(new Error("not found"));
    });

    const { result, unmount } = renderHook(() => useTenantBranding("tenant-1"));

    await waitFor(() => {
      expect(result.current.logoUrl).toBeTruthy();
    });

    expect(api.get).toHaveBeenCalledWith("/tenants/tenant-1/branding");
    expect(api.get).toHaveBeenCalledWith("/tenants/tenant-1/branding/logo", { responseType: "blob" });
    expect(document.documentElement.style.getPropertyValue("--color-action-primary")).toBe("#123456");
    expect(document.documentElement.style.getPropertyValue("--color-accent")).toBe("#abcdef");

    unmount();
    // After unmount, CSS variables applied by the unmounted instance are cleaned up
    expect(document.documentElement.style.getPropertyValue("--color-action-primary")).toBe("");

    // Reset api mock call history
    vi.mocked(api.get).mockClear();

    // Re-mount hook with the same tenantId
    const { result: remountResult } = renderHook(() => useTenantBranding("tenant-1"));

    // LogoUrl is immediately available from cache
    expect(remountResult.current.logoUrl).toBeTruthy();
    // Tokens are reapplied
    expect(document.documentElement.style.getPropertyValue("--color-action-primary")).toBe("#123456");
    expect(document.documentElement.style.getPropertyValue("--color-accent")).toBe("#abcdef");

    // Network request was NOT fired again!
    expect(api.get).not.toHaveBeenCalled();
  });

  it("handles tenant switching correctly", async () => {
    const mockBrandingA = { hasLogo: false, logoVersion: null, primary: "#111111", accent: null };
    const mockBrandingB = { hasLogo: false, logoVersion: null, primary: "#222222", accent: null };

    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === "/tenants/tenant-a/branding") return Promise.resolve({ data: mockBrandingA });
      if (url === "/tenants/tenant-b/branding") return Promise.resolve({ data: mockBrandingB });
      return Promise.reject(new Error("not found"));
    });

    const { rerender } = renderHook(({ tid }: { tid: string | null }) => useTenantBranding(tid), {
      initialProps: { tid: "tenant-a" as string | null },
    });

    await waitFor(() => {
      expect(document.documentElement.style.getPropertyValue("--color-action-primary")).toBe("#111111");
    });

    // Switch to tenant-b
    rerender({ tid: "tenant-b" });

    await waitFor(() => {
      expect(document.documentElement.style.getPropertyValue("--color-action-primary")).toBe("#222222");
    });

    // Switch to null (sign-out / platform console)
    rerender({ tid: null });

    await waitFor(() => {
      expect(document.documentElement.style.getPropertyValue("--color-action-primary")).toBe("");
    });
  });
});
