import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { RouteTitleListener, getTitleForPath } from "./RouteTitleListener";

describe("RouteTitleListener & getTitleForPath", () => {
  let originalTitle: string;

  beforeEach(() => {
    originalTitle = document.title;
  });

  afterEach(() => {
    document.title = originalTitle;
  });

  it("returns correct titles for platform routes", () => {
    expect(getTitleForPath("/platform/tenants")).toBe("Tenants — EDU 360");
    expect(getTitleForPath("/platform/reports")).toBe("Reports — EDU 360");
    expect(getTitleForPath("/platform/plans")).toBe("Plans & Packages — EDU 360");
    expect(getTitleForPath("/platform/invoices")).toBe("Invoices & Billing — EDU 360");
  });

  it("returns correct titles for dashboard and common routes", () => {
    expect(getTitleForPath("/dashboard")).toBe("Dashboard — EDU 360");
    expect(getTitleForPath("/profile")).toBe("Profile — EDU 360");
    expect(getTitleForPath("/sessions")).toBe("Sessions — EDU 360");
    expect(getTitleForPath("/login")).toBe("Sign In — EDU 360");
  });

  it("falls back to EDU 360 for unknown routes", () => {
    expect(getTitleForPath("/unknown-route")).toBe("EDU 360");
  });

  it("updates document.title when rendered inside a router", () => {
    render(
      <MemoryRouter initialEntries={["/platform/tenants"]}>
        <RouteTitleListener />
      </MemoryRouter>
    );

    expect(document.title).toBe("Tenants — EDU 360");
  });
});
