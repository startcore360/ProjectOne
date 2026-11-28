import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { MemoryRouter } from "react-router-dom";
import AppRoutes from "./Routes";
import authReducer from "../store/slices/authSlice";
import platformPermissionsReducer from "../store/slices/platformPermissionsSlice";
import tenantPermissionsReducer from "../store/slices/tenantPermissionsSlice";
import notificationsReducer from "../store/slices/notificationsSlice";

function makeStore(authOverrides?: Partial<{ isAuthenticated: boolean; user: Record<string, unknown> | null }>) {
  return configureStore({
    reducer: {
      auth: authReducer,
      platformPermissions: platformPermissionsReducer,
      tenantPermissions: tenantPermissionsReducer,
      notifications: notificationsReducer,
    } as never,
    preloadedState: {
      auth: {
        isAuthenticated: authOverrides?.isAuthenticated ?? false,
        user: (authOverrides?.user as never) ?? null,
        loading: false,
        error: null,
      },
    } as never,
  });
}

describe("RootEntry & existing tenant subdomain regression", () => {
  const originalLocation = window.location;

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
    });
  });

  function mockHostname(hostname: string, protocol = "http:", port = "5173") {
    Object.defineProperty(window, "location", {
      value: {
        ...originalLocation,
        hostname,
        protocol,
        port,
        origin: `${protocol}//${hostname}${port ? `:${port}` : ""}`,
        href: `${protocol}//${hostname}${port ? `:${port}` : ""}/`,
        assign: vi.fn(),
      },
      writable: true,
    });
  }

  it("renders LandingPage at / on shared landing host (localhost) when unauthenticated", async () => {
    mockHostname("localhost");
    const store = makeStore({ isAuthenticated: false });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/"]}>
          <AppRoutes />
        </MemoryRouter>
      </Provider>,
    );

    expect(
      await screen.findByRole("heading", { name: /bring your entire institution into sync/i }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });

  it("redirects unauthenticated / on tenant subdomain (abc.edu360.localhost) to /login as before", async () => {
    mockHostname("abc.edu360.localhost");
    const store = makeStore({ isAuthenticated: false });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/"]}>
          <AppRoutes />
        </MemoryRouter>
      </Provider>,
    );

    expect(await screen.findByRole("heading", { name: /welcome back\./i })).toBeInTheDocument();
    const switchLink = screen.getByRole("link", { name: /not your institution\? switch institution/i });
    expect(switchLink).toHaveAttribute("href", "http://localhost:5173/?switch=1");
  });

  it("redirects authenticated / on tenant subdomain (abc.edu360.localhost) to /dashboard as before", async () => {
    mockHostname("abc.edu360.localhost");
    const store = makeStore({
      isAuthenticated: true,
      user: { id: "u1", email: "user@abc.edu", userType: "STAFF", tenantId: "t1", mustChangePassword: false },
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/"]}>
          <AppRoutes />
        </MemoryRouter>
      </Provider>,
    );

    // AppLayout / DashboardPage loads after redirect to /dashboard
    expect(screen.queryByRole("heading", { name: /bring your entire institution into sync/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /welcome back\./i })).not.toBeInTheDocument();
  });

  it("shows LandingPage at / and keeps switch link on the same origin on Cloudflare quick tunnels", async () => {
    mockHostname("calm-fox-1234.trycloudflare.com", "https:", "");
    const store = makeStore({ isAuthenticated: false });

    const { unmount } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/"]}>
          <AppRoutes />
        </MemoryRouter>
      </Provider>,
    );

    expect(
      await screen.findByRole("heading", { name: /bring your entire institution into sync/i }, { timeout: 5000 }),
    ).toBeInTheDocument();
    unmount();

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/login"]}>
          <AppRoutes />
        </MemoryRouter>
      </Provider>,
    );

    expect(await screen.findByRole("heading", { name: /welcome back\./i })).toBeInTheDocument();
    const switchLink = screen.getByRole("link", { name: /not your institution\? switch institution/i });
    expect(switchLink).toHaveAttribute("href", "https://calm-fox-1234.trycloudflare.com/?switch=1");
  });

  it("redirects authenticated / on Cloudflare quick tunnel to /dashboard instead of LandingPage", () => {
    mockHostname("calm-fox-1234.trycloudflare.com", "https:", "");
    const store = makeStore({
      isAuthenticated: true,
      user: { id: "u1", email: "user@abc.edu", userType: "STAFF", tenantId: "t1", mustChangePassword: false },
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/"]}>
          <AppRoutes />
        </MemoryRouter>
      </Provider>,
    );

    expect(screen.queryByRole("heading", { name: /bring your entire institution into sync/i })).not.toBeInTheDocument();
  });
});
