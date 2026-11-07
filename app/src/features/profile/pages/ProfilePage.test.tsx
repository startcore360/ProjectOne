import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import { profileService } from "../api/profileService";
import ProfilePage from "./ProfilePage";

vi.mock("../api/profileService", () => ({
  profileService: {
    get: vi.fn(),
    update: vi.fn(),
  },
}));

function renderWithStore(platformRole: string | null = null) {
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
    <Provider store={store}>
      <ProfilePage />
    </Provider>,
  );
}

describe("ProfilePage", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows a loading state while the profile is being fetched", () => {
    vi.mocked(profileService.get).mockReturnValue(new Promise(() => {}));
    renderWithStore();
    expect(screen.getByText(/Loading your profile/i)).toBeInTheDocument();
  });

  it("shows an error state and can retry when the fetch fails", async () => {
    vi.mocked(profileService.get).mockRejectedValueOnce(new Error("network down"));
    renderWithStore();
    await waitFor(() =>
      expect(screen.getByText(/Could not load your profile/i)).toBeInTheDocument(),
    );
    vi.mocked(profileService.get).mockResolvedValueOnce({
      data: {
        tenantId: "t1",
        userAccountId: "ua1",
        userType: "STAFF",
        platformUserId: null,
        platformRole: null,
        displayName: "Jordan Lee",
        email: "jordan@example.edu",
        mobile: "+15551234567",
      },
      // minimal AxiosResponse shape for the test
    } as never);
    fireEvent.click(screen.getByRole("button", { name: /retry/i }));
    await waitFor(() => expect(screen.getByText("jordan@example.edu")).toBeInTheDocument());
  });

  it("shows the mobile field for a tenant account and lets the user edit and save", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({
      data: {
        tenantId: "t1",
        userAccountId: "ua1",
        userType: "STAFF",
        platformUserId: null,
        platformRole: null,
        displayName: "Jordan Lee",
        email: "jordan@example.edu",
        mobile: "+15551234567",
      },
    } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByLabelText(/Mobile/i)).toBeInTheDocument());

    vi.mocked(profileService.update).mockResolvedValueOnce({
      data: {
        tenantId: "t1",
        userAccountId: "ua1",
        userType: "STAFF",
        platformUserId: null,
        platformRole: null,
        displayName: "Jordan A. Lee",
        email: "jordan@example.edu",
        mobile: "+15551234567",
      },
    } as never);
    fireEvent.change(screen.getByLabelText(/Display name/i), {
      target: { value: "Jordan A. Lee" },
    });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() =>
      expect(screen.getByText(/profile has been updated/i)).toBeInTheDocument(),
    );
    expect(profileService.update).toHaveBeenCalledWith({
      displayName: "Jordan A. Lee",
      mobile: "+15551234567",
    });
  });

  it("hides the mobile field for a platform account", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({
      data: {
        tenantId: null,
        userAccountId: null,
        userType: null,
        platformUserId: "p1",
        platformRole: "PLATFORM_ADMIN",
        displayName: "Avery Admin",
        email: "avery@edu360.local",
        mobile: null,
      },
    } as never);
    renderWithStore("PLATFORM_ADMIN");
    await waitFor(() => expect(screen.getByText("avery@edu360.local")).toBeInTheDocument());
    expect(screen.queryByLabelText(/Mobile/i)).not.toBeInTheDocument();
  });

  it("shows a validation error when the display name is cleared", async () => {
    vi.mocked(profileService.get).mockResolvedValueOnce({
      data: {
        tenantId: "t1",
        userAccountId: "ua1",
        userType: "STAFF",
        platformUserId: null,
        platformRole: null,
        displayName: "Jordan Lee",
        email: "jordan@example.edu",
        mobile: null,
      },
    } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByLabelText(/Display name/i)).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/Display name/i), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByText(/Display name must not be blank/i)).toBeInTheDocument();
    expect(profileService.update).not.toHaveBeenCalled();
  });
});
