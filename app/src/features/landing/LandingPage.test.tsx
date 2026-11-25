import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import LandingPage from "./LandingPage";
import { landingApi } from "./api/landingApi";

vi.mock("./api/landingApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api/landingApi")>();
  return {
    ...actual,
    landingApi: {
      ...actual.landingApi,
      listPublicInstitutions: vi.fn().mockResolvedValue([
        { name: "Greenwood University", slug: "greenwood", tenantType: "UNIVERSITY" },
      ]),
      getPublicLanding: vi.fn().mockResolvedValue({
        heroEyebrow: "A connected education management platform",
        heroTitle: "Bring your entire institution into sync.",
        heroSubtitle: "EDU 360 connects academic, financial, and governance workflows.",
        heroNote: "Designed for Schools · Colleges · Universities",
        trustLabel: "One connected platform",
        whyChooseJson: "[]",
        roleBenefitsJson: "[]",
        solutionsJson: "[]",
        stepsJson: "[]",
        contactEmail: "hello@edu360.example",
        footerTagline: "One Platform.",
        heroLightMediaId: null,
        heroDarkMediaId: null,
        showcaseLightMediaId: null,
        showcaseDarkMediaId: null,
        testimonials: [
          {
            testimonialId: "v-test-1",
            kind: "VIDEO",
            authorName: "Dr. Priya Nair",
            authorRole: "Principal",
            institutionName: "Demo College",
            quoteText: "EDU 360 transformed our workflows.",
            videoUrl: "https://example.com/video.mp4",
            videoMediaId: null,
            posterMediaId: null,
            displayOrder: 0,
            published: true,
            rowVersion: 1,
          },
        ],
        catalogModules: [
          { moduleCode: "ACADEMICS", itemName: "Academic management", description: null },
          { moduleCode: "FEES", itemName: "Fees & finance", description: null },
        ],
      }),
      submitPublicEnquiry: vi.fn().mockResolvedValue({
        enquiryId: "enq-1",
        enquiryType: "QUOTATION",
        status: "NEW",
        message: "Your quotation request has been received.",
        submittedAt: new Date().toISOString(),
      }),
    },
  };
});

describe("LandingPage", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("renders About EDU 360 landing page, interactive role tabs, and marquee controls", async () => {
    const onNavigate = vi.fn();
    render(
      <MemoryRouter initialEntries={["/"]}>
        <LandingPage onNavigate={onNavigate} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("heading", {
        name: /bring your entire institution into sync/i,
      }),
    ).toBeInTheDocument();

    // Role tabs switch content
    fireEvent.click(screen.getByRole("tab", { name: /teachers & faculty/i }));
    expect(screen.getByText(/spend less time on paperwork/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: /parents & guardians/i }));
    expect(screen.getByText(/stay connected to your child's progress/i)).toBeInTheDocument();

    // Pause marquee and open a video testimonial modal
    fireEvent.click(await screen.findByRole("button", { name: /pause feedback carousel/i }));
    expect(screen.getByRole("button", { name: /resume feedback carousel/i })).toBeInTheDocument();

    const playBtns = await screen.findAllByRole("button", { name: /play video testimonial/i });
    fireEvent.click(playBtns[0]);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("redirects immediately when an institution is remembered in localStorage", () => {
    localStorage.setItem("edu360_institution", "greenwood");
    const onNavigate = vi.fn();

    render(
      <MemoryRouter initialEntries={["/"]}>
        <LandingPage onNavigate={onNavigate} />
      </MemoryRouter>,
    );

    expect(onNavigate).toHaveBeenCalledTimes(1);
    expect(onNavigate.mock.calls[0][0]).toContain("greenwood.edu360.localhost");
    expect(onNavigate.mock.calls[0][0]).toContain("/login");
  });

  it("clears remembered institution and opens selector when ?switch=1 is present", () => {
    localStorage.setItem("edu360_institution", "greenwood");
    const onNavigate = vi.fn();

    render(
      <MemoryRouter initialEntries={["/?switch=1"]}>
        <LandingPage onNavigate={onNavigate} />
      </MemoryRouter>,
    );

    expect(onNavigate).not.toHaveBeenCalled();
    expect(localStorage.getItem("edu360_institution")).toBeNull();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("supports searchable institution list + manual code without saving before login", async () => {
    const onNavigate = vi.fn();
    render(
      <MemoryRouter initialEntries={["/"]}>
        <LandingPage onNavigate={onNavigate} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getAllByRole("button", { name: /sign in to your institution/i })[0]);
    await waitFor(() => {
      expect(landingApi.listPublicInstitutions).toHaveBeenCalled();
    });

    // Searchable list shows Greenwood University
    const optionBtn = await screen.findByRole("option", { name: /greenwood university/i });
    fireEvent.click(optionBtn);
    expect(localStorage.getItem("edu360_institution")).toBeNull();
    expect(onNavigate).toHaveBeenCalledTimes(1);
    expect(onNavigate.mock.calls[0][0]).toContain("greenwood.edu360.localhost");

    // Manual code fallback works too
    onNavigate.mockClear();
    fireEvent.click(screen.getByRole("button", { name: /enter code manually/i }));
    const input = screen.getByLabelText(/institution code/i);

    fireEvent.change(input, { target: { value: "bad.domain" } });
    fireEvent.click(screen.getByRole("button", { name: /continue to institution login/i }));
    expect(screen.getByText(/use only letters, numbers, and hyphens/i)).toBeInTheDocument();
    expect(onNavigate).not.toHaveBeenCalled();

    fireEvent.change(input, { target: { value: "Apex" } });
    fireEvent.click(screen.getByRole("button", { name: /continue to institution login/i }));
    expect(localStorage.getItem("edu360_institution")).toBeNull();
    expect(onNavigate).toHaveBeenCalledTimes(1);
    expect(onNavigate.mock.calls[0][0]).toContain("apex.edu360.localhost");
  });

  it("submits quotation request through public enquiry API", async () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <LandingPage onNavigate={vi.fn()} />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText(/institution type/i), { target: { value: "University" } });
    fireEvent.change(screen.getByLabelText(/approx\. student strength/i), { target: { value: "1,501–3,000" } });
    fireEvent.change(screen.getByLabelText(/your name/i), { target: { value: "Dr. Rao" } });
    fireEvent.change(screen.getByLabelText(/work email/i), { target: { value: "rao@univ.edu" } });
    fireEvent.change(screen.getByLabelText(/institution name/i), { target: { value: "Apex Univ" } });

    fireEvent.click(screen.getByRole("button", { name: /send my project brief/i }));

    await waitFor(() => {
      expect(landingApi.submitPublicEnquiry).toHaveBeenCalledWith(
        expect.objectContaining({
          enquiryType: "QUOTATION",
          institutionType: "University",
          contactEmail: "rao@univ.edu",
        }),
      );
    });
  });

  it("does not auto-redirect when user navigates back via browser Back button", () => {
    localStorage.setItem("edu360_institution", "greenwood");
    const spy = vi.spyOn(performance, "getEntriesByType").mockReturnValue([
      { type: "back_forward" } as PerformanceNavigationTiming,
    ]);
    const onNavigate = vi.fn();

    render(
      <MemoryRouter initialEntries={["/"]}>
        <LandingPage onNavigate={onNavigate} />
      </MemoryRouter>,
    );

    expect(onNavigate).not.toHaveBeenCalled();
    spy.mockRestore();
  });
});
