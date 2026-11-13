import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "./Modal";

describe("Modal", () => {
  it("associates its description with the dialog and applies the requested size", () => {
    render(
      <Modal isOpen onClose={vi.fn()} title="Create record" description="Enter the record details." size="xl">
        <button type="button">Save</button>
      </Modal>,
    );

    const dialog = screen.getByRole("dialog", { name: "Create record" });
    expect(dialog).toHaveAttribute("aria-describedby");
    expect(document.getElementById(dialog.getAttribute("aria-describedby") ?? "")).toHaveTextContent(
      "Enter the record details.",
    );
    expect(dialog).toHaveAttribute("data-size", "xl");
  });

  it("closes when the close control is activated", () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Confirm action">
        <p>Continue?</p>
      </Modal>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
