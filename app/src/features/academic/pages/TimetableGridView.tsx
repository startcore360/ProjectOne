import { useCallback,useEffect,useMemo,useState } from "react";
import type { FormEvent,DragEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import ClashWarningToast from "../components/ClashWarningToast";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";

type Slot={timetable_slot_id:string;section_id:string|null;subject_group_id:string|null;subject_allocation_id:string;
 course_offering_id:string;teacher_id:string|null;teacher_name?:string;room_id:string|null;room_number?:string;
 day_of_week:number;period_no:number;start_time:string;end_time:string;is_online:boolean;status:string;valid_from:string;valid_to:string|null};
const DAYS=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];
const TIMES=Array.from({length:20},(_,i)=>String(8+Math.floor(i/2)).padStart(2,"0")+(i%2?":30":":00"));

export default function TimetableGridView(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);
 const {canAny}=useTenantAccess();
 const canBuild=canAny(["TIMETABLE_BUILD"]);
 const [institutionId,setInstitutionId]=useState("");const [sessionId,setSessionId]=useState("");const [sectionId,setSectionId]=useState("");
 const [subjectAllocationId,setSubjectAllocationId]=useState("");const [periodNo,setPeriodNo]=useState("1");const [slots,setSlots]=useState<Slot[]>([]);
 const [roomId,setRoomId]=useState("");const [day,setDay]=useState("1");const [start,setStart]=useState("09:00");const [end,setEnd]=useState("10:00");
 const [validFrom,setValidFrom]=useState(new Date().toISOString().slice(0,10));const [validTo,setValidTo]=useState("");
 const [roomNumber,setRoomNumber]=useState("");const [roomCapacity,setRoomCapacity]=useState("40");const [roomType,setRoomType]=useState("LECTURE_HALL");
 const [isOnline,setIsOnline]=useState(false);const [meetingUrl,setMeetingUrl]=useState("");const [meetingProvider,setMeetingProvider]=useState("ZOOM");
 const [occurrenceDate,setOccurrenceDate]=useState(new Date().toISOString().slice(0,10));
 const [tokenExpiry,setTokenExpiry]=useState(()=>new Date(Date.now()+7*86400000).toISOString().slice(0,16));
 const [dragged,setDragged]=useState<Slot|null>(null);const [toast,setToast]=useState("");const [feedback,setFeedback]=useState("");const [busy,setBusy]=useState(false);
 const [studentAccessToken,setStudentAccessToken]=useState("");const [accessUrl,setAccessUrl]=useState("");

 const load=useCallback(async()=>{
  if(!tenantId||!institutionId)return;
  try{const res=await api.get<Slot[]>("/api/tenants/"+tenantId+"/timetable",{params:{institutionId,sectionId:sectionId||undefined,academicSessionId:sessionId||undefined}});setSlots(res.data);}
  catch(e){setFeedback(e instanceof Error?e.message:"Unable to load timetable.");}
 },[tenantId,institutionId,sectionId,sessionId]);
 useEffect(()=>{void load();},[load]);

 async function createSlot(event:FormEvent){
  event.preventDefault();if(!tenantId)return;setBusy(true);setToast("");setFeedback("");
  try{
   const response=await api.post("/api/tenants/"+tenantId+"/timetable/slots",{
    institutionId,sectionId,subjectGroupId:null,subjectAllocationId,periodNo:Number(periodNo),dayOfWeek:Number(day),
    startTime:start+":00",endTime:end+":00",roomId:roomId||null,isOnline,validFrom,validTo:validTo||null,
   });
   if(isOnline&&meetingUrl){
    const meeting=await api.post("/api/tenants/"+tenantId+"/timetable/slots/"+response.data.timetable_slot_id+"/meeting-link",{
     institutionId,occurrenceDate,provider:meetingProvider,joinUrl:meetingUrl,externalMeetingId:null,tokenExpiresAt:new Date(tokenExpiry).toISOString(),
    });
    setFeedback("Draft timetable slot created. One-time student meeting token: "+meeting.data.accessToken);
   }else setFeedback("Draft timetable slot created.");
   await load();
  }catch(e){
   const failure=e as {response?:{data?:{code?:string;message?:string}}};const code=failure.response?.data?.code??"";
   setToast(code.includes("TEACHER_DOUBLE_BOOKED")?"Teacher already has a slot at this time."
    :code.includes("ROOM_OCCUPIED")?"Room is already occupied at this time."
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
  if(clashes){setToast("Move blocked: the teacher or room is already booked in that time range.");setDragged(null);return;}
  void api.put("/api/tenants/"+tenantId+"/timetable/slots/"+dragged.timetable_slot_id,{
   institutionId,dayOfWeek,periodNo:dragged.period_no,startTime:startAt,endTime:newEnd,roomId:dragged.room_id,
  }).then(()=>{setToast("");setFeedback("Draft timetable slot moved.");void load();})
   .catch((e:unknown)=>{const failure=e as {response?:{data?:{code?:string;message?:string}}};const code=failure.response?.data?.code??"";
    setToast(code.includes("TEACHER_DOUBLE_BOOKED")?"Teacher already has a slot at this time."
     :code.includes("ROOM_OCCUPIED")?"Room is already occupied at this time."
     :failure.response?.data?.message??(e instanceof Error?e.message:"The timetable engine rejected this move."));
   });
  setDragged(null);
 }
 const byCell=useMemo(()=>{const map=new Map<string,Slot[]>();slots.forEach(slot=>{const key=slot.day_of_week+"-"+slot.start_time.slice(0,5);map.set(key,[...(map.get(key)??[]),slot]);});return map;},[slots]);

 return <main className="page-container">
  <header><h1>Weekly timetable</h1><p>Slots use the existing subject-allocation model. PostgreSQL GiST constraints enforce teacher and room clashes across overlapping effective dates.</p></header>
  <div className="form-grid">
   <label>Institution ID<input value={institutionId} onChange={e=>setInstitutionId(e.target.value)}/></label>
   <label>Academic session ID (filter)<input value={sessionId} onChange={e=>setSessionId(e.target.value)}/></label>
   <label>Section ID<input value={sectionId} onChange={e=>setSectionId(e.target.value)}/></label>
   <button type="button" onClick={()=>void load()}>Refresh timetable</button>
  </div>
  <ClashWarningToast message={toast} onDismiss={()=>setToast("")}/>{feedback&&<p role="status">{feedback}</p>}
  {canBuild&&<>
  <section><h2>Create timetable slot</h2><form onSubmit={createSlot} className="form-grid">
   <label>Subject allocation ID<input required value={subjectAllocationId} onChange={e=>setSubjectAllocationId(e.target.value)}/></label>
   <label>Period number<input type="number" min="1" max="20" required value={periodNo} onChange={e=>setPeriodNo(e.target.value)}/></label>
   <label>Room ID (optional)<input value={roomId} onChange={e=>setRoomId(e.target.value)}/></label>
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
   <button disabled={busy||!institutionId||!sectionId||!subjectAllocationId||(isOnline&&!meetingUrl)}>Add draft slot</button>
  </form></section>
  <section><h2>Register room</h2><form onSubmit={createRoom} className="form-grid">
   <label>Room number<input required value={roomNumber} onChange={e=>setRoomNumber(e.target.value)}/></label>
   <label>Capacity<input type="number" min="1" max="10000" value={roomCapacity} onChange={e=>setRoomCapacity(e.target.value)}/></label>
   <label>Room type<select value={roomType} onChange={e=>setRoomType(e.target.value)}><option value="LECTURE_HALL">Lecture hall</option><option value="LAB">Lab</option><option value="AUDITORIUM">Auditorium</option></select></label>
   <button disabled={busy||!institutionId}>Create room</button>
  </form></section>
  </>}
  <h2>Weekly grid</h2><div style={{overflowX:"auto"}}><table><thead><tr><th>Time</th>{DAYS.map(d=><th key={d}>{d}</th>)}</tr></thead><tbody>
   {TIMES.map(time=><tr key={time}><th>{time}</th>{DAYS.map((d,index)=>{const cell=byCell.get((index+1)+"-"+time)??[];return <td key={d} onDragOver={e=>e.preventDefault()} onDrop={e=>dropOn(index+1,time,e)} style={{minWidth:130,verticalAlign:"top"}}>
    {cell.map(slot=><div key={slot.timetable_slot_id} draggable={slot.status==="DRAFT"} onDragStart={()=>setDragged(slot)}
     style={{border:"1px solid var(--border, #ccc)",borderRadius:6,padding:6,marginBottom:4,cursor:slot.status==="DRAFT"?"grab":"default"}}>
     <strong>{slot.start_time.slice(0,5)}–{slot.end_time.slice(0,5)}</strong><br/>
     <small>{slot.course_offering_id.slice(0,8)} · {slot.teacher_name??slot.teacher_id?.slice(0,8)??"Teacher pending"} · {slot.room_number??slot.room_id?.slice(0,8)??"Online"}</small><br/>
     <small>{slot.status}{slot.is_online?" · Online":""}</small>
     {canBuild&&slot.status==="DRAFT"&&<button type="button" disabled={busy} onClick={()=>void publishSlot(slot)}>Publish slot</button>}
    </div>)}
   </td>;})}</tr>)}
  </tbody></table></div>
  <section><h2>Join an online meeting</h2><form onSubmit={accessMeeting} className="form-grid">
   <label>Student access token<input required value={studentAccessToken} onChange={e=>setStudentAccessToken(e.target.value)}/></label>
   <button disabled={busy||!studentAccessToken}>Verify and open meeting</button>
  </form>{accessUrl&&<p><a href={accessUrl} target="_blank" rel="noopener noreferrer">Open verified meeting link</a></p>}</section>
 </main>;
}