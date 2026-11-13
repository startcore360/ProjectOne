import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FileDrop } from "./FileDrop";

describe("FileDrop", () => {
  it("explains an oversized file rejection and clears the selected file", () => {
    const onFile = vi.fn();
    render(<FileDrop label="Upload document" onFile={onFile} maxBytes={3} />);

    const input = screen.getByLabelText("Upload document");
    fireEvent.change(input, { target: { files: [new File(["four"], "large.pdf", { type: "application/pdf" })] } });

    expect(screen.getByRole("alert")).toHaveTextContent("no larger than 3 bytes");
    expect(onFile).toHaveBeenCalledWith(null);
    expect(input).toHaveValue("");
  });

  it("passes an accepted file and clears the validation message", () => {
    const onFile = vi.fn();
    render(<FileDrop label="Upload document" onFile={onFile} maxBytes={10} />);

    const input = screen.getByLabelText("Upload document");
    const file = new File(["ok"], "small.pdf", { type: "application/pdf" });
    fireEvent.change(input, { target: { files: [file] } });

    expect(onFile).toHaveBeenCalledWith(file);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
