import { describe,it,expect,vi } from "vitest";
import { render,screen } from "@testing-library/react";
import LibraryCatalogPage from "./LibraryCatalogPage";
vi.mock("../../../store/hooks",()=>({useAppSelector:(fn:(state:{auth:{user:{tenantId:string}};tenantPermissions:{current:{institutionId:string;allowedActions:string[]}}})=>unknown)=>fn({auth:{user:{tenantId:"tenant-1"}},tenantPermissions:{current:{institutionId:"inst-1",allowedActions:["BOOK_SEARCH_RESERVE"]}}})}));
vi.mock("../api/t10Api",()=>({t10Api:{catalog:vi.fn().mockResolvedValue([])}}));
describe("LibraryCatalogPage",()=>{it("loads catalog and shows the empty state",async()=>{render(<LibraryCatalogPage/>);expect(screen.getByText("Library Operations")).toBeInTheDocument();expect(await screen.findByText("No catalog matches. Add a book if you have catalog permissions.")).toBeInTheDocument();});});
