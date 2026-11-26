import { describe,it,expect,vi } from "vitest";
import { render,screen } from "@testing-library/react";
import TransportRoutePage from "./TransportRoutePage";
vi.mock("../../../store/hooks",()=>({useAppSelector:(fn:(state:{auth:{user:{tenantId:string}};tenantPermissions:{current:{institutionId:string;allowedActions:string[]}}})=>unknown)=>fn({auth:{user:{tenantId:"tenant-1"}},tenantPermissions:{current:{institutionId:"inst-1",allowedActions:["DRIVER_ROUTE_VIEW"]}}})}));
vi.mock("../api/t10Api",()=>({t10Api:{routes:vi.fn().mockResolvedValue([]),routeStops:vi.fn().mockResolvedValue([])}}));
describe("TransportRoutePage",()=>{it("loads institution routes and shows the empty state",async()=>{render(<TransportRoutePage/>);expect(screen.getByText("Transport Routes")).toBeInTheDocument();expect(await screen.findByText("No transport routes configured for this institution.")).toBeInTheDocument();});});
