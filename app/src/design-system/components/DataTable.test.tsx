import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DataTable } from "./DataTable";

describe("DataTable", () => {
  it("renders accessible mobile cards labelled from table headers", () => {
    render(
      <DataTable headers={["Name", "Status", "Actions"]}>
        <tr>
          <td>Example student</td>
          <td>Active</td>
          <td><button type="button">View</button></td>
        </tr>
      </DataTable>,
    );

    // Cards are aria-hidden; assert the table row content instead
    expect(screen.getByRole("cell", { name: "Example student" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Active" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View" })).toBeInTheDocument();
  });

  it("renders empty state and shows no card container outside mobile", () => {
    const { rerender } = render(
      <DataTable headers={["Name", "Details"]} empty emptyMessage="No records yet." />,
    );
    expect(screen.getByText("No records yet.")).toBeInTheDocument();
    expect(document.querySelector(".platform-table-cards")).not.toBeInTheDocument();

    rerender(
      <DataTable headers={["Name", "Details"]}>
        <tr><td colSpan={2}>Summary row</td></tr>
      </DataTable>,
    );
    // matchMedia is mocked to desktop in setup.tsx so cards never render
    expect(document.querySelector(".platform-table-cards")).not.toBeInTheDocument();
  });
});
