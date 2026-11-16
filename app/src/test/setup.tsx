// Runs automatically before every test file (see vitest.config.ts). It adds
// extra, easier-to-read checks like `expect(element).toBeVisible()` that
// aren't part of plain Vitest on their own.
import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// jsdom does not implement window.matchMedia. Stub it so components that use
// matchMedia (e.g. DataTable's mobile-card rendering) work without throwing,
// and report non-mobile so responsive duplicates don't break text queries.
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

// vitest.config.ts doesn't set `test.globals: true`, so @testing-library/react's own
// auto-cleanup (which only registers itself when it detects a global `afterEach`) never
// kicks in. Without this, every render() in a test file piles onto the same jsdom document
// instead of unmounting between tests, so later tests in a file see duplicate elements left
// over from earlier ones and fail with "Found multiple elements...". Register it explicitly
// instead of turning on vitest's globals (which would be a bigger, unrelated config change).
afterEach(cleanup);
