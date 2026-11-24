import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Toast from "./Toast";
import ToastHost from "./ToastHost";
import Notice from "./Notice";
import { toast, toastRegistry } from "./toastRegistry";

describe("Toast & ToastHost popup functionality", () => {
  afterEach(() => {
    toastRegistry.clear();
    vi.clearAllMocks();
  });

  it("renders a Toast component with popup styling by default when alert is true", () => {
    render(<Toast variant="success" title="Success!" description="Operation completed" alert={true} />);

    const toastEl = screen.getByTestId("toast");
    expect(toastEl).toBeInTheDocument();
    expect(toastEl).toHaveClass("eds-toast--success");
    expect(toastEl).toHaveClass("eds-toast--popup");
    expect(screen.getByText("Success!")).toBeInTheDocument();
    expect(screen.getByText("Operation completed")).toBeInTheDocument();
  });

  it("ToastHost renders toasts at top-center by default", () => {
    render(<ToastHost />);

    act(() => {
      toast.success("Profile updated", "Saved successfully.");
    });

    const host = screen.getByTestId("toast-host");
    expect(host).toBeInTheDocument();
    expect(host).toHaveAttribute("data-position", "top");
    expect(screen.getByText("Profile updated")).toBeInTheDocument();
    expect(screen.getByText("Saved successfully.")).toBeInTheDocument();
  });

  it("ToastHost supports custom position prop", () => {
    render(<ToastHost position="bottom-center" />);

    act(() => {
      toast.error("Failed", "Network issue");
    });

    const host = screen.getByTestId("toast-host");
    expect(host).toBeInTheDocument();
    expect(host).toHaveAttribute("data-position", "bottom-center");
  });

  it("allows dismissing toasts via dismiss button", async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();

    render(
      <Toast
        variant="info"
        title="Heads up"
        description="Check your inbox"
        alert={true}
        onDismiss={onDismiss}
      />
    );

    const dismissBtn = screen.getByRole("button", { name: /dismiss/i });
    await user.click(dismissBtn);

    // Wait for the exit animation timeout
    await new Promise((r) => setTimeout(r, 250));
    expect(onDismiss).toHaveBeenCalled();
  });

  it("Notice renders as a top popup when popup or asToast prop is true", async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();

    const { rerender } = render(
      <Notice tone="success" popup={true} onDismiss={onDismiss}>
        Saved successfully!
      </Notice>
    );

    const popupToast = screen.getByTestId("toast");
    expect(popupToast).toBeInTheDocument();
    expect(popupToast).toHaveClass("eds-toast--popup");
    expect(screen.getByText("Saved successfully!")).toBeInTheDocument();

    const dismissBtn = screen.getByRole("button", { name: /dismiss/i });
    await user.click(dismissBtn);
    await new Promise((r) => setTimeout(r, 250));
    expect(onDismiss).toHaveBeenCalled();

    // Rerender as normal inline notice
    rerender(<Notice tone="info">Inline notice</Notice>);
    const inlineNotice = screen.getByRole("status");
    expect(inlineNotice).toHaveClass("eds-notice");
    expect(screen.getByText("Inline notice")).toBeInTheDocument();
  });
});
