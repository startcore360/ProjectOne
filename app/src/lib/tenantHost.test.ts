import { describe, it, expect, beforeEach } from "vitest";
import {
  buildSharedLandingUrl,
  buildTargetLoginUrl,
  clearRememberedInstitution,
  getActiveInstitutionSlug,
  getRememberedInstitution,
  isSharedLandingHost,
  isValidInstitutionSlug,
  rememberInstitutionOnLoginSuccess,
  setRememberedInstitution,
} from "./tenantHost";

describe("tenantHost", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe("isSharedLandingHost", () => {
    it("identifies shared landing hosts", () => {
      expect(isSharedLandingHost("localhost")).toBe(true);
      expect(isSharedLandingHost("127.0.0.1")).toBe(true);
      expect(isSharedLandingHost("edu360.localhost")).toBe(true);
      expect(isSharedLandingHost("www.edu360.localhost")).toBe(true);
      expect(isSharedLandingHost("edu360.com")).toBe(true);
      expect(isSharedLandingHost("www.edu360.com")).toBe(true);
      expect(isSharedLandingHost("edu360.duckdns.org")).toBe(true);
      expect(isSharedLandingHost("calm-fox-1234.trycloudflare.com")).toBe(true);
    });

    it("returns false for tenant and platform subdomains", () => {
      expect(isSharedLandingHost("abc.edu360.localhost")).toBe(false);
      expect(isSharedLandingHost("platform.edu360.localhost")).toBe(false);
      expect(isSharedLandingHost("abc.edu360.local")).toBe(false);
      expect(isSharedLandingHost("abc-edu360.duckdns.org")).toBe(false);
      expect(isSharedLandingHost("greenwood.edu360.com")).toBe(false);
      expect(isSharedLandingHost("platform.edu360.com")).toBe(false);
    });
  });

  describe("isValidInstitutionSlug", () => {
    it("accepts valid DNS label slugs and rejects unsafe input", () => {
      expect(isValidInstitutionSlug("abc")).toBe(true);
      expect(isValidInstitutionSlug("greenwood-univ")).toBe(true);
      expect(isValidInstitutionSlug("platform")).toBe(true);
      expect(isValidInstitutionSlug("")).toBe(false);
      expect(isValidInstitutionSlug("-abc")).toBe(false);
      expect(isValidInstitutionSlug("abc.evil.com")).toBe(false);
      expect(isValidInstitutionSlug("abc/login")).toBe(false);
    });
  });

  describe("buildTargetLoginUrl", () => {
    it("builds local .edu360.localhost login URLs from localhost", () => {
      expect(
        buildTargetLoginUrl("abc", { protocol: "http:", hostname: "localhost", port: "5173" }),
      ).toBe("http://abc.edu360.localhost:5173/login");
      expect(
        buildTargetLoginUrl("platform", { protocol: "http:", hostname: "localhost", port: "5173" }),
      ).toBe("http://platform.edu360.localhost:5173/login");
    });

    it("builds production subdomain login URLs from apex or www host", () => {
      expect(
        buildTargetLoginUrl("greenwood", { protocol: "https:", hostname: "edu360.com", port: "" }),
      ).toBe("https://greenwood.edu360.com/login");
      expect(
        buildTargetLoginUrl("greenwood", { protocol: "https:", hostname: "www.edu360.com", port: "" }),
      ).toBe("https://greenwood.edu360.com/login");
    });

    it("builds duckdns login URLs", () => {
      expect(
        buildTargetLoginUrl("abc", { protocol: "https:", hostname: "edu360.duckdns.org", port: "" }),
      ).toBe("https://abc-edu360.duckdns.org/login");
    });

    it("builds /__tenant/<slug> login URLs on Cloudflare quick tunnels", () => {
      expect(
        buildTargetLoginUrl("abc", {
          protocol: "https:",
          hostname: "calm-fox-1234.trycloudflare.com",
          port: "",
        }),
      ).toBe("https://calm-fox-1234.trycloudflare.com/__tenant/abc");
      expect(
        buildTargetLoginUrl("platform", {
          protocol: "https:",
          hostname: "calm-fox-1234.trycloudflare.com",
          port: "",
        }),
      ).toBe("https://calm-fox-1234.trycloudflare.com/__tenant/platform");
    });
  });

  describe("buildSharedLandingUrl", () => {
    it("builds ?switch=1 URL back to the shared landing host", () => {
      expect(
        buildSharedLandingUrl({ protocol: "http:", hostname: "abc.edu360.localhost", port: "5173" }),
      ).toBe("http://localhost:5173/?switch=1");
      expect(
        buildSharedLandingUrl({ protocol: "https:", hostname: "greenwood.edu360.com", port: "" }),
      ).toBe("https://edu360.com/?switch=1");
      expect(
        buildSharedLandingUrl({ protocol: "https:", hostname: "abc-edu360.duckdns.org", port: "" }),
      ).toBe("https://edu360.duckdns.org/?switch=1");
      expect(
        buildSharedLandingUrl({
          protocol: "https:",
          hostname: "calm-fox-1234.trycloudflare.com",
          port: "",
        }),
      ).toBe("https://calm-fox-1234.trycloudflare.com/?switch=1");
    });
  });

  describe("localStorage preference helpers", () => {
    it("stores, reads, and clears valid slugs", () => {
      expect(getRememberedInstitution()).toBeNull();
      setRememberedInstitution("Greenwood");
      expect(getRememberedInstitution()).toBe("greenwood");
      clearRememberedInstitution();
      expect(getRememberedInstitution()).toBeNull();
    });

    it("ignores corrupted or unsafe values in localStorage", () => {
      localStorage.setItem("edu360_institution", "https://evil.example");
      expect(getRememberedInstitution()).toBeNull();
    });

    it("resolves active institution slug from subdomain or tunnel cookie on login success", () => {
      expect(getActiveInstitutionSlug("abc.edu360.localhost")).toBe("abc");
      expect(getActiveInstitutionSlug("platform.edu360.com")).toBe("platform");
      expect(getActiveInstitutionSlug("calm-fox.trycloudflare.com", "edu360_tunnel_host=xyz")).toBe("xyz");
      expect(getActiveInstitutionSlug("localhost")).toBeNull();

      rememberInstitutionOnLoginSuccess(
        { protocol: "https:", hostname: "calm-fox.trycloudflare.com" },
        "edu360_tunnel_host=abc",
      );
      expect(getRememberedInstitution()).toBe("abc");
    });
  });
});
