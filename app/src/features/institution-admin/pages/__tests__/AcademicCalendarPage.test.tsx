import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import AcademicSessionDetailPage from "../AcademicSessionDetailPage";

type TestState = {
  auth: {
    user: {
      tenantId: string;
    };
  };
};

vi.mock("../../../../store/hooks", () => ({
  useAppSelector: (selector: (state: TestState) => unknown) =>
    selector({ auth: { user: { tenantId: "tenant-test" } } }),
}));

vi.mock("../../api/academicHierarchyApi", () => ({
  getAcademicSessionDetail: vi.fn().mockResolvedValue({
    sessionLabel: "2026-27",
    academicYear: 2026,
    startDate: "2026-06-01",
    endDate: "2027-05-31",
    status: "ACTIVE",
    isCurrent: true,
  }),
}));

describe("AcademicSessionDetailPage", () => {
  it("loads selected session details in its institution route context", async () => {
    render(
      <MemoryRouter
        initialEntries={[
          "/university/setup/structure/inst-test/academic-sessions/s1",
        ]}
      >
        <Routes>
          <Route
            path="/university/setup/structure/:institutionId/academic-sessions/:academicSessionId"
            element={<AcademicSessionDetailPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText("2026-27")).toBeTruthy());
    expect(screen.getByText("ACTIVE")).toBeTruthy();
  });
});
