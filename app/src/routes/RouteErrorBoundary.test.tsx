import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Suspense, lazy } from "react";
import type { ReactNode } from "react";
import { RouteErrorBoundary } from "./RouteErrorBoundary";
import { Spinner } from "../design-system/components";

function BrokenChunk(): ReactNode {
  throw new Error("Failed to fetch dynamically imported module: chunk-123.js");
}

describe("RouteErrorBoundary", () => {
  it("renders children when no chunk error occurs", () => {
    render(
      <RouteErrorBoundary>
        <div>Feature Content</div>
      </RouteErrorBoundary>
    );
    expect(screen.getByText("Feature Content")).toBeInTheDocument();
  });

  it("catches chunk error and renders ErrorState with Reload button", async () => {
    const user = userEvent.setup();
    const reloadMock = vi.fn();
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    render(
      <RouteErrorBoundary onReload={reloadMock}>
        <BrokenChunk />
      </RouteErrorBoundary>
    );

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("Failed to load page")).toBeInTheDocument();
    expect(
      screen.getByText(/A newer version of the application may be available/)
    ).toBeInTheDocument();

    const reloadButton = screen.getByRole("button", { name: "Reload" });
    expect(reloadButton).toBeInTheDocument();

    await user.click(reloadButton);
    expect(reloadMock).toHaveBeenCalledTimes(1);

    consoleErrorSpy.mockRestore();
  });

  it("works with lazy components and Suspense fallback", async () => {
    const LazyComponent = lazy(() =>
      Promise.resolve({
        default: () => <div>Lazy Loaded Page Content</div>,
      })
    );

    render(
      <RouteErrorBoundary>
        <Suspense fallback={<Spinner label="Loading page…" />}>
          <LazyComponent />
        </Suspense>
      </RouteErrorBoundary>
    );

    expect(await screen.findByText("Lazy Loaded Page Content")).toBeInTheDocument();
  });
});
