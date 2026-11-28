import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import TenantUserRoute from "./TenantUserRoute";

const auth = vi.hoisted((): { tenantId: string | null; userType: string } => ({ tenantId: "tenant-1", userType: "STUDENT" }));
vi.mock("../store/hooks", () => ({
  useAppSelector: (selector: (state: { auth: { user: { tenantId: string | null; userType: string } } }) => unknown) =>
    selector({ auth: { user: { tenantId: auth.tenantId, userType: auth.userType } } }),
}));

describe("TenantUserRoute", () => {
  beforeEach(() => {
    auth.tenantId = "tenant-1";
  });

  it.each(["STUDENT", "GUARDIAN", "STAFF"])("%s can open self-service as a signed-in tenant user", (userType) => {
    auth.userType = userType;
    render(
      <MemoryRouter initialEntries={["/self-service"]}>
        <Routes>
          <Route path="/self-service" element={<TenantUserRoute><p>Self-service content</p></TenantUserRoute>} />
          <Route path="/dashboard" element={<p>Dashboard</p>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("Self-service content")).toBeInTheDocument();
  });

  it("does not expose tenant self-service to a platform identity", () => {
    auth.tenantId = null;
    auth.userType = "SYSTEM";
    render(
      <MemoryRouter initialEntries={["/self-service"]}>
        <Routes>
          <Route path="/self-service" element={<TenantUserRoute><p>Self-service content</p></TenantUserRoute>} />
          <Route path="/dashboard" element={<p>Dashboard</p>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.queryByText("Self-service content")).not.toBeInTheDocument();
  });
});
