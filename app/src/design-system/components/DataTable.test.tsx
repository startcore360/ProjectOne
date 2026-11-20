import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DataTable } from "./DataTable";

describe("DataTable", () => {
  it("renders desktop table with cells and action buttons", () => {
    render(
      <DataTable headers={["Name", "Status", "Actions"]}>
        <tr>
          <td>Example student</td>
          <td>Active</td>
          <td><button type="button">View</button></td>
        </tr>
      </DataTable>,
    );

    expect(screen.getByRole("cell", { name: "Example student" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Active" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View" })).toBeInTheDocument();
  });

  it("renders accessible mobile cards without aria-hidden when mobile breakpoint matches (EDU-UX-001, EDU-UX-016)", () => {
    window.__setMatchMediaMatches?.(true);
    render(
      <DataTable headers={["Name", "Status", "Actions"]} ariaLabel="Students list">
        <tr>
          <td>Example student</td>
          <td>Active</td>
          <td><button type="button">View student</button></td>
        </tr>
      </DataTable>,
    );

    const cardsContainer = screen.getByRole("list", { name: "Students list" });
    expect(cardsContainer).toBeInTheDocument();
    expect(cardsContainer).not.toHaveAttribute("aria-hidden");

    const card = screen.getByRole("listitem");
    expect(card).toBeInTheDocument();
    expect(screen.getAllByText("Example student").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Active").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "View student" })).toBeInTheDocument();
  });

  it("does not slice rows when pagination={false} even if rows exceed pageSize (EDU-UX-045)", () => {
    const rows = Array.from({ length: 15 }, (_, i) => (
      <tr key={i}>
        <td>Row {i + 1}</td>
        <td>Active</td>
      </tr>
    ));

    render(
      <DataTable headers={["Row", "Status"]} pagination={false} pageSize={5}>
        {rows}
      </DataTable>,
    );

    // All 15 rows must be rendered in DOM
    expect(screen.getAllByRole("row").length).toBe(16); // 1 header + 15 body rows
    expect(screen.getByText("Row 1")).toBeInTheDocument();
    expect(screen.getByText("Row 15")).toBeInTheDocument();
  });

  it("renders empty state and shows no card container outside mobile", () => {
    const { rerender } = render(
      <DataTable headers={["Name", "Details"]} empty emptyMessage="No records yet." />,
    );
    expect(screen.getByText("No records yet.")).toBeInTheDocument();
    expect(document.querySelector(".app-table-cards")).not.toBeInTheDocument();

    rerender(
      <DataTable headers={["Name", "Details"]}>
        <tr><td colSpan={2}>Summary row</td></tr>
      </DataTable>,
    );
    // matchMedia is mocked to desktop in setup.tsx so cards never render
    expect(document.querySelector(".app-table-cards")).not.toBeInTheDocument();
  });

  it("renders accessible desktop table skeleton rows while loading", () => {
    render(<DataTable headers={["Name", "Status"]} loading loadingLabel="Loading students" loadingRows={3} />);

    expect(screen.getByRole("status", { name: "Loading students" })).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText(/Loading students/i)).toBeInTheDocument();
    expect(document.querySelectorAll(".eds-skeleton-table-wrap tbody tr")).toHaveLength(3);
    expect(screen.queryByText("No matching records.")).not.toBeInTheDocument();
  });

  it("renders mobile card-shaped skeletons when the responsive card layout is enabled", () => {
    window.__setMatchMediaMatches?.(true);
    const { container } = render(<DataTable headers={["Name", "Actions"]} loading loadingRows={2} />);

    expect(container.querySelectorAll(".app-table-cards .app-table-card")).toHaveLength(2);
    expect(container.querySelectorAll(".app-table-wrap .app-table-scroll")).toHaveLength(1);
  });
});
