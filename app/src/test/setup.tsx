// Runs automatically before every test file (see vitest.config.ts). It adds
// extra, easier-to-read checks like `expect(element).toBeVisible()` that
// aren't part of plain Vitest on their own.
import "@testing-library/jest-dom/vitest";
