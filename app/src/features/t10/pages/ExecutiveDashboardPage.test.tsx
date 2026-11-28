import { describe,it,expect,vi } from "vitest";
import { render,screen } from "@testing-library/react";
import ExecutiveDashboardPage from "./ExecutiveDashboardPage";
vi.mock("../../../store/hooks",()=>({useAppSelector:(fn:(state:{auth:{user:{tenantId:string}};tenantPermissions:{current:{institutionId:string;allowedActions:string[]}}})=>unknown)=>fn({auth:{user:{tenantId:"tenant-1"}},tenantPermissions:{current:{institutionId:"inst-1",allowedActions:["REPORTS_VIEW"]}}})}));
vi.mock("../api/t10Api",()=>({t10Api:{dashboard:vi.fn().mockResolvedValue({activeStudents:120,activeStaff:20})}}));
describe("ExecutiveDashboardPage",()=>{it("renders the executive dashboard and indicators",async()=>{render(<ExecutiveDashboardPage/>);expect(screen.getByText("Executive Dashboard")).toBeInTheDocument();expect(await screen.findByText("120")).toBeInTheDocument();});});
