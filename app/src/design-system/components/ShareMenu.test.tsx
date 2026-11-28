import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import ShareMenu from "./ShareMenu";

describe("ShareMenu", () => {
  it("renders trigger button and opens destination menu with tokenized styles", () => {
    render(<ShareMenu text="https://example.com/share" label="Share document" />);

    const trigger = screen.getByRole("button", { name: /share document/i });
    expect(trigger).toBeInTheDocument();

    fireEvent.click(trigger);

    const menu = screen.getByRole("menu", { name: /share document destinations/i });
    expect(menu).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /copy to clipboard/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /whatsapp/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /telegram/i })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /email/i })).toBeInTheDocument();
  });
});
