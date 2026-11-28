import { describe, expect, it } from "vitest";
import { ApiHelper, getApiErrorMessage, getPermissionDenialDetails } from "./apiHelper";

function axios403(data: Record<string, unknown>) {
  return { isAxiosError: true, response: { status: 403, data } };
}

describe("ApiHelper 403 messaging", () => {
  it("falls back to a generic sentence when the backend sent no action detail", () => {
    expect(getApiErrorMessage(axios403({ code: "FORBIDDEN", message: "Access denied" }))).toBe(
      "You do not have permission to perform this action. Ask your administrator to review your access.",
    );
  });

  it("names the action and the top roles that hold it, when the backend sent them", () => {
    const message = getApiErrorMessage(
      axios403({
        code: "ACTION_NOT_GRANTED",
        actionCode: "FEE_STRUCTURE_CREATE",
        actionDescription: "create a fee structure",
        heldBy: ["Finance Director", "Registrar"],
      }),
    );
    expect(message).toBe(
      "You don't have permission to create a fee structure. Ask a Finance Director or Registrar to do this, or switch to a role that can.",
    );
  });

  it("never implies a list of roles when the backend withheld heldBy for a sensitive action", () => {
    const message = getApiErrorMessage(
      axios403({
        code: "ACTION_NOT_GRANTED",
        actionCode: "SALARY_INDIVIDUAL_VIEW",
        actionDescription: "view an individual staff salary",
        heldBy: [],
      }),
    );
    expect(message).toBe(
      "You don't have permission to view an individual staff salary. Ask your administrator to review your access.",
    );
    expect(message).not.toMatch(/ask a .+ or /i);
  });

  it("getPermissionDenialDetails returns null for anything other than a 403", () => {
    expect(getPermissionDenialDetails({ isAxiosError: true, response: { status: 404, data: {} } })).toBeNull();
    expect(getPermissionDenialDetails(new Error("boom"))).toBeNull();
  });

  it("getPermissionDenialDetails surfaces the structured fields for a Permission Denied screen state", () => {
    const details = getPermissionDenialDetails(
      axios403({
        code: "ACTION_NOT_GRANTED",
        actionCode: "FEE_STRUCTURE_CREATE",
        actionDescription: "create a fee structure",
        heldBy: ["Finance Director", "Registrar"],
      }),
    );
    expect(details).toEqual({
      reasonCode: "ACTION_NOT_GRANTED",
      actionCode: "FEE_STRUCTURE_CREATE",
      actionDescription: "create a fee structure",
      heldBy: ["Finance Director", "Registrar"],
      message: "You don't have permission to create a fee structure. Ask a Finance Director or Registrar to do this, or switch to a role that can.",
    });
  });

  it("ApiHelper.normalizeError exposes the same behaviour as the getApiErrorMessage alias", () => {
    expect(ApiHelper.normalizeError(axios403({ code: "FORBIDDEN" }))).toBe(
      "You do not have permission to perform this action. Ask your administrator to review your access.",
    );
  });
});
