import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { UnlockTermModal } from "./UnlockTermModal";

describe("UnlockTermModal", () => {
  it("requires a 20-character rationale and fixes the temporary unlock to 72 hours", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<UnlockTermModal isOpen onClose={vi.fn()} onSubmit={onSubmit} unlocking={false} />);

    const reason = screen.getByLabelText(/Statutory Justification/i) as HTMLTextAreaElement;
    const confirm = screen.getByRole("button", { name: /Confirm Unlock/i }) as HTMLButtonElement;
    const duration = screen.getByLabelText(/Unlock Duration/i) as HTMLInputElement;

    expect(confirm.disabled).toBe(true);
    expect(duration.value).toBe("72");
    expect(duration.disabled).toBe(true);

    fireEvent.change(reason, { target: { value: "Too short" } });
    expect(confirm.disabled).toBe(true);

    const rationale = "Approved grievance re-evaluation for cohort 2026";
    fireEvent.change(reason, { target: { value: rationale } });
    expect(confirm.disabled).toBe(false);
    fireEvent.click(confirm);

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(rationale, 72));
  });
});
