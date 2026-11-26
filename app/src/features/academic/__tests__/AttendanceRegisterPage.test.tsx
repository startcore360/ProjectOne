import { fireEvent,render,screen,waitFor } from "@testing-library/react";
import { beforeEach,describe,expect,it,vi } from "vitest";
import api from "../../../services/axios";

vi.mock("../../../store/hooks",()=>({useAppSelector:(selector:(state:unknown)=>unknown)=>selector({auth:{user:{tenantId:"tenant-1"}}})}));
vi.mock("../../../services/axios",()=>({default:{get:vi.fn(),post:vi.fn(),put:vi.fn()}}));
import AttendanceRegisterPage from "../pages/AttendanceRegisterPage";

describe("AttendanceRegisterPage",()=>{
 beforeEach(()=>vi.clearAllMocks());
 it("loads an enrolled class roster and exposes only canonical attendance statuses",async()=>{
  vi.mocked(api.get).mockResolvedValue({data:[{enrollment_id:"enrol-1",student_profile_id:"student-1",student_code:"S001",full_name:"A Student"}]} as never);
  render(<AttendanceRegisterPage/>);
  fireEvent.change(screen.getByLabelText("Institution ID"),{target:{value:"institution-1"}});
  fireEvent.change(screen.getByLabelText("Section ID"),{target:{value:"section-1"}});
  await waitFor(()=>expect(screen.getByText("A Student")).toBeInTheDocument());
  expect(screen.getByText("Present")).toBeInTheDocument();expect(screen.getByText("Absent")).toBeInTheDocument();
  expect(screen.getByText("Approved leave")).toBeInTheDocument();expect(screen.queryByText(/Late|Excused/)).not.toBeInTheDocument();
 });
 it("submits school half-day attendance using canonical statuses",async()=>{
  vi.mocked(api.get).mockResolvedValue({data:[{enrollment_id:"enrol-1",student_profile_id:"student-1",student_code:"S001",full_name:"A Student"}]} as never);
  vi.mocked(api.post).mockResolvedValueOnce({data:{attendance_session_id:"session-1"}} as never);
  render(<AttendanceRegisterPage/>);
  fireEvent.change(screen.getByLabelText("Institution ID"),{target:{value:"institution-1"}});
  fireEvent.change(screen.getByLabelText("Section ID"),{target:{value:"section-1"}});
  fireEvent.change(screen.getByLabelText("Session kind"),{target:{value:"SCHOOL_FIRST_HALF"}});
  await waitFor(()=>expect(screen.getByText("A Student")).toBeInTheDocument());
  fireEvent.click(screen.getByLabelText("A Student ABSENT"));
  fireEvent.click(screen.getByRole("button",{name:"Save attendance"}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledTimes(2));
  expect(api.post).toHaveBeenNthCalledWith(1,"/api/tenants/tenant-1/academic/attendance/sessions",expect.objectContaining({
   institutionId:"institution-1",sectionId:"section-1",subjectGroupId:null,timetableSlotId:null,subjectAllocationId:null,
   sessionKind:"SCHOOL_FIRST_HALF",notHeld:false,entries:[],
  }));
  expect(api.post).toHaveBeenNthCalledWith(2,"/api/tenants/tenant-1/academic/attendance/entries",{
   institutionId:"institution-1",attendanceSessionId:"session-1",entries:[{enrollmentId:"enrol-1",status:"ABSENT"}],
  });
  expect(await screen.findByText("Attendance session and entries saved.")).toBeInTheDocument();
 });
});