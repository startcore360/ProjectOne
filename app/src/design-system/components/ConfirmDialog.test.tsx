import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";

describe("ConfirmDialog", () => {
  it("associates string confirmation text with the dialog description", () => {
    render(
      <ConfirmDialog
        isOpen
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        title="Delete this record?"
        message="This action cannot be undone."
      />,
    );

    const dialog = screen.getByRole("dialog", { name: "Delete this record?" });
    const descriptionId = dialog.getAttribute("aria-describedby");

    expect(descriptionId).toBeTruthy();
    expect(document.getElementById(descriptionId ?? "")).toHaveTextContent(
      "This action cannot be undone.",
    );
    expect(screen.getAllByText("This action cannot be undone.")).toHaveLength(1);
  });

  it("keeps rich confirmation content in the dialog body", () => {
    render(
      <ConfirmDialog
        isOpen
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        title="Review action"
        message={<strong>Review the affected records first.</strong>}
      />,
    );

    const dialog = screen.getByRole("dialog", { name: "Review action" });
    expect(dialog).not.toHaveAttribute("aria-describedby");
    expect(screen.getByText("Review the affected records first.")).toBeInTheDocument();
  });

  it("calls the confirmation handler when confirmed", () => {
    const onConfirm = vi.fn();
    render(
      <ConfirmDialog
        isOpen
        onClose={vi.fn()}
        onConfirm={onConfirm}
        title="Continue?"
        message="Proceed with this action?"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });
});
