import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { describe, expect, it, vi, beforeEach } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import { selfServiceApi } from "../api/selfServiceApi";
import SelfServicePage from "./SelfServicePage";

vi.mock("../api/selfServiceApi", () => ({
  selfServiceApi: {
    getNotifications: vi.fn(),
    getLeaveBalances: vi.fn(),
    getLeaveRequests: vi.fn(),
    getNotices: vi.fn(),
  },
}));

function renderWithStore(ui: React.ReactElement) {
  const store = configureStore({
    reducer: { auth: authReducer },
  });
  store.dispatch(
    setUser({
      id: "user-1",
      displayName: "Prof. Turing",
      roleCode: "PROFESSOR",
      roleName: "Professor",
      tenantId: "tenant-1",
      userType: "STAFF",
      platformRole: null,
      photoUrl: null,
      mustChangePassword: false,
    })
  );
  return render(<Provider store={store}>{ui}</Provider>);
}

describe("SelfServicePage (Story #7: SELF_SERVICE_VIEW)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(selfServiceApi.getNotices).mockResolvedValue([
      {
        noticeId: "not-1",
        noticeLevel: "UNIVERSITY",
        noticeKind: "GENERAL",
        title: "Campus Holiday Announcement",
        body: "Campus is closed tomorrow.",
        authoredActiveRole: "REGISTRAR",
        publishedAt: "2026-11-07T00:00:00Z",
      },
    ]);
    vi.mocked(selfServiceApi.getLeaveBalances).mockResolvedValue([
      {
        leaveBalanceId: "bal-1",
        leaveTypeId: "lt-1",
        leaveYear: 2026,
        creditedDays: 20,
        usedDays: 5,
        remainingDays: 15,
      },
    ]);
    vi.mocked(selfServiceApi.getLeaveRequests).mockResolvedValue([
      {
        leaveRequestId: "req-1",
        leaveTypeId: "lt-1",
        unit: "DAYS",
        startDate: "2026-11-10",
        endDate: "2026-11-12",
        reason: "Academic symposium",
        daysCounted: 2,
        processCode: "STAFF_CASUAL_LEAVE",
        status: "APPROVED",
        createdAt: "2026-11-05T00:00:00Z",
      },
    ]);
    vi.mocked(selfServiceApi.getNotifications).mockResolvedValue([
      {
        notificationId: "notif-1",
        triggerCode: "LEAVE_APPROVED",
        title: "Leave approved",
        body: "Your symposium leave was approved.",
        priorityClass: "NORMAL",
        createdAt: "2026-11-06T00:00:00Z",
        readAt: null,
      },
    ]);
  });

  it("loads and displays published campus notices on initial view", async () => {
    renderWithStore(<SelfServicePage />);

    await waitFor(() => {
      expect(screen.getByText("Campus Holiday Announcement")).toBeInTheDocument();
      expect(screen.getByText("Campus is closed tomorrow.")).toBeInTheDocument();
      expect(screen.getByText(/Authored by: REGISTRAR/i)).toBeInTheDocument();
    });
  });

  it("switches to leaves tab and displays balances and requests", async () => {
    renderWithStore(<SelfServicePage />);

    await waitFor(() => {
      expect(screen.getByText("Campus Holiday Announcement")).toBeInTheDocument();
    });

    const leavesTab = screen.getByRole("button", { name: /Leave Balances & History/i });
    fireEvent.click(leavesTab);

    await waitFor(() => {
      expect(screen.getByText("15")).toBeInTheDocument();
      expect(screen.getByText(/days left/i)).toBeInTheDocument();
      expect(screen.getByText("Academic symposium")).toBeInTheDocument();
      expect(screen.getByText("Approved")).toBeInTheDocument();
    });
  });

  it("switches to notifications tab and displays alerts", async () => {
    renderWithStore(<SelfServicePage />);

    await waitFor(() => {
      expect(screen.getByText("Campus Holiday Announcement")).toBeInTheDocument();
    });

    const notifsTab = screen.getByRole("button", { name: /Notifications \(1\)/i });
    fireEvent.click(notifsTab);

    await waitFor(() => {
      expect(screen.getByText("Leave approved")).toBeInTheDocument();
      expect(screen.getByText("Your symposium leave was approved.")).toBeInTheDocument();
    });
  });
});
