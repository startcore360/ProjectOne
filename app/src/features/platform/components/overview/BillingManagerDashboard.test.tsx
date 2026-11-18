import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "../../../../store/slices/authSlice";
import { BillingManagerDashboard } from "./BillingManagerDashboard";
import { platformOpsService } from "../../api/platformOpsService";

const access = vi.hoisted(() => ({ can: vi.fn<(action: string) => boolean>() }));
vi.mock("../../platformAccess", () => ({ usePlatformAccess: () => access }));
vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: { listInvoices: vi.fn(), listOverdueInvoices: vi.fn(), getReport: vi.fn() },
}));

const ops = vi.mocked(platformOpsService);

function renderDashboard() {
  const store = configureStore({ reducer: { auth: authReducer } });
  return render(<Provider store={store}><BillingManagerDashboard /></Provider>);
}

function resolveSources() {
  ops.listInvoices.mockResolvedValue([]);
  ops.listOverdueInvoices.mockResolvedValue([]);
  ops.getReport.mockResolvedValue({
    currency: "INR",
    metrics: { totalCollectionsThisMonth: 0, renewalsDueIn60Days: 0 },
  } as never);
}

describe("BillingManagerDashboard tile states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    access.can.mockReturnValue(true);
    resolveSources();
  });

  it("shows independent loading states while each source is pending", () => {
    ops.listInvoices.mockReturnValue(new Promise(() => {}) as never);
    ops.listOverdueInvoices.mockReturnValue(new Promise(() => {}) as never);
    ops.getReport.mockReturnValue(new Promise(() => {}) as never);

    renderDashboard();

    expect(screen.getByText("Loading invoices due...")).toBeInTheDocument();
    expect(screen.getByText("Loading invoices paid...")).toBeInTheDocument();
    expect(screen.getByText("Loading invoices overdue...")).toBeInTheDocument();
    expect(screen.getByText("Loading revenue this month...")).toBeInTheDocument();
    expect(screen.getByText("Loading renewals due in 60 days...")).toBeInTheDocument();
  });

  it("counts due, paid and overdue invoices and reads revenue and renewals from the REVENUE report", async () => {
    ops.listInvoices.mockResolvedValue([
      { status: "SENT", overdue: false },
      { status: "PAID", overdue: false },
      { status: "DRAFT", overdue: false },
    ] as never);
    ops.listOverdueInvoices.mockResolvedValue([{ status: "SENT", overdue: true }] as never);
    ops.getReport.mockResolvedValue({
      currency: "INR",
      metrics: { totalCollectionsThisMonth: 125000, renewalsDueIn60Days: 3 },
    } as never);

    renderDashboard();

    await waitFor(() => expect(screen.getByText("Invoices overdue")).toBeInTheDocument());
    expect(screen.getByText("INR 125,000")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getAllByText("1")).toHaveLength(3);
    expect(ops.listInvoices).toHaveBeenCalledTimes(1);
    expect(ops.listOverdueInvoices).toHaveBeenCalledTimes(1);
    expect(ops.getReport).toHaveBeenCalledTimes(1);
    expect(ops.getReport).toHaveBeenCalledWith("REVENUE");
  });

  it("shows real zero values for empty sources", async () => {
    renderDashboard();

    await waitFor(() => expect(screen.getByText("Revenue this month")).toBeInTheDocument());
    expect(screen.getAllByText("0", { exact: true })).toHaveLength(4);
    expect(screen.getByText("INR 0")).toBeInTheDocument();
  });

  it("shows errors independently for invoice and report sources", async () => {
    ops.listInvoices.mockRejectedValue(new Error("invoice source unavailable"));
    ops.listOverdueInvoices.mockRejectedValue(new Error("overdue source unavailable"));
    ops.getReport.mockRejectedValue(new Error("revenue report unavailable"));

    renderDashboard();

    expect(await screen.findAllByText("invoice source unavailable")).toHaveLength(2);
    expect(screen.getByText("overdue source unavailable")).toBeInTheDocument();
    expect(screen.getAllByText("revenue report unavailable")).toHaveLength(2);
  });

  it("denies invoice and report tiles independently by permission", async () => {
    access.can.mockImplementation(action => action !== "INVOICE_MANAGE");

    renderDashboard();

    await waitFor(() => expect(screen.getAllByText("Not available for this role")).toHaveLength(3));
    expect(screen.getAllByText("0", { exact: true })).toHaveLength(1);
    expect(screen.getByText("INR 0")).toBeInTheDocument();
    expect(ops.listInvoices).not.toHaveBeenCalled();
    expect(ops.listOverdueInvoices).not.toHaveBeenCalled();
    expect(ops.getReport).toHaveBeenCalledTimes(1);
  });

  it("keeps missing report metrics explicitly unmeasured without fragile single-text queries", async () => {
    ops.getReport.mockResolvedValue({ currency: "INR", metrics: {} } as never);

    renderDashboard();

    await waitFor(() => expect(screen.getAllByText("Not measured")).toHaveLength(2));
    expect(screen.getByText("Invoices due")).toBeInTheDocument();
    expect(screen.getByText("Invoices paid")).toBeInTheDocument();
    expect(screen.getByText("Invoices overdue")).toBeInTheDocument();
  });
});
