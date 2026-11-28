import { useCallback,useEffect,useMemo,useState } from "react";
import type { FormEvent,DragEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import ClashWarningToast from "../components/ClashWarningToast";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";
import { institutionService, type InstitutionItem, type AcademicSessionItem, type SectionItem } from "../../institution-admin/api/institutionService";
import { academicService, type SubjectAllocationItem } from "../../institution-admin/api/academicService";

type Slot={timetable_slot_id:string;section_id:string|null;subject_group_id:string|null;subject_allocation_id:string;
 course_offering_id:string;teacher_id:string|null;teacher_name?:string;room_id:string|null;room_number?:string;
 day_of_week:number;period_no:number;start_time:string;end_time:string;is_online:boolean;status:string;valid_from:string;valid_to:string|null};
const DAYS=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];
const TIMES=Array.from({length:20},(_,i)=>String(8+Math.floor(i/2)).padStart(2,"0")+(i%2?":30":":00"));

export default function TimetableGridView(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);
 const {canAny}=useTenantAccess();
 const canBuild=canAny(["TIMETABLE_BUILD"]);
 const canListAllocations=canAny(["SUBJECT_ALLOCATE"]);
 const canManageStructure=canAny(["STRUCTURE_MANAGE"]);
 const [institutions,setInstitutions]=useState<InstitutionItem[]>([]);
 const [sessions,setSessions]=useState<AcademicSessionItem[]>([]);
 const [sections,setSections]=useState<SectionItem[]>([]);
 const [allocations,setAllocations]=useState<SubjectAllocationItem[]>([]);
 const [rooms,setRooms]=useState<Array<{room_id:string;room_number:string;building?:string;capacity?:number;room_type?:string}>>([]);
 const [optionsLoading,setOptionsLoading]=useState(false);
 const [slotsLoading,setSlotsLoading]=useState(false);
 const [optionsError,setOptionsError]=useState("");
 const [institutionId,setInstitutionId]=useState("");const [sessionId,setSessionId]=useState("");const [sectionId,setSectionId]=useState("");
 const [viewMode,setViewMode]=useState<"SECTION"|"TEACHER"|"ROOM">("SECTION");
 const [teacherFilter,setTeacherFilter]=useState("");const [roomFilter,setRoomFilter]=useState("");const [dateFilter,setDateFilter]=useState("");const [dayFilter,setDayFilter]=useState("");
 const [subjectAllocationId,setSubjectAllocationId]=useState("");const [periodNo,setPeriodNo]=useState("1");const [slots,setSlots]=useState<Slot[]>([]);
 const [roomId,setRoomId]=useState("");const [day,setDay]=useState("1");const [start,setStart]=useState("09:00");const [end,setEnd]=useState("10:00");
 const [validFrom,setValidFrom]=useState(new Date().toISOString().slice(0,10));const [validTo,setValidTo]=useState("");
 const [roomNumber,setRoomNumber]=useState("");const [roomCapacity,setRoomCapacity]=useState("40");const [roomType,setRoomType]=useState("LECTURE_HALL");
 const [isOnline,setIsOnline]=useState(false);const [meetingUrl,setMeetingUrl]=useState("");const [meetingProvider,setMeetingProvider]=useState("ZOOM");
 const [occurrenceDate,setOccurrenceDate]=useState(new Date().toISOString().slice(0,10));
 const [tokenExpiry,setTokenExpiry]=useState(()=>new Date(Date.now()+7*86400000).toISOString().slice(0,16));
 const [dragged,setDragged]=useState<Slot|null>(null);const [toast,setToast]=useState("");const [feedback,setFeedback]=useState("");const [busy,setBusy]=useState(false);
 const [studentAccessToken,setStudentAccessToken]=useState("");const [accessUrl,setAccessUrl]=useState("");
 const selectedInstitution=institutions.find(item=>item.institutionId===institutionId);
 const overlaps=(slot:Slot)=>slot.day_of_week===Number(day)
  &&(!validTo||validTo>=slot.valid_from)&&(!slot.valid_to||slot.valid_to>=validFrom)
  &&start<slot.end_time&&end>slot.start_time;
 const allocationConflict=(allocation:SubjectAllocationItem)=>{
  const sameClass=slots.find(slot=>slot.subject_allocation_id===allocation.subjectAllocationId
   &&(allocation.sectionId?slot.section_id===allocation.sectionId:slot.subject_group_id===allocation.subjectGroupId)
   &&overlaps(slot));
  return sameClass?{slot:sameClass,reason:"Already allocated to this class at "+sameClass.start_time.slice(0,5)+"–"+sameClass.end_time.slice(0,5)}:null;
 };
 const roomConflict=(room:string)=>{
  const occupied=slots.find(slot=>slot.room_id===room&&overlaps(slot));
  return occupied?{slot:occupied,reason:"Room already allocated "+occupied.start_time.slice(0,5)+"–"+occupied.end_time.slice(0,5)}:null;
 };


 useEffect(()=>{
  if(!tenantId)return;
  let active=true;
  setOptionsLoading(true);setOptionsError("");
  institutionService.listInstitutions(tenantId).then(async response=>{
   if(!active)return;
   const available=response.items.filter(item=>item.status==="ACTIVE");
   setInstitutions(available);
   if(!institutionId&&available.length===1)setInstitutionId(available[0].institutionId);
  }).catch(error=>{if(active)setOptionsError(error instanceof Error?error.message:"Unable to load institutions.");})
   .finally(()=>{if(active)setOptionsLoading(false);});
  return ()=>{active=false;};
 },[tenantId]);
 useEffect(()=>{
  if(!tenantId||!institutionId)return;
  let active=true;
  setOptionsLoading(true);setOptionsError("");
  Promise.all([
   canManageStructure?institutionService.listAcademicSessions(tenantId,institutionId):Promise.resolve([] as AcademicSessionItem[]),
   canManageStructure?institutionService.listSections(tenantId,institutionId):Promise.resolve([] as SectionItem[]),
   api.get<typeof rooms>("/api/tenants/"+tenantId+"/timetable/rooms",{params:{institutionId}}).then(res=>res.data),
   canListAllocations?academicService.listSubjectAllocations(tenantId,institutionId):Promise.resolve([] as SubjectAllocationItem[]),
  ]).then(([sessionRows,sectionRows,roomRows,allocationRows])=>{
   if(!active)return;
   setSessions(sessionRows);setSections(sectionRows);setRooms(roomRows);setAllocations(allocationRows);
   if(!sessionId&&sessionRows.length===1)setSessionId(sessionRows[0].academicSessionId);
   if(!sectionId&&sectionRows.length===1)setSectionId(sectionRows[0].sectionId);
  }).catch(error=>{if(active)setOptionsError(error instanceof Error?error.message:"Unable to load timetable setup options.");})
   .finally(()=>{if(active)setOptionsLoading(false);});
  return ()=>{active=false;};
 },[tenantId,institutionId,canListAllocations,canManageStructure]);
 const load=useCallback(async()=>{
  if(!tenantId||!institutionId){setSlots([]);return;}
  setSlotsLoading(true);
  try{const res=await api.get<Slot[]>("/api/tenants/"+tenantId+"/academic/timetable/slots",{params:{institutionId,sectionId:viewMode==="SECTION"?(sectionId||undefined):undefined,teacherId:viewMode==="TEACHER"?(teacherFilter||undefined):undefined,roomId:viewMode==="ROOM"?(roomFilter||undefined):undefined,academicSessionId:sessionId||undefined,date:dateFilter||undefined,dayOfWeek:dayFilter||undefined}});setSlots(res.data);setFeedback("");}
  catch(e){setFeedback(e instanceof Error?e.message:"Unable to load timetable.");}
  finally{setSlotsLoading(false);}
 },[tenantId,institutionId,sectionId,sessionId,viewMode,teacherFilter,roomFilter,dateFilter,dayFilter]);
 useEffect(()=>{void load();},[load]);

 async function createSlot(event:FormEvent){
  event.preventDefault();if(!tenantId)return;setBusy(true);setToast("");setFeedback("");
  try{
   const allocation=allocations.find(item=>item.subjectAllocationId===subjectAllocationId);
   if(!allocation)throw new Error("Choose an active subject allocation before creating a period.");
   const response=await api.post("/api/tenants/"+tenantId+"/timetable/slots",{
    institutionId,sectionId:allocation.sectionId??null,subjectGroupId:allocation.subjectGroupId??null,subjectAllocationId,periodNo:Number(periodNo),dayOfWeek:Number(day),
    startTime:start+":00",endTime:end+":00",roomId:roomId||null,isOnline,validFrom,validTo:validTo||null,
   });
   const created=response.data as Slot;
   const conflicts=slots.filter(slot=>slot.timetable_slot_id!==created.timetable_slot_id
    &&slot.day_of_week===created.day_of_week
    &&(!created.valid_to||created.valid_to>=slot.valid_from)&&(!slot.valid_to||slot.valid_to>=created.valid_from)
    &&created.start_time<slot.end_time&&created.end_time>slot.start_time
    &&((created.teacher_id&&created.teacher_id===slot.teacher_id)||(created.room_id&&created.room_id===slot.room_id)));
   if(conflicts.length)setToast("Saved with a clash warning: a teacher or room is already scheduled during this period. Review before publishing.");
   if(isOnline&&meetingUrl){
    const meeting=await api.post("/api/tenants/"+tenantId+"/timetable/slots/"+response.data.timetable_slot_id+"/meeting-link",{
     institutionId,occurrenceDate,provider:meetingProvider,joinUrl:meetingUrl,externalMeetingId:null,tokenExpiresAt:new Date(tokenExpiry).toISOString(),
    });
    setFeedback("Draft timetable slot created. One-time student meeting token: "+meeting.data.accessToken);
   }else setFeedback("Draft timetable slot created.");
   await load();
  }catch(e){
   const failure=e as {response?:{data?:{code?:string;message?:string}}};const code=failure.response?.data?.code??"";
   setToast(code.includes("TEACHER_DOUBLE_BOOKED")?"Teacher is already scheduled. The current database constraint blocks this save."
    :code.includes("ROOM_OCCUPIED")?"Room is already booked. The current database constraint blocks this save."
    :failure.response?.data?.message??(e instanceof Error?e.message:"Unable to create timetable slot."));
  }finally{setBusy(false);}
 }
 async function publishSlot(slot:Slot){
  if(!tenantId)return;setBusy(true);setFeedback("");setToast("");
  try{await api.post("/api/tenants/"+tenantId+"/timetable/slots/"+slot.timetable_slot_id+"/publish",{}, {params:{institutionId}});
   setFeedback("Timetable slot published.");await load();
  }catch(e){setToast(e instanceof Error?e.message:"Unable to publish timetable slot.");}finally{setBusy(false);}
 }
 async function accessMeeting(event:FormEvent){
  event.preventDefault();if(!tenantId)return;setBusy(true);setFeedback("");setAccessUrl("");
  try{const response=await api.post("/api/tenants/"+tenantId+"/timetable/meeting-link-access",{accessToken:studentAccessToken});
   setAccessUrl(response.data.meetingUrl);setFeedback("Online meeting access verified.");
  }catch(e){setFeedback(e instanceof Error?e.message:"Unable to verify online meeting access.");}finally{setBusy(false);}
 }
 async function createRoom(event:FormEvent){
  event.preventDefault();if(!tenantId)return;setBusy(true);setFeedback("");
  try{const res=await api.post("/api/tenants/"+tenantId+"/timetable/rooms",{institutionId,roomNumber,capacity:Number(roomCapacity),roomType});
   setRoomId(res.data.room_id??res.data.roomId);setFeedback("Room created.");
  }catch(e){setFeedback(e instanceof Error?e.message:"Unable to create room.");}finally{setBusy(false);}
 }
 function dropOn(dayOfWeek:number,time:string,event:DragEvent<HTMLElement>){
  event.preventDefault();if(!dragged||!tenantId||dragged.status!=="DRAFT")return;
  const startAt=time+":00";const old=dragged.start_time.slice(0,5).split(":").map(Number);
  const duration=(Number(dragged.end_time.slice(0,2))*60+Number(dragged.end_time.slice(3,5)))-(old[0]*60+old[1]);
  const endMinutes=Number(time.slice(0,2))*60+Number(time.slice(3,5))+duration;
  const newEnd=String(Math.floor(endMinutes/60)).padStart(2,"0")+":"+String(endMinutes%60).padStart(2,"0")+":00";
  const clashes=slots.some(slot=>{
   const dateOverlap=(!dragged.valid_to||dragged.valid_to>=slot.valid_from)&&(!slot.valid_to||slot.valid_to>=dragged.valid_from);
   const teacherClash=Boolean(dragged.teacher_id&&slot.teacher_id===dragged.teacher_id);
   const roomClash=Boolean(dragged.room_id&&slot.room_id===dragged.room_id);
   return slot.timetable_slot_id!==dragged.timetable_slot_id&&slot.day_of_week===dayOfWeek&&dateOverlap&&(teacherClash||roomClash)
    &&startAt<slot.end_time&&newEnd>slot.start_time;
  });
  if(clashes)setToast("Potential clash detected. The current database may reject this move; review the conflict before retrying.");
  const clashWarning=clashes?"Clash warning: this move overlaps an existing teacher or room booking. The timetable rule allows the change; review the warning before publishing.":"";
  void api.put("/api/tenants/"+tenantId+"/timetable/slots/"+dragged.timetable_slot_id,{
   institutionId,dayOfWeek,periodNo:dragged.period_no,startTime:startAt,endTime:newEnd,roomId:dragged.room_id,
  }).then(()=>{setToast(clashWarning);setFeedback("Draft timetable slot moved.");void load();})
   .catch((e:unknown)=>{const failure=e as {response?:{data?:{code?:string;message?:string}}};const code=failure.response?.data?.code??"";
    setToast(code.includes("TEACHER_DOUBLE_BOOKED")?"Teacher is already scheduled. The current database constraint blocks this move."
     :code.includes("ROOM_OCCUPIED")?"Room is already booked. The current database constraint blocks this move."
     :failure.response?.data?.message??(e instanceof Error?e.message:"The timetable engine rejected this move."));
   });
  setDragged(null);
 }
 const byCell=useMemo(()=>{const map=new Map<string,Slot[]>();slots.forEach(slot=>{const key=slot.day_of_week+"-"+slot.start_time.slice(0,5);map.set(key,[...(map.get(key)??[]),slot]);});return map;},[slots]);

 return <main className="page-container">
  <header className="eds-page-header"><div><h1>Timetable Management</h1><p>Build and review weekly schedules by class, faculty or room. Timetables are created manually in Phase 1.</p></div><span className="eds-badge eds-badge--info">{canBuild?"Builder access":"Read-only oversight"}</span></header>
  {!canBuild&&<div className="notice notice-info" role="status">You can view published timetables for your authorised scope. Creating, moving or publishing periods is restricted to College Admin, School Admin, HOD and Department Coordinator roles.</div>}
  <div className="form-grid">
   <label>Institution<select value={institutionId} onChange={e=>{setInstitutionId(e.target.value);setSessionId("");setSectionId("");setSubjectAllocationId("");}}><option value="">Select institution</option>{institutions.map(item=><option key={item.institutionId} value={item.institutionId}>{item.institutionName}</option>)}</select></label>
   <label>Academic session<select value={sessionId} onChange={e=>setSessionId(e.target.value)}><option value="">All sessions</option>{sessions.map(item=><option key={item.academicSessionId} value={item.academicSessionId}>{item.sessionLabel}</option>)}</select></label>
   <label>View<select value={viewMode} onChange={e=>setViewMode(e.target.value as "SECTION"|"TEACHER"|"ROOM")}><option value="SECTION">Section view</option><option value="TEACHER">Teacher view</option><option value="ROOM">Room view</option></select></label>
  {viewMode==="SECTION"&&canManageStructure&&<label>Class / Section<select value={sectionId} onChange={e=>setSectionId(e.target.value)}><option value="">All sections</option>{sections.map(item=><option key={item.sectionId} value={item.sectionId}>{item.sectionName}</option>)}</select></label>}
  {viewMode==="TEACHER"&&<label>Faculty<select value={teacherFilter} onChange={e=>setTeacherFilter(e.target.value)}><option value="">All faculty</option>{Array.from(new Map(slots.filter(slot=>slot.teacher_id).map(slot=>[slot.teacher_id!,slot.teacher_name??slot.teacher_id!.slice(0,8)])).entries()).map(([id,name])=><option key={id} value={id}>{name}</option>)}</select></label>}
  {viewMode==="ROOM"&&<label>Room<select value={roomFilter} onChange={e=>setRoomFilter(e.target.value)}><option value="">All rooms</option>{rooms.map(item=><option key={item.room_id} value={item.room_id}>{item.room_number}</option>)}</select></label>}
  <label>Effective date<input type="date" value={dateFilter} onChange={e=>setDateFilter(e.target.value)}/></label>
  <label>Day filter<select value={dayFilter} onChange={e=>setDayFilter(e.target.value)}><option value="">All days</option>{DAYS.map((d,i)=><option key={d} value={i+1}>{d}</option>)}</select></label>
   <button type="button" onClick={()=>void load()}>Refresh timetable</button>
  </div>
  {optionsLoading&&<p role="status">Loading institution and timetable setup data…</p>}
  {optionsError&&<div className="eds-error-state" role="alert"><strong>Setup data unavailable</strong><p>{optionsError}</p></div>}
  {feedback&&<p role="status">{feedback}</p>}
  <ClashWarningToast message={toast} onDismiss={()=>setToast("")}/>
  {canBuild&&<>
  <section><h2>Create timetable slot</h2><form onSubmit={createSlot} className="form-grid">
   <label>Subject allocation {canListAllocations?<select required value={subjectAllocationId} onChange={e=>{setSubjectAllocationId(e.target.value);const allocation=allocations.find(item=>item.subjectAllocationId===e.target.value);if(allocation?.sectionId)setSectionId(allocation.sectionId);}}><option value="">Select subject / faculty allocation</option>{allocations.filter(item=>item.status==="ACTIVE"&&(!sectionId||!item.sectionId||item.sectionId===sectionId)).map(item=>{const conflict=allocationConflict(item);return <option key={item.subjectAllocationId} value={item.subjectAllocationId} disabled={Boolean(conflict)} title={conflict?.reason}>{item.courseOfferingId.slice(0,8)} · {item.sectionId?"Section allocation":"Elective group"} · {item.staffAssignmentId.slice(0,8)}{conflict?" — "+conflict.reason:""}</option>;})}</select>:<input required value={subjectAllocationId} onChange={e=>setSubjectAllocationId(e.target.value)} placeholder="Subject allocation reference"/>}{canListAllocations&&<small>Unavailable allocations are disabled and labelled with the existing class period.</small>}{!canListAllocations&&<small>Allocation lookup is permission-restricted. Enter an existing allocation reference supplied by your institution.</small>}</label>
   <label>Period number<input type="number" min="1" max="20" required value={periodNo} onChange={e=>setPeriodNo(e.target.value)}/></label>
   <label>Room<select value={roomId} onChange={e=>setRoomId(e.target.value)}><option value="">No room / online</option>{rooms.map(item=>{const conflict=roomConflict(item.room_id);return <option key={item.room_id} value={item.room_id} disabled={Boolean(conflict)} title={conflict?.reason}>{item.room_number}{item.building?" · "+item.building:""}{conflict?" — "+conflict.reason:""}</option>;})}</select><small>Rooms already booked for the selected day, time and effective dates are disabled.</small></label>
   <label>Day<select value={day} onChange={e=>setDay(e.target.value)}>{DAYS.map((name,i)=><option key={name} value={i+1}>{name}</option>)}</select></label>
   <label>Start<input type="time" value={start} onChange={e=>setStart(e.target.value)}/></label>
   <label>End<input type="time" value={end} onChange={e=>setEnd(e.target.value)}/></label>
   <label>Valid from<input type="date" required value={validFrom} onChange={e=>setValidFrom(e.target.value)}/></label>
   <label>Valid to (optional)<input type="date" min={validFrom} value={validTo} onChange={e=>setValidTo(e.target.value)}/></label>
   <label><input type="checkbox" checked={isOnline} onChange={e=>setIsOnline(e.target.checked)}/> Online period</label>
   {isOnline&&<>
    <label>Meeting URL<input type="url" required placeholder="https://" value={meetingUrl} onChange={e=>setMeetingUrl(e.target.value)}/></label>
    <label>Provider<select value={meetingProvider} onChange={e=>setMeetingProvider(e.target.value)}><option value="ZOOM">Zoom</option><option value="GOOGLE_MEET">Google Meet</option><option value="MS_TEAMS">Microsoft Teams</option></select></label>
    <label>Occurrence date<input type="date" required value={occurrenceDate} onChange={e=>setOccurrenceDate(e.target.value)}/></label>
    <label>Access token expiry<input type="datetime-local" value={tokenExpiry} onChange={e=>setTokenExpiry(e.target.value)}/></label>
   </>}
   <button disabled={busy||!institutionId||!subjectAllocationId||(isOnline&&!meetingUrl)}>Add draft slot</button>
  </form></section>
  <section><h2>Register room</h2><form onSubmit={createRoom} className="form-grid">
   <label>Room number<input required value={roomNumber} onChange={e=>setRoomNumber(e.target.value)}/></label>
   <label>Capacity<input type="number" min="1" max="10000" value={roomCapacity} onChange={e=>setRoomCapacity(e.target.value)}/></label>
   <label>Room type<select value={roomType} onChange={e=>setRoomType(e.target.value)}><option value="LECTURE_HALL">Lecture hall</option><option value="LAB">Lab</option><option value="AUDITORIUM">Auditorium</option></select></label>
   <button disabled={busy||!institutionId}>Create room</button>
  </form></section>
  </>}
  <div className="timetable-grid-heading"><div><h2>Weekly timetable</h2><p>{selectedInstitution?.institutionName??"Select an institution"}{sessionId?" · "+(sessions.find(item=>item.academicSessionId===sessionId)?.sessionLabel??""):""}</p></div><button type="button" onClick={()=>void load()} disabled={!institutionId||busy}>Refresh</button></div><div style={{overflowX:"auto"}}><table className="timetable-workspace-grid"><thead><tr><th>Time</th>{DAYS.map(d=><th key={d}>{d}</th>)}</tr></thead><tbody>
   {slotsLoading?<tr><td colSpan={8}>Loading timetable…</td></tr>:TIMES.map(time=><tr key={time}><th>{time}</th>{DAYS.map((d,index)=>{const cell=byCell.get((index+1)+"-"+time)??[];return <td key={d} onDragOver={e=>e.preventDefault()} onDrop={e=>dropOn(index+1,time,e)} style={{minWidth:130,verticalAlign:"top"}}>
    {cell.map(slot=><div key={slot.timetable_slot_id} draggable={slot.status==="DRAFT"} onDragStart={()=>setDragged(slot)}
     style={{border:"1px solid var(--color-border-divider)",borderRadius:6,padding:6,marginBottom:4,cursor:slot.status==="DRAFT"?"grab":"default"}}>
     <strong>{slot.start_time.slice(0,5)}–{slot.end_time.slice(0,5)}</strong><br/>
     <small>Section {slot.section_id?.slice(0,8)??"Elective"} · {slot.course_offering_id.slice(0,8)} · {slot.teacher_name??slot.teacher_id?.slice(0,8)??"Teacher pending"} · {slot.room_number??slot.room_id?.slice(0,8)??"Online"}</small><br/>
     <small>{slot.status}{slot.is_online?" · Online":""}</small>
     {canBuild&&slot.status==="DRAFT"&&<button type="button" disabled={busy} onClick={()=>void publishSlot(slot)}>Publish slot</button>}
    </div>)}
   </td>;})}</tr>)}
  </tbody></table></div>
  {!slotsLoading&&institutionId&&slots.length===0&&<div className="eds-empty-state"><strong className="eds-empty-state__title">No timetable periods found</strong><p className="eds-empty-state__description">Try another session or filter, or add the first draft period if your role has build access.</p></div>}
  {canBuild&&<section><h2>Online period access</h2><form onSubmit={accessMeeting} className="form-grid">
   <label>Student access token<input required value={studentAccessToken} onChange={e=>setStudentAccessToken(e.target.value)}/></label>
   <button disabled={busy||!studentAccessToken}>Verify meeting access</button>
  </form>{accessUrl&&<p><a href={accessUrl} target="_blank" rel="noopener noreferrer">Open verified meeting link</a></p>}</section>}
 </main>;
}