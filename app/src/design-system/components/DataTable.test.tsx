import { render, screen, within } from "@testing-library/react";
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

    const cards = screen.getByRole("list", { name: "Records" });
    expect(cards).toBeInTheDocument();
    expect(within(cards).getByText("Name")).toBeInTheDocument();
    expect(within(cards).getByText("Example student")).toBeInTheDocument();
    expect(within(cards).getByText("Status")).toBeInTheDocument();
    expect(within(cards).getByText("Active")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View" })).toBeInTheDocument();
  });

  it("does not create mobile cards for empty or spanning table rows", () => {
    const { rerender } = render(
      <DataTable headers={["Name", "Details"]} empty emptyMessage="No records yet." />,
    );
    expect(screen.queryByRole("list", { name: "Records" })).not.toBeInTheDocument();
    expect(screen.getByText("No records yet.")).toBeInTheDocument();

    rerender(
      <DataTable headers={["Name", "Details"]}>
        <tr><td colSpan={2}>Summary row</td></tr>
      </DataTable>,
    );
    expect(screen.queryByRole("list", { name: "Records" })).not.toBeInTheDocument();
  });
});
