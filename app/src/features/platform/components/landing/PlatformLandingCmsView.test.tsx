import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { PlatformLandingCmsPage } from "./PlatformLandingCmsView";
import { landingApi } from "../../../landing/api/landingApi";

vi.mock("../../../landing/api/landingApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../landing/api/landingApi")>();
  return {
    ...actual,
    landingApi: {
      ...actual.landingApi,
      getCmsOverview: vi.fn().mockResolvedValue({
        config: {
          configId: "cfg-1",
          heroEyebrow: "Eyebrow",
          heroTitle: "Title",
          heroSubtitle: "Subtitle",
          heroNote: "Note",
          trustLabel: "Trust",
          whyChooseJson: "[]",
          roleBenefitsJson: "[]",
          solutionsJson: "[]",
          stepsJson: "[]",
          contactEmail: "hello@edu360.example",
          footerTagline: "Tagline",
          heroLightMediaId: null,
          heroDarkMediaId: null,
          showcaseLightMediaId: null,
          showcaseDarkMediaId: null,
          rowVersion: 1,
        },
        testimonials: [],
        directoryTenants: [
          {
            tenantId: "t-1",
            tenantName: "Greenwood University",
            subdomain: "greenwood",
            tenantType: "UNIVERSITY",
            state: "ACTIVE",
            listedInDirectory: false,
          },
        ],
      }),
      listEnquiries: vi.fn().mockResolvedValue([]),
      toggleDirectory: vi.fn().mockResolvedValue({
        tenantId: "t-1",
        tenantName: "Greenwood University",
        subdomain: "greenwood",
        tenantType: "UNIVERSITY",
        state: "ACTIVE",
        listedInDirectory: true,
      }),
      updateConfig: vi.fn().mockImplementation((payload, rowVersion) =>
        Promise.resolve({
          configId: "cfg-1",
          ...payload,
          rowVersion: (rowVersion ?? 1) + 1,
        }),
      ),
    },
  };
});

describe("PlatformLandingCmsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads CMS overview and allows toggling tenant directory visibility", async () => {
    render(<PlatformLandingCmsPage />);

    expect(await screen.findByLabelText(/hero headline/i)).toHaveValue("Title");

    fireEvent.click(screen.getByRole("tab", { name: /sign-in directory/i }));
    expect(await screen.findByText("Greenwood University")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /show in dropdown/i }));
    await waitFor(() => {
      expect(landingApi.toggleDirectory).toHaveBeenCalledWith("t-1", true);
    });
  });

  it("resets landing page copy and media to defaults when confirmed", async () => {
    render(<PlatformLandingCmsPage />);

    expect(await screen.findByLabelText(/hero headline/i)).toHaveValue("Title");

    const resetButtons = screen.getAllByRole("button", { name: /reset to default/i });
    expect(resetButtons.length).toBeGreaterThan(0);
    fireEvent.click(resetButtons[0]);

    expect(await screen.findByText(/reset landing page to default\?/i)).toBeInTheDocument();

    const dialogConfirmBtn = screen.getByRole("button", { name: "Reset to Default" });
    fireEvent.click(dialogConfirmBtn);

    await waitFor(() => {
      expect(landingApi.updateConfig).toHaveBeenCalledWith(
        expect.objectContaining({
          heroTitle: "Bring your entire institution into sync.",
          heroEyebrow: "A connected education management platform",
          heroNote: "Designed for Schools · Colleges · Universities",
          heroLightMediaId: null,
          heroDarkMediaId: null,
        }),
        1,
      );
    });

    expect(screen.getByLabelText(/hero headline/i)).toHaveValue(
      "Bring your entire institution into sync.",
    );
  });
});
