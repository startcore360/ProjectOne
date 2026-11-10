import { render } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer from "../../../store/slices/authSlice";
import { idleLogout, logout } from "../../../services/authManager";
import { SESSION_IDLE_LIMIT_MS } from "../../../services/inactivityTracker";
import ChangePasswordPage from "./ChangePasswordPage";

vi.mock("../../../services/authManager", () => ({
  changePassword: vi.fn(),
  logout: vi.fn(),
  idleLogout: vi.fn(),
}));

describe("ChangePasswordPage", () => {
  afterEach(() => vi.useRealTimers());

  it("REG-I10-1: idle on the change-password screen signs the user out with a reason", () => {
    vi.useFakeTimers();
    const store = configureStore({ reducer: { auth: authReducer } });
    render(
      <Provider store={store}><MemoryRouter><ChangePasswordPage /></MemoryRouter></Provider>,
    );
    vi.advanceTimersByTime(SESSION_IDLE_LIMIT_MS + 1000);
    expect(vi.mocked(idleLogout)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(logout)).not.toHaveBeenCalled();
  });
});
