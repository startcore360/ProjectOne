/** @vitest-environment jsdom */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TicketsView } from "./TicketsView";
import { platformOpsService } from "../../api/platformOpsService";
import type { SupportTicketItem } from "../../api/platformOpsService";

vi.mock("../../platformAccess", () => ({
  usePlatformAccess: () => ({
    can: () => true,
  }),
}));

vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    listSupportTickets: vi.fn(),
    listActiveGrants: vi.fn(),
    updateSupportTicket: vi.fn(),
    escalateSupportTicket: vi.fn(),
    assumeSupportSession: vi.fn(),
    endCurrentSupportSession: vi.fn(),
  },
}));

describe("TicketsView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockTicket: SupportTicketItem = {
    supportTicketId: "ticket-1",
    ticketNumber: "TICK-101",
    subject: "Cannot access gradebook",
    description: "Teacher reports 403 on gradebook view",
    tenantId: "tenant-12345678",
    status: "ASSIGNED",
    priority: "HIGH",
    category: "ACCESS",
    raisedByName: "John Doe",
    raisedByUserId: "user-1",
    createdAt: "2026-03-01T10:00:00Z",
  };

  it("renders resolve modal with proper label-input association and in-modal error on failure", async () => {
    const user = userEvent.setup();
    vi.mocked(platformOpsService.listSupportTickets).mockResolvedValueOnce([mockTicket]);
    vi.mocked(platformOpsService.listActiveGrants).mockResolvedValueOnce([]);
    vi.mocked(platformOpsService.updateSupportTicket).mockRejectedValueOnce(
      new Error("Resolution failed due to locked state")
    );

    render(<TicketsView />);

    await waitFor(() => {
      expect(screen.getByText("TICK-101")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Resolve" }));

    // Verify modal is open and has accessible TextAreaField
    expect(screen.getByRole("heading", { name: /Resolve · TICK-101/i })).toBeInTheDocument();
    const resolutionInput = screen.getByLabelText(/Resolution note/i);
    expect(resolutionInput).toBeInTheDocument();

    fireEvent.change(resolutionInput, { target: { value: "Applied permissions update" } });
    await user.click(screen.getByRole("button", { name: "Confirm resolution" }));

    // Error must display inside the modal and preserve input
    await waitFor(() => {
      expect(screen.getByText("Resolution failed due to locked state")).toBeInTheDocument();
    });
    expect(screen.getByRole("heading", { name: /Resolve · TICK-101/i })).toBeInTheDocument();
    expect((resolutionInput as HTMLTextAreaElement).value).toBe("Applied permissions update");
  }, 15000);

  it("renders escalate modal with SelectField and TextAreaField with proper labels", async () => {
    const user = userEvent.setup();
    const newTicket: SupportTicketItem = { ...mockTicket, status: "TRIAGED" };
    vi.mocked(platformOpsService.listSupportTickets).mockResolvedValueOnce([newTicket]);
    vi.mocked(platformOpsService.listActiveGrants).mockResolvedValueOnce([]);

    render(<TicketsView />);

    await waitFor(() => {
      expect(screen.getByText("TICK-101")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Escalate" }));

    expect(screen.getByRole("heading", { name: /Escalate · TICK-101/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/Escalate to/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Escalation note/i)).toBeInTheDocument();
  });

  it("renders close modal and submits CLOSED status with closing note", async () => {
    const user = userEvent.setup();
    const resolvedTicket: SupportTicketItem = {
      ...mockTicket,
      status: "RESOLVED",
      resolutionNote: "Fixed the user permission",
    };
    vi.mocked(platformOpsService.listSupportTickets).mockResolvedValueOnce([resolvedTicket]);
    vi.mocked(platformOpsService.listActiveGrants).mockResolvedValueOnce([]);
    vi.mocked(platformOpsService.updateSupportTicket).mockResolvedValueOnce({
      ...resolvedTicket,
      status: "CLOSED",
    });

    render(<TicketsView />);

    await waitFor(() => {
      expect(screen.getByText("TICK-101")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Close" }));

    // Verify close modal heading and label
    expect(screen.getByRole("heading", { name: /Close · TICK-101/i })).toBeInTheDocument();
    const closingInput = screen.getByLabelText(/Closing note/i);
    expect(closingInput).toBeInTheDocument();

    fireEvent.change(closingInput, { target: { value: "User confirmed resolution, ticket closed" } });
    await user.click(screen.getByRole("button", { name: "Confirm closure" }));

    await waitFor(() => {
      expect(platformOpsService.updateSupportTicket).toHaveBeenCalledWith("ticket-1", {
        status: "CLOSED",
        resolutionNote: "User confirmed resolution, ticket closed",
      });
    });
  });
});
