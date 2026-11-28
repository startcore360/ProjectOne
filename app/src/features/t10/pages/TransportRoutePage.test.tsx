import { describe,it,expect,vi } from "vitest";
import { render,screen } from "@testing-library/react";
import TransportRoutePage from "./TransportRoutePage";
const mockState = vi.hoisted(() => ({ auth: { user: { tenantId: "tenant-1" } }, tenantPermissions: { current: { institutionId: "inst-1", allowedActions: ["DRIVER_ROUTE_VIEW"] } } }));
vi.mock("../../../store/hooks",()=>({useAppSelector:(fn:(state:typeof mockState)=>unknown)=>fn(mockState)}));
vi.mock("../api/t10Api",()=>({t10Api:{routes:vi.fn().mockResolvedValue([]),routeStops:vi.fn().mockResolvedValue([])}}));
describe("TransportRoutePage",()=>{it("loads institution routes and shows the empty state",async()=>{render(<TransportRoutePage/>);expect(screen.getByText("Transport Tracking")).toBeInTheDocument();expect(await screen.findByText("No transport routes configured for this institution.")).toBeInTheDocument();});});
