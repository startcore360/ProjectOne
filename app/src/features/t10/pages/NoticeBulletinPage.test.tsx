import { describe,it,expect,vi } from "vitest";
import { render,screen } from "@testing-library/react";
import NoticeBulletinPage from "./NoticeBulletinPage";
vi.mock("../../../store/hooks",()=>({useAppSelector:(fn:(state:{auth:{user:{tenantId:string}};tenantPermissions:{current:{institutionId:string;allowedActions:string[]}}})=>unknown)=>fn({auth:{user:{tenantId:"tenant-1"}},tenantPermissions:{current:{institutionId:"inst-1",allowedActions:["NOTICE_PUBLISH_INSTITUTION"]}}})}));
vi.mock("../api/t10Api",()=>({t10Api:{notices:vi.fn().mockResolvedValue([]),publishNotice:vi.fn()}}));
describe("NoticeBulletinPage",()=>{it("shows publisher controls and a useful empty state",async()=>{render(<NoticeBulletinPage/>);expect(screen.getByText("Notice Board & Circulars")).toBeInTheDocument();expect(screen.getByText("Publish a notice")).toBeInTheDocument();expect(await screen.findByText("No published notices")).toBeInTheDocument();});});
