import { render, screen, waitFor } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import notificationsReducer from "../../../store/slices/notificationsSlice";
import { dashboardService } from "../api/dashboardService";
import { RoleWorkspace } from "./RoleWorkspace";

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
  it("shows real active-user and active-section counts for School Admin once loaded", async () => {
    vi.mocked(dashboardService.getSummary).mockResolvedValueOnce({
      data: { activeUsers: 12, activeSections: 7 },
    } as never);

    renderWorkspace("SCHOOL_ADMIN");

    await waitFor(() => {
      expect(screen.getByText("12")).toBeInTheDocument();
      expect(screen.getByText("7")).toBeInTheDocument();
    });
    expect(screen.getByText("Active users in your tenant")).toBeInTheDocument();
    expect(screen.getByText("Active sections configured")).toBeInTheDocument();
    expect(screen.getByText("Pending approvals")).toBeInTheDocument();
  });

  it("leaves every other role's dashboard as the placeholder (not wired to live data)", async () => {
    renderWorkspace("PROFESSOR");

    await waitFor(() => {
      expect(screen.getAllByText("—").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("Live data not yet connected")).toBeInTheDocument();
    expect(dashboardService.getSummary).not.toHaveBeenCalled();
  });
});
