import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import api from "../../../../services/axios";
import { EntitlementEditor } from "./EntitlementEditor";

vi.mock("../../../../services/axios", () => ({ default: { get: vi.fn(), put: vi.fn() } }));
const mocked = api as unknown as { get: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn> };

const modules = [
  { moduleCode: "EXAMINATIONS", moduleName: "Exams", phase: 1, enabled: true, planEnabled: true, source: "PLAN", requires: [], features: [] },
  { moduleCode: "RESULTS", moduleName: "Results", phase: 1, enabled: true, planEnabled: true, source: "PLAN", requires: ["EXAMINATIONS"], features: [] },
  {
    moduleCode: "FEES", moduleName: "Fees", phase: 1, enabled: false, planEnabled: false, source: "PLAN", requires: [],
    features: [{ featureCode: "FEES_ONLINE_PAYMENT", key: "FEES:FEES_ONLINE_PAYMENT", description: "Online payment", provisional: true, enabled: false, planEnabled: false, source: "PLAN" }],
  },
  { moduleCode: "LIBRARY", moduleName: "Library", phase: 2, enabled: false, planEnabled: false, source: "PLAN", requires: [], features: [] },
];

const open = (target: "plan" | "tenant", isProductOwner: boolean) => {
  mocked.get.mockResolvedValue({ data: { modules } });
  render(<EntitlementEditor isOpen target={target} targetId="id-1" targetName="Doon" isProductOwner={isProductOwner}
    onClose={vi.fn()} onSaved={vi.fn()} />);
};

describe("EntitlementEditor (E2-S2)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("explains that a module still needed by another can't be turned off", async () => {
    open("plan", true);
    const exams = await screen.findByLabelText("Exams");
    expect((exams as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByText(/Results still need this module/)).toBeTruthy();
  });

  it("keeps a feature switch locked until its module is on, and marks provisional features and later phases", async () => {
    open("plan", true);
    fireEvent.click(await screen.findByRole("button", { name: "1 features" }));
    expect((screen.getByLabelText("Online payment") as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByText("Provisional")).toBeTruthy();
    expect(screen.getByText("Phase 2")).toBeTruthy();

    fireEvent.click(screen.getByLabelText("Fees"));
    expect((screen.getByLabelText("Online payment") as HTMLInputElement).disabled).toBe(false);
  });

  it("on a tenant, a Platform Admin can't switch on what the plan leaves off; the Product Owner can", async () => {
    open("tenant", false);
    expect(((await screen.findByLabelText("Library")) as HTMLInputElement).disabled).toBe(true);
    expect(screen.getAllByText(/Product Owner only/).length).toBeGreaterThan(0);
    cleanup();

    open("tenant", true);
    expect(((await screen.findByLabelText("Library")) as HTMLInputElement).disabled).toBe(false);
  });

  it("saves only the changed switches", async () => {
    mocked.put.mockResolvedValue({ data: {} });
    open("plan", true);
    fireEvent.click(await screen.findByLabelText("Fees"));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith("/platform/plans/id-1/entitlements", { features: { FEES: true } }));
  });
});
