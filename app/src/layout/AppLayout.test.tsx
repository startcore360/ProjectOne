import { render, screen } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../store/slices/authSlice";
import platformPermissionsReducer, { setPlatformPermissions } from "../store/slices/platformPermissionsSlice";
import type { SupportSessionState } from "../features/support/useSupportSession";
import AppLayout from "./AppLayout";

vi.mock("../services/axios", () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

vi.mock("../services/authManager", () => ({
  logout: vi.fn(),
  idleLogout: vi.fn(),
}));

vi.mock("../services/inactivityTracker", () => ({
  SESSION_IDLE_LIMIT_MS: 1800000,
  startInactivityTracker: vi.fn(() => () => {}),
}));

vi.mock("../features/live/liveSocket", () => ({
  LIVE_EVENT_NAME: "edu360:live-event",
  useLiveSocket: vi.fn(),
}));

vi.mock("../features/branding/useTenantBranding", () => ({
  useTenantBranding: vi.fn(() => ({ logoUrl: null, primaryColor: null })),
}));

vi.mock("../features/profile/hooks/useProfilePhoto", () => ({
  useProfilePhoto: vi.fn(() => null),
}));

const mockCanTenantAny = vi.fn();
vi.mock("../features/profile/hooks/useTenantAccess", () => ({
  useTenantAccess: () => ({
    canAny: mockCanTenantAny,
    loading: false,
    error: null,
    permissions: null,
  }),
}));

const mockSupportSession: { isSupportSession: boolean; session: SupportSessionState | null } = {
  isSupportSession: false,
  session: null,
};
vi.mock("../features/support/useSupportSession", () => ({
  useSupportSession: () => mockSupportSession,
}));

function renderLayout(userConfig: Parameters<typeof setUser>[0], initialRoute = "/dashboard") {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      platformPermissions: platformPermissionsReducer,
    },
  });
  store.dispatch(setUser(userConfig));
  if (userConfig?.platformRole) {
    store.dispatch(
      setPlatformPermissions({
        userId: userConfig.id,
        actions: [
          "PLATFORM_USER_MANAGE",
          "TENANT_MANAGE",
          "SECURITY_POLICY_VIEW",
          "INTEGRATION_MANAGE",
          "RELEASE_VIEW",
          "TENANT_EXPORT_APPROVE",
          "LANDING_MANAGE",
        ],
        customRoleName: null,
        stepInRequired: [],
        steppedIn: [],
      })
    );
  }

  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialRoute]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="*" element={<div>Page Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </Provider>
  );
}

describe("AppLayout Navigation (Item 7)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem("edu360-sidebar-collapsed", "false");
    mockSupportSession.isSupportSession = false;
    mockSupportSession.session = null;
    mockCanTenantAny.mockReturnValue(false);
  });

  it("renders university navigation with Term lock (lock) and personal navigation below divider", () => {
    mockCanTenantAny.mockImplementation((actions: string[]) =>
      actions.includes("TENANT_EXPORT_REQUEST")
    );

    renderLayout({
      id: "u1",
      displayName: "University Admin",
      tenantId: "tenant-1",
      userType: "STAFF",
      platformRole: null,
      roleCode: "UNIVERSITY_ADMIN",
      roleName: "University Admin",
      photoUrl: null,
      mustChangePassword: false,
    });

    // Check Roles & permissions has shield icon
    const rolesLink = screen.getAllByRole("link", { name: /Roles & permissions/i })[0];
    expect(rolesLink).toBeInTheDocument();
    expect(rolesLink.querySelector('svg[data-icon="shield"]')).toBeInTheDocument();

    // Check Term lock has lock icon
    const termLockLink = screen.getAllByRole("link", { name: /Term lock/i })[0];
    expect(termLockLink).toBeInTheDocument();
    expect(termLockLink.querySelector('svg[data-icon="lock"]')).toBeInTheDocument();

    // Check Data protection has key icon (not lock, not shield)
    const dataProtectionLink = screen.getAllByRole("link", { name: /Data protection/i })[0];
    expect(dataProtectionLink).toBeInTheDocument();
    expect(dataProtectionLink.querySelector('svg[data-icon="key"]')).toBeInTheDocument();
    expect(dataProtectionLink.querySelector('svg[data-icon="lock"]')).toBeNull();

    // Check Personal navigation: Self-service & My sessions
    const mySessionsLink = screen.getAllByRole("link", { name: /My sessions/i })[0];
    expect(mySessionsLink).toBeInTheDocument();
    expect(mySessionsLink.querySelector('svg[data-icon="clock"]')).toBeInTheDocument();

    // Divider exists between feature navigation and personal navigation
    const divider = document.querySelector(".app-nav__divider");
    expect(divider).toBeInTheDocument();
  });

  it("renders Role Previews with eye icon during support session", () => {
    mockSupportSession.isSupportSession = true;
    mockSupportSession.session = {
      ticketNumber: "T-100",
      token: "mock-token",
      tenantId: "tenant-1",
      expiresAt: null,
    };

    renderLayout({
      id: "sup-1",
      displayName: "Support Engineer",
      tenantId: "tenant-1",
      userType: "STAFF",
      platformRole: null,
      roleCode: "SUPPORT_OPERATOR",
      roleName: "Support Operator",
      photoUrl: null,
      mustChangePassword: false,
    });

    const rolePreviewsLink = screen.getAllByRole("link", { name: /Role Previews/i })[0];
    expect(rolePreviewsLink).toBeInTheDocument();
    expect(rolePreviewsLink.querySelector('svg[data-icon="eye"]')).toBeInTheDocument();
    expect(rolePreviewsLink.querySelector('svg[data-icon="shield"]')).toBeNull();
  });

  it("renders Data protection with key icon in platform console", () => {
    renderLayout({
      id: "plt-1",
      displayName: "Platform Admin",
      tenantId: null,
      userType: null,
      platformRole: "PLATFORM_ADMIN",
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    });

    const dataProtectionLinks = screen.getAllByRole("link", { name: /Data protection/i });
    expect(dataProtectionLinks.length).toBeGreaterThan(0);
    expect(dataProtectionLinks[0].querySelector('svg[data-icon="key"]')).toBeInTheDocument();
    expect(dataProtectionLinks[0].querySelector('svg[data-icon="shield"]')).toBeNull();
  });
});
