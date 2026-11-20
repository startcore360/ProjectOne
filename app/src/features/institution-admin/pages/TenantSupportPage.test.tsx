import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer from "../../../store/slices/authSlice";
import TenantSupportPage from "./TenantSupportPage";

vi.mock("../../support/useSupportSession", () => ({
  useSupportSession: () => ({ isSupportSession: true, tenantId: "tenant-1", session: null, endSupportSession: vi.fn() }),
}));

afterEach(() => sessionStorage.clear());

describe("TenantSupportPage in a support session", () => {
  it("hides ticket creation, grant management and withdrawal controls", () => {
    const store = configureStore({ reducer: { auth: authReducer } });

    render(
      <Provider store={store}>
        <TenantSupportPage />
      </Provider>,
    );

    expect(screen.getByText("Read-only access is active")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit ticket/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /grant read-only access/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /withdraw now/i })).not.toBeInTheDocument();
  });
});
