import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import api from "../../../../services/axios";
import { BackupStatusView } from "./BackupStatusView";

vi.mock("../../../../services/axios", () => ({
  default: { get: vi.fn() },
}));

const mockedApi = api as unknown as { get: ReturnType<typeof vi.fn> };

beforeEach(() => {
  mockedApi.get.mockReset();
});

describe("BackupStatusView", () => {
  it("shows the honest not-connected state when no backup system is wired", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        wiredToRealBackupSystem: false,
        note: "No backup/PITR system writes status rows yet.",
        tenants: [],
      },
    });

    render(<BackupStatusView />);

    expect(await screen.findByText("Not connected to a real backup system.")).toBeInTheDocument();
    expect(screen.getByText("No backup/PITR system writes status rows yet.")).toBeInTheDocument();
    expect(screen.getByText("No backup status rows recorded yet.")).toBeInTheDocument();
  });
});
