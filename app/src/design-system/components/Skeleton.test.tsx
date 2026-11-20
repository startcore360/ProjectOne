import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  SkeletonText,
  SkeletonAvatar,
  SkeletonMetricCard,
  SkeletonTable,
  SkeletonPage,
} from "./Skeleton";

describe("Skeleton components (EDU-UX-029)", () => {
  it("renders SkeletonText with aria-hidden", () => {
    const { container } = render(<SkeletonText lines={3} />);
    const group = container.querySelector(".eds-skeleton-group");
    expect(group).toBeInTheDocument();
    expect(group).toHaveAttribute("aria-hidden", "true");
    expect(group?.children.length).toBe(3);
  });

  it("renders SkeletonAvatar", () => {
    const { container } = render(<SkeletonAvatar size={48} />);
    const avatar = container.querySelector(".eds-skeleton--avatar");
    expect(avatar).toBeInTheDocument();
    expect(avatar).toHaveStyle({ width: "48px", height: "48px" });
  });

  it("renders SkeletonMetricCard and SkeletonTable", () => {
    const { container } = render(
      <>
        <SkeletonMetricCard />
        <SkeletonTable rows={3} columns={3} />
      </>
    );
    expect(container.querySelector(".eds-skeleton-metric-card")).toBeInTheDocument();
    expect(container.querySelectorAll("tbody tr").length).toBe(3);
  });

  it("renders SkeletonPage with aria-busy and sr-only label", () => {
    render(<SkeletonPage title="Loading tenants" showKpis showTable />);
    const page = screen.getByRole("status");
    expect(page).toHaveAttribute("aria-busy", "true");
    expect(page).toHaveAttribute("aria-label", "Loading tenants");
    expect(screen.getByText(/Loading tenants/i)).toBeInTheDocument();
  });
});
