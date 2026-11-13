import { describe, expect, it } from "vitest";
import { readErrorMessage } from "./types";

describe("readErrorMessage", () => {
  it("replaces internal permission action codes with actionable language", () => {
    expect(readErrorMessage({
      response: { data: { error: "The active role for this scope does not hold action PROGRAM_RULES_SET." } },
    }, "Fallback")).toBe(
      "Your current role does not have permission to perform this action. Ask your administrator to review your access.",
    );
  });

  it("removes correlation IDs from server messages", () => {
    expect(readErrorMessage({
      response: { data: { error: "An unexpected error occurred. Please contact support quoting correlation ID: dd1681e4-6a44-4e63-aa4f-b708db0f2e3a" } },
    }, "Fallback")).toBe("An unexpected error occurred. Please contact support quoting");
  });

  it("uses a friendly fallback for raw internal exception messages", () => {
    expect(readErrorMessage({
      response: { data: { error: "org.springframework.security.AccessDeniedException" } },
    }, "Something went wrong.")).toBe("Something went wrong.");
  });
});
