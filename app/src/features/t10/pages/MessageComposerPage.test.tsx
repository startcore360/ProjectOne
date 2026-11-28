import { describe,it,expect,vi } from "vitest";
import { render,screen } from "@testing-library/react";
import MessageComposerPage from "./MessageComposerPage";
const mockState = vi.hoisted(() => ({ auth: { user: { tenantId: "tenant-1" } }, tenantPermissions: { current: { institutionId: "inst-1", allowedActions: ["MESSAGE_SEND","DELIVERY_FAILURES_VIEW"] } } }));
vi.mock("../../../store/hooks",()=>({useAppSelector:(fn:(state:typeof mockState)=>unknown)=>fn(mockState)}));
vi.mock("../api/t10Api",()=>({t10Api:{dispatch:vi.fn(),dispatchHistory:vi.fn().mockResolvedValue([])}}));
describe("MessageComposerPage",()=>{it("shows composer and empty dispatch history",async()=>{render(<MessageComposerPage/>);expect(screen.getByText("Communications & Dispatch")).toBeInTheDocument();expect(await screen.findByText("No dispatch attempts recorded for this institution.")).toBeInTheDocument();});});
