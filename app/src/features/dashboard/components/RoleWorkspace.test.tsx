import { render, screen, waitFor } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import notificationsReducer from "../../../store/slices/notificationsSlice";
import { dashboardService } from "../api/dashboardService";
import { institutionService } from "../../institution-admin/api/institutionService";
import { RoleWorkspace } from "./RoleWorkspace";

vi.mock("../../institution-admin/api/institutionService", () => ({
  institutionService: { listInstitutions: vi.fn() },
}));

vi.mock("../api/dashboardService", () => ({
  dashboardService: {
    getSummary: vi.fn(),
  },
}));

function renderWorkspace(roleCode: string) {
  const store = configureStore({ reducer: { auth: authReducer, notifications: notificationsReducer } });
  store.dispatch(
    setUser({
      id: "u1",
      displayName: "Dr. Erappa",
      tenantId: "tenant-1",
      userType: "STAFF",
      platformRole: null,
      roleCode,
      roleName: roleCode,
      photoUrl: null,
      mustChangePassword: false,
    })
  );
  render(
    <Provider store={store}>
      <MemoryRouter>
        <RoleWorkspace name="Erappa" roleCode={roleCode} />
      </MemoryRouter>
    </Provider>
  );
}

describe("RoleWorkspace", () => {
  beforeEach(() => vi.clearAllMocks());
  it("shows real active-user and active-section counts for School Admin once loaded", async () => {
    vi.mocked(dashboardService.getSummary).mockResolvedValueOnce({
      data: { activeUsers: 12, activeSections: 7 },
    } as never);

    renderWorkspace("SCHOOL_ADMIN");

    await waitFor(() => {
      expect(screen.getByText("12")).toBeInTheDocument();
      expect(screen.getByText("7")).toBeInTheDocument();
    });
    expect(screen.getByText("Active accounts in this institution")).toBeInTheDocument();
    expect(screen.getByText("Configured active sections")).toBeInTheDocument();
    expect(screen.getByText("Approvals assigned")).toBeInTheDocument();
  });



  it("loads the live tenant summary for College Admin", async () => {
    vi.mocked(dashboardService.getSummary).mockResolvedValueOnce({ data: { activeUsers: 27, activeSections: 9 } } as never);
    renderWorkspace("COLLEGE_ADMIN");
    await waitFor(() => { expect(screen.getByText("27")).toBeInTheDocument(); expect(screen.getByText("9")).toBeInTheDocument(); });
    expect(screen.getByText("Academic setup")).toBeInTheDocument();
    expect(screen.getByText("Users & roles")).toBeInTheDocument();
  });

  it("loads the live summary and role panels for a College Principal", async () => {
    vi.mocked(dashboardService.getSummary).mockResolvedValueOnce({ data: { activeUsers: 48, activeSections: 16 } } as never);
    renderWorkspace("PRINCIPAL_COLLEGE");
    await waitFor(() => { expect(screen.getByText("48")).toBeInTheDocument(); expect(screen.getByText("16")).toBeInTheDocument(); });
    expect(screen.getByText("Approval desk")).toBeInTheDocument();
    expect(screen.getByText("Academic pulse")).toBeInTheDocument();
    expect(screen.getByText("College oversight")).toBeInTheDocument();
    expect(screen.getByText("Approvals assigned")).toBeInTheDocument();
  });

  it("loads the institution portfolio for University Admin", async () => {
    vi.mocked(institutionService.listInstitutions).mockResolvedValueOnce({ total: 3, items: [ { institutionId: "i1", status: "ACTIVE" }, { institutionId: "i2", status: "ACTIVE" }, { institutionId: "i3", status: "DRAFT" } ] } as never);
    renderWorkspace("UNIVERSITY_ADMIN");
    await waitFor(() => { expect(screen.getByText("3")).toBeInTheDocument(); expect(screen.getByText("2")).toBeInTheDocument(); });
    expect(screen.getByText("Institution setup")).toBeInTheDocument();
    expect(screen.getByText("Users and roles")).toBeInTheDocument();
  });

  it("leaves roles without a scoped dashboard API on explicit placeholders", async () => {
    renderWorkspace("PROFESSOR");

    await waitFor(() => {
      expect(screen.getAllByText("—").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("Live data not connected for this metric")).toBeInTheDocument();
    expect(dashboardService.getSummary).not.toHaveBeenCalled();
  });
});
