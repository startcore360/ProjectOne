import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import authReducer from "../../../store/slices/authSlice";
import {
  SUPPORT_SESSION_EXPIRY_KEY,
  SUPPORT_SESSION_TICKET_KEY,
  SUPPORT_SESSION_TOKEN_KEY,
} from "../../../services/axios";
import TenantSupportPage from "./TenantSupportPage";

afterEach(() => sessionStorage.clear());

describe("TenantSupportPage in a support session", () => {
  it("hides ticket creation, grant management and withdrawal controls", () => {
    const payload = btoa(JSON.stringify({ tenant_id: "tenant-1" }));
    sessionStorage.setItem(SUPPORT_SESSION_TOKEN_KEY, `e30.${payload}.signature`);
    sessionStorage.setItem(SUPPORT_SESSION_EXPIRY_KEY, new Date(Date.now() + 60 * 60 * 1000).toISOString());
    sessionStorage.setItem(SUPPORT_SESSION_TICKET_KEY, "SUP-2026-ABCDE");
    const store = configureStore({ reducer: { auth: authReducer } });

    render(
      <Provider store={store}>
        <TenantSupportPage />
      </Provider>,
    );

    expect(screen.getByText("Read-only access is active")).toBeInTheDocument();
    expect(screen.queryByText(/Raise support ticket/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Grant access/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Withdraw/i)).not.toBeInTheDocument();
  });
});
