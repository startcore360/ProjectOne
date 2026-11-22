import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { StudentDirectoryPage } from "./StudentDirectoryPage";
import { studentApi } from "../api/studentApi";

vi.mock("../api/studentApi",()=>({studentApi:{list:vi.fn(),get:vi.fn(),guardians:vi.fn(),notes:vi.fn(),holds:vi.fn(),addNote:vi.fn()}}));
const store=configureStore({reducer:{auth:(state={user:{tenantId:"tenant-1"}})=>state}});
describe("StudentDirectoryPage",()=>{
 beforeEach(()=>{vi.clearAllMocks();vi.mocked(studentApi.list).mockResolvedValue({data:{items:[{studentProfileId:"student-1",studentCode:"S-001",fullName:"Asha Rao",status:"ACTIVE",institutionId:"inst-1"}],hasMore:false}} as never);});
 it("renders the student directory and navigates on row click",async()=>{render(<Provider store={store}><MemoryRouter initialEntries={["/tenants/students"]}><Routes><Route path="/tenants/students" element={<StudentDirectoryPage/>}/><Route path="/tenants/students/:id" element={<div>Student detail route</div>}/></Routes></MemoryRouter></Provider>);expect(await screen.findByText("Asha Rao")).toBeTruthy();fireEvent.click(screen.getByRole("link",{name:/open asha rao/i}));expect(await screen.findByText("Student detail route")).toBeTruthy();});
 it("sends selected status filter to the directory API",async()=>{render(<Provider store={store}><MemoryRouter><StudentDirectoryPage/></MemoryRouter></Provider>);await screen.findByText("Asha Rao");fireEvent.change(screen.getByLabelText("Status"),{target:{value:"SUSPENDED"}});fireEvent.click(screen.getByRole("button",{name:"Apply filters"}));await waitFor(()=>expect(studentApi.list).toHaveBeenLastCalledWith("tenant-1",expect.objectContaining({status:"SUSPENDED"})));});
});