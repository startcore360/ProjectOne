import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { selfServiceApi } from "../../self-service/api/selfServiceApi";
import { StudentDashboard } from "./StudentDashboard";

vi.mock("../../self-service/api/selfServiceApi", () => ({
  selfServiceApi: { getNotices: vi.fn() },
}));

describe("StudentDashboard", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows published campus notices returned for the signed-in tenant", async () => {
    vi.mocked(selfServiceApi.getNotices).mockResolvedValue([
      {
        noticeId: "notice-1",
        noticeLevel: "UNIVERSITY",
        noticeKind: "GENERAL",
        title: "Campus Holiday",
        body: "Campus is closed tomorrow.",
        authoredActiveRole: "REGISTRAR",
        publishedAt: "2026-11-12T00:00:00Z",
      },
    ]);

    render(<StudentDashboard name="Asha" tenantId="tenant-1" />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading published campus notices");
    await waitFor(() => expect(screen.getByText("Campus Holiday")).toBeInTheDocument());
    expect(screen.getByText("Campus is closed tomorrow.")).toBeInTheDocument();
    expect(selfServiceApi.getNotices).toHaveBeenCalledWith("tenant-1");
  });

  it("does not show an empty-state claim when campus notices fail to load", async () => {
    vi.mocked(selfServiceApi.getNotices).mockRejectedValue(new Error("network"));

    render(<StudentDashboard name="Asha" tenantId="tenant-1" />);

    await waitFor(() => {
      expect(screen.getByText(/Campus notices could not be loaded/i)).toBeInTheDocument();
    });
    expect(screen.queryByText("No published campus notices")).not.toBeInTheDocument();
  });
});
