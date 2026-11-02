import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("merges conflicting Tailwind utility classes with last-one-wins semantics", () => {
    expect(cn("px-2", "px-4")).toBe("px-4");
  });

  it("preserves independent classes and conditional class values", () => {
    expect(cn("text-sm", false && "hidden", "font-medium")).toBe("text-sm font-medium");
  });
});
