import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("merges conflicting Tailwind utility classes with last-one-wins semantics", () => {
    expect(cn("px-2", "px-4")).toBe("px-4");
  });

  it("preserves independent classes and omits a disabled conditional class", () => {
    const isHidden = false;
    expect(cn("text-sm", isHidden ? "hidden" : undefined, "font-medium")).toBe("text-sm font-medium");
  });
});
