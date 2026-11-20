import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { Link, MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import { toastRegistry } from "../../../design-system/components";
import { profileService } from "../api/profileService";
import { sessionsService } from "../../sessions/api/sessionsService";
import ProfilePage from "./ProfilePage";

vi.mock("../api/profileService", () => ({
  profileService: {
    get: vi.fn(),
    update: vi.fn(),
    getPhoto: vi.fn(),
    uploadPhoto: vi.fn(),
  },
}));

vi.mock("../../sessions/api/sessionsService", () => ({
  sessionsService: {
    list: vi.fn(),
  },
}));

function LocationProbe() {
  const { pathname, search } = useLocation();
  return <output data-testid="location-search" data-pathname={pathname}>{search}</output>;
}

function renderWithStore(platformRole: string | null = null, initialEntry = "/profile", includeNavigationLink = false) {
  const store = configureStore({ reducer: { auth: authReducer } });
  store.dispatch(
    setUser({
      id: "u1",
      displayName: "Jordan Lee",
      tenantId: platformRole ? null : "t1",
      userType: platformRole ? null : "STAFF",
      // @ts-expect-error - test-only literal, not every PlatformRole is needed here
      platformRole,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    }),
  );
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Provider store={store}>
        <ProfilePage />
        <LocationProbe />
        {includeNavigationLink && <Link to="/dashboard">Go to dashboard</Link>}
      </Provider>
    </MemoryRouter>,
  );
}

const tenantProfile = {
  tenantId: "t1",
  userAccountId: "ua1",
  userType: "STAFF",
  platformUserId: null,
  platformRole: null,
  displayName: "Jordan Lee",
  email: "jordan@example.edu",
  mobile: "+15551234567",
  photoUrl: null,
};

const platformProfile = {
  tenantId: null,
  userAccountId: null,
  userType: null,
  platformUserId: "p1",
  platformRole: "PLATFORM_ADMIN",
  displayName: "Avery Admin",
  email: "avery@edu360.local",
  mobile: null,
  photoUrl: null,
};

afterEach(() => {
  vi.clearAllMocks();
  toastRegistry.clear();
});

describe("ProfilePage", () => {
  it("shows a loading state while the profile is being fetched", () => {
    vi.mocked(profileService.get).mockReturnValue(new Promise(() => {}));
    renderWithStore();
    expect(screen.getByText(/Loading your profile/i)).toBeInTheDocument();
  });

  it("shows an error state and can retry when the fetch fails", async () => {
    vi.mocked(profileService.get).mockRejectedValueOnce(new Error("network down"));
    renderWithStore();
    expect(await screen.findByText(/Could not load your profile/i)).toBeInTheDocument();

    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    fireEvent.click(screen.getByRole("button", { name: /retry/i }));

    expect(await screen.findAllByText("jordan@example.edu")).not.toHaveLength(0);
  });

  it("shows the identity header and read-only details before entering edit mode", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    renderWithStore();

    expect(await screen.findByRole("heading", { name: "Jordan Lee" })).toBeInTheDocument();
    expect(screen.getAllByText("jordan@example.edu").length).toBeGreaterThan(0);
    expect(screen.getByText("Tenant account")).toBeInTheDocument();
    expect(screen.getByText(/Email and role are managed by your administrator/i)).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /display name/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /edit/i }));
    expect(screen.getByRole("textbox", { name: /display name/i })).toHaveValue("Jordan Lee");
    expect(screen.getByRole("textbox", { name: /mobile/i })).toHaveValue("+15551234567");
  });

  it("saves tenant profile changes and announces success with a toast", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    vi.mocked(profileService.update).mockResolvedValueOnce({
      data: { ...tenantProfile, displayName: "Jordan A. Lee" },
    } as never);
    renderWithStore();

    await screen.findByRole("heading", { name: "Jordan Lee" });
    fireEvent.click(screen.getByRole("button", { name: /edit/i }));
    fireEvent.change(screen.getByRole("textbox", { name: /display name/i }), {
      target: { value: "Jordan A. Lee" },
    });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(profileService.update).toHaveBeenCalledWith({
        displayName: "Jordan A. Lee",
        mobile: "+15551234567",
      });
    });
    expect(await screen.findByRole("heading", { name: "Jordan A. Lee" })).toBeInTheDocument();
    expect(toastRegistry.get()).toEqual(
      expect.arrayContaining([expect.objectContaining({ title: "Profile updated", variant: "success" })]),
    );
  });

  it("shows a validation error when the display name is cleared", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    renderWithStore();

    await screen.findByRole("heading", { name: "Jordan Lee" });
    fireEvent.click(screen.getByRole("button", { name: /edit/i }));
    fireEvent.change(screen.getByRole("textbox", { name: /display name/i }), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByText(/Display name must not be blank/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Display name/i)).toHaveAttribute("aria-invalid", "true");
    expect(profileService.update).not.toHaveBeenCalled();
  });

  it("asks before discarding edits when switching tabs and stores the selected tab in the URL", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    renderWithStore();

    await screen.findByRole("heading", { name: "Jordan Lee" });
    fireEvent.click(screen.getByRole("button", { name: /edit/i }));
    fireEvent.change(screen.getByRole("textbox", { name: /display name/i }), {
      target: { value: "Unsaved name" },
    });
    fireEvent.click(screen.getByRole("tab", { name: "Security" }));

    expect(await screen.findByRole("heading", { name: "Discard unsaved changes?" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
    expect(screen.getByRole("textbox", { name: /display name/i })).toHaveValue("Unsaved name");

    fireEvent.click(screen.getByRole("tab", { name: "Security" }));
    fireEvent.click(screen.getByRole("button", { name: "Discard changes" }));
    expect(await screen.findByRole("heading", { name: "Sign-in and security" })).toBeInTheDocument();
    expect(screen.getByTestId("location-search")).toHaveTextContent("?tab=security");
  });

  it("warns before following an in-app link with unsaved edits", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    renderWithStore(null, "/profile", true);

    await screen.findByRole("heading", { name: "Jordan Lee" });
    fireEvent.click(screen.getByRole("button", { name: /edit/i }));
    fireEvent.change(screen.getByRole("textbox", { name: /display name/i }), {
      target: { value: "Unsaved name" },
    });
    fireEvent.click(screen.getByRole("link", { name: "Go to dashboard" }));

    expect(await screen.findByRole("heading", { name: "Discard unsaved changes?" })).toBeInTheDocument();
    expect(screen.getByTestId("location-search")).toHaveAttribute("data-pathname", "/profile");
    fireEvent.click(screen.getByRole("button", { name: "Discard changes" }));
    await waitFor(() => {
      expect(screen.getByTestId("location-search")).toHaveAttribute("data-pathname", "/dashboard");
    });
  });

  it("shows platform security status and hides tenant-only mobile editing", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: platformProfile } as never);
    renderWithStore("PLATFORM_ADMIN", "/profile?tab=security");

    expect(await screen.findByRole("heading", { name: "Sign-in and security" })).toBeInTheDocument();
    expect(screen.getByText("On")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change password" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate new codes" })).toBeInTheDocument();
    expect(sessionsService.list).not.toHaveBeenCalled();
  });

  it("loads the device count only when the Devices tab is selected", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({ data: tenantProfile } as never);
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [{ sessionId: "s1" }, { sessionId: "s2" }],
    } as never);
    renderWithStore();

    await screen.findByRole("heading", { name: "Jordan Lee" });
    expect(sessionsService.list).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("tab", { name: "Devices" }));

    expect(await screen.findByText("You are signed in on 2 devices.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Manage devices" })).toBeInTheDocument();
    expect(screen.getByTestId("location-search")).toHaveTextContent("?tab=devices");
  });
});
