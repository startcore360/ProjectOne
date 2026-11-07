import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import authReducer, { setUser } from "../store/slices/authSlice";
import platformPermissionsReducer, { setPlatformPermissions } from "../store/slices/platformPermissionsSlice";
import PlatformConsoleRoute, { PlatformPageGate } from "./PlatformConsoleRoute";

function renderAt(platformRole: string | null, actions?: string[]) {
  const store = configureStore({
    reducer: { auth: authReducer, platformPermissions: platformPermissionsReducer },
  });
  store.dispatch(
    setUser({
      id: "plt-1",
      displayName: "Test User",
      tenantId: null,
      userType: null,
      // @ts-expect-error - test-only literal, not every PlatformRole is needed here
      platformRole,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    }),
  );
  if (actions) {
    store.dispatch(
      setPlatformPermissions({ userId: "plt-1", actions, customRoleName: "Custom", stepInRequired: [], steppedIn: [] }),
    );
  }
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={["/platform/users"]}>
        <Routes>
          <Route element={<PlatformConsoleRoute />}>
            <Route element={<PlatformPageGate anyOf={["PLATFORM_USER_MANAGE"]} />}>
              <Route path="/platform/users" element={<p>staff page</p>} />
            </Route>
          </Route>
          <Route path="/dashboard" element={<p>dashboard</p>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

describe("platform console access", () => {
  it("opens a page for a role that holds its action", () => {
    renderAt("PRODUCT_OWNER");
    expect(screen.getByText("staff page")).toBeInTheDocument();
  });

  it.each(["PLATFORM_ADMIN", "BILLING_MANAGER", "SUPPORT_MANAGER", "RELEASE_MANAGER", "DATA_PROTECTION_OFFICER"])(
    "shows %s the console but a clear denied state on a page they can't use",
    (role) => {
      renderAt(role);
      expect(screen.getByText("You don't have access to this")).toBeInTheDocument();
      expect(screen.queryByText("staff page")).not.toBeInTheDocument();
      expect(screen.queryByText("dashboard")).not.toBeInTheDocument();
    },
  );

  it("sends someone with no platform role to the dashboard", () => {
    renderAt(null);
    expect(screen.getByText("dashboard")).toBeInTheDocument();
  });

  it("uses the server's answer over the standard rights", () => {
    renderAt("RELEASE_MANAGER", ["PLATFORM_USER_MANAGE"]);
    expect(screen.getByText("staff page")).toBeInTheDocument();
  });

  it("denies a Product Owner when the server says the action is gone", () => {
    renderAt("PRODUCT_OWNER", ["PLAN_MANAGE"]);
    expect(screen.getByText("You don't have access to this")).toBeInTheDocument();
  });
});
