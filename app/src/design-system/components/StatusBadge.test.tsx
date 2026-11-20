import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusBadge, humanizeStatus, getStatusTone } from "./StatusBadge";

describe("StatusBadge (EDU-UX-022, EDU-UX-046)", () => {
  it("capitalizes every word in multi-word enum values", () => {
    expect(humanizeStatus("PENDING_ACTIVATION")).toBe("Pending Activation");
    expect(humanizeStatus("ROLLED_BACK")).toBe("Rolled Back");
    expect(humanizeStatus("SOME_NEW_STATUS")).toBe("Some New Status");
  });

  it("maps tones case-insensitively and handles billing and lifecycle states", () => {
    // Billing
    expect(getStatusTone("PAID")).toBe("success");
    expect(getStatusTone("paid")).toBe("success");
    expect(getStatusTone("OVERDUE")).toBe("warning");
    expect(getStatusTone("overdue")).toBe("warning");

    // Lifecycle
    expect(getStatusTone("TRIAL")).toBe("warning");
    expect(getStatusTone("trial")).toBe("warning");
    expect(getStatusTone("SUSPENDED")).toBe("danger");
    expect(getStatusTone("suspended")).toBe("danger");
    expect(getStatusTone("EXPIRED")).toBe("danger");
    expect(getStatusTone("expired")).toBe("danger");

    // Operations
    expect(getStatusTone("FAILED")).toBe("danger");
    expect(getStatusTone("failed")).toBe("danger");
    expect(getStatusTone("ROLLED_BACK")).toBe("danger");
    expect(getStatusTone("APPROVED")).toBe("success");
    expect(getStatusTone("REJECTED")).toBe("danger");

    // Unknown value falls back to neutral
    expect(getStatusTone("UNKNOWN_XYZ")).toBe("neutral");
  });

  it("renders badge with appropriate tone and humanized text", () => {
    render(<StatusBadge value="ROLLED_BACK" />);
    const badge = screen.getByText("Rolled Back");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass("eds-badge--danger");
  });
});
