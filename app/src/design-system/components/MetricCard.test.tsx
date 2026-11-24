import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MetricCard, getDeterministicSeries } from "./MetricCard";

describe("MetricCard", () => {
  it("does not synthesize a positive trend percentage when value is 0", () => {
    render(
      <MetricCard
        label="Past Due"
        value={0}
        note="Sent invoices beyond due date"
        mark="alert"
      />,
    );

    expect(screen.getByText("Past Due")).toBeInTheDocument();
    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getByText("Sent invoices beyond due date")).toBeInTheDocument();
    // Must NOT synthesize a fake 68% increase on 0 count
    expect(screen.queryByText(/68%/)).not.toBeInTheDocument();
    expect(screen.queryByText(/▲/)).not.toBeInTheDocument();
  });

  it("returns zeros array for value 0 in getDeterministicSeries", () => {
    const series = getDeterministicSeries("Past Due", 0);
    expect(series.every((val) => val === 0)).toBe(true);
  });

  it("renders explicit trend when passed", () => {
    render(
      <MetricCard
        label="Revenue"
        value="₹1,00,000"
        trend={{ text: "12%", tone: "up" }}
      />,
    );

    expect(screen.getByText(/12%/)).toBeInTheDocument();
    expect(screen.getByText(/▲/)).toBeInTheDocument();
  });
});
