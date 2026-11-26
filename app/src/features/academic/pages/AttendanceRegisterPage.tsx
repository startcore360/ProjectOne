import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";

type Student = { enrollment_id:string; student_profile_id:string; student_code:string; full_name:string };
type Status = "PRESENT"|"ABSENT"|"ON_APPROVED_LEAVE"|"NOT_HELD"|"";
type SessionKind = "DAILY"|"PERIOD"|"SCHOOL_FIRST_HALF"|"SCHOOL_SECOND_HALF";

export default function AttendanceRegisterPage(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);
 const {canAny}=useTenantAccess();
 const canSetRules=canAny(["ATTENDANCE_RULES_SET"]);
 const [institutionId,setInstitutionId]=useState(""); const [departmentId,setDepartmentId]=useState("");
 const [sectionId,setSectionId]=useState(""); const [slotId,setSlotId]=useState(""); const [subjectAllocationId,setSubjectAllocationId]=useState("");
 const [date,setDate]=useState(new Date().toISOString().slice(0,10)); const [sessionKind,setSessionKind]=useState<SessionKind>("PERIOD");
 const [students,setStudents]=useState<Student[]>([]); const [marks,setMarks]=useState<Record<string,Status>>({});
 const [notHeld,setNotHeld]=useState(false); const [busy,setBusy]=useState(false); const [feedback,setFeedback]=useState("");
 const [sessionId,setSessionId]=useState(""); const [threshold,setThreshold]=useState("");

 const loadRoster=useCallback(async()=>{
  if(!tenantId||!institutionId||!sectionId)return;
  setBusy(true);setFeedback("");
  try{const res=await api.get<Student[]>("/api/tenants/"+tenantId+"/attendance/sections/"+sectionId+"/roster",{params:{institutionId}});
   setStudents(res.data);setMarks(Object.fromEntries(res.data.map(x=>[x.enrollment_id,""])));
  }catch(e){setFeedback(e instanceof Error?e.message:"Unable to load class roster.");}finally{setBusy(false);}
 },[tenantId,institutionId,sectionId]);
 useEffect(()=>{void loadRoster();},[loadRoster]);
 useEffect(()=>{
  if(!tenantId||!institutionId)return;
  void api.get("/api/tenants/"+tenantId+"/attendance/thresholds",{params:{institutionId,departmentId:departmentId||undefined}})
   .then(r=>setThreshold(String(r.data.minimum_attendance_percentage))).catch(()=>setThreshold(""));
 },[tenantId,institutionId,departmentId]);

 const counts=useMemo(()=>({present:Object.values(marks).filter(x=>x==="PRESENT").length,
  absent:Object.values(marks).filter(x=>x==="ABSENT").length,leave:Object.values(marks).filter(x=>x==="ON_APPROVED_LEAVE").length}),[marks]);
 function setMark(id:string,status:Status){setMarks(v=>({...v,[id]:status}));}
 function keyboard(id:string,event:KeyboardEvent<HTMLTableRowElement>){
  const key=event.key.toUpperCase();const map:Record<string,Exclude<Status,"">>={P:"PRESENT",A:"ABSENT",O:"ON_APPROVED_LEAVE"};
  if(map[key]){event.preventDefault();setMark(id,map[key]);}
 }
 async function submit(event:FormEvent){
  event.preventDefault();if(!tenantId)return;setBusy(true);setFeedback("");
  try{
   const entries=students.map(s=>({enrollmentId:s.enrollment_id,status:notHeld?"NOT_HELD":marks[s.enrollment_id]}));
   const response=await api.post("/api/tenants/"+tenantId+"/attendance/sessions",{
    institutionId,sectionId,subjectGroupId:null,timetableSlotId:sessionKind==="PERIOD"?slotId:null,
    subjectAllocationId:sessionKind==="PERIOD"?subjectAllocationId:null,attendanceDate:date,sessionKind,notHeld,entries,
   });
   setSessionId(response.data.attendance_session_id);setFeedback("Attendance session saved.");
  }catch(e){setFeedback(e instanceof Error?e.message:"Unable to save attendance.");}finally{setBusy(false);}
 }
 async function saveThreshold(event:FormEvent){
  event.preventDefault();if(!tenantId)return;setBusy(true);setFeedback("");
  try{await api.put("/api/tenants/"+tenantId+"/attendance/thresholds",{institutionId,departmentId:departmentId||null,minimumAttendancePercentage:Number(threshold)});
   setFeedback("Attendance threshold settings saved.");
  }catch(e){setFeedback(e instanceof Error?e.message:"Unable to save attendance threshold.");}finally{setBusy(false);}
 }
 const periodIncomplete=sessionKind==="PERIOD"&&(!slotId||!subjectAllocationId);
 return <main className="page-container">
  <header><h1>Attendance register</h1><p>Capture school half-day or college period attendance. Keyboard shortcuts: P = Present, A = Absent, O = On approved leave. Only the four canonical V4.0 statuses are available.</p></header>
  <div className="form-grid">
   <label>Institution ID<input value={institutionId} onChange={e=>setInstitutionId(e.target.value)}/></label>
   <label>Section ID<input value={sectionId} onChange={e=>setSectionId(e.target.value)}/></label>
   <label>Attendance date<input type="date" value={date} onChange={e=>setDate(e.target.value)}/></label>
   <label>Session kind<select value={sessionKind} onChange={e=>setSessionKind(e.target.value as SessionKind)}>
    <option value="DAILY">Daily attendance</option><option value="PERIOD">College period</option><option value="SCHOOL_FIRST_HALF">School first half</option><option value="SCHOOL_SECOND_HALF">School second half</option>
   </select></label>
   {sessionKind==="PERIOD"&&<>
    <label>Published timetable slot ID<input value={slotId} onChange={e=>setSlotId(e.target.value)}/></label>
    <label>Subject allocation ID<input value={subjectAllocationId} onChange={e=>setSubjectAllocationId(e.target.value)}/></label>
   </>}
   <button type="button" onClick={()=>void loadRoster()} disabled={busy||!institutionId||!sectionId}>Load class roster</button>
  </div>
  {canSetRules&&<section><h2>Exam eligibility threshold (PO-T02)</h2><form onSubmit={saveThreshold} className="form-grid">
   <label>Department ID (blank = institution default)<input value={departmentId} onChange={e=>setDepartmentId(e.target.value)}/></label>
   <label>Minimum attendance %<input type="number" min="0" max="100" step="0.01" required value={threshold} onChange={e=>setThreshold(e.target.value)}/></label>
   <button disabled={busy||!institutionId}>Save threshold</button>
  </form></section>}
  <section><h2>Register summary</h2><p>Present: {counts.present} · Absent: {counts.absent} · On approved leave: {counts.leave} · Total: {students.length}</p></section>
  <form onSubmit={submit}>
   <label><input type="checkbox" checked={notHeld} onChange={e=>setNotHeld(e.target.checked)}/> Session not held</label>
   <div style={{overflowX:"auto"}}><table><thead><tr><th>Student</th><th>Present</th><th>Absent</th><th>Approved leave</th></tr></thead><tbody>
    {students.map(student=><tr key={student.enrollment_id} tabIndex={0} onKeyDown={e=>keyboard(student.enrollment_id,e)}>
     <td>{student.full_name} <small>({student.student_code})</small></td>
     {(["PRESENT","ABSENT","ON_APPROVED_LEAVE"] as Status[]).map(status=><td key={status}>
      <input type="radio" name={student.enrollment_id} checked={notHeld?false:marks[student.enrollment_id]===status} disabled={notHeld}
       onChange={()=>setMark(student.enrollment_id,status)} aria-label={student.full_name+" "+status}/>
     </td>)}
    </tr>)}
   </tbody></table></div>
   <button disabled={busy||students.length===0||!institutionId||!sectionId||periodIncomplete||(!notHeld&&students.some(s=>!marks[s.enrollment_id]))}>
    {busy?"Saving…":"Save attendance"}
   </button>
  </form>
  {feedback&&<p role="status">{feedback} {sessionId&&<small>Session: {sessionId}</small>}</p>}
  <p>Students and primary guardians submit medical certificates from the student portal. Authorized approvers review requests in the medical exemption review workspace.</p>
 </main>;
}