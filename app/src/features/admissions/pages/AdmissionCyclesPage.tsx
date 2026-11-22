import { useCallback, useEffect, useState, type FormEvent } from "react";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Panel, Button, TextField, DateField, Notice, DataTable, EmptyState } from "../../../design-system/components";
type Cycle={id:string;cycle_name:string;status:string;start_date:string;end_date:string;total_seats:number;application_fee:number};
export default function AdmissionCyclesPage(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);
 const [rows,setRows]=useState<Cycle[]>([]),[institutionId,setInstitutionId]=useState(""),[academicYearId,setAcademicYearId]=useState(""),[programId,setProgramId]=useState(""),[name,setName]=useState(""),[start,setStart]=useState(""),[end,setEnd]=useState(""),[seats,setSeats]=useState(""),[fee,setFee]=useState("0"),[busy,setBusy]=useState(false),[error,setError]=useState(""),[success,setSuccess]=useState("");
 const load=useCallback(async()=>{if(!tenantId)return;try{const r=await api.get(`/tenants/${tenantId}/admissions/cycles`);setRows(r.data);}catch(e){setError(getApiErrorMessage(e));}},[tenantId]);
 useEffect(()=>{void load();},[load]);
 const submit=async(e:FormEvent)=>{e.preventDefault();setBusy(true);setError("");setSuccess("");try{await api.post(`/tenants/${tenantId}/admissions/cycles`,{institutionId,academicYearId,programId,cycleName:name,startDate:start,endDate:end,totalSeats:Number(seats),applicationFee:Number(fee)});setSuccess("Admission cycle created.");setName("");await load();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 return <div className="app-page"><PageHeader title="Admission cycles" description="Configure program intake windows, application fees and seat capacity."/>
 {error&&<Notice tone="danger">{error}</Notice>}{success&&<Notice tone="success">{success}</Notice>}
 <Panel title="Create admission cycle"><form onSubmit={submit} className="app-form-grid">
 <TextField id="admission-institution" label="Institution ID" required value={institutionId} onChange={e=>setInstitutionId(e.target.value)}/>
 <TextField id="admission-year" label="Academic session ID" required value={academicYearId} onChange={e=>setAcademicYearId(e.target.value)}/>
 <TextField id="admission-program" label="Program ID" required value={programId} onChange={e=>setProgramId(e.target.value)}/>
 <TextField id="admission-cycle-name" label="Cycle name" required value={name} onChange={e=>setName(e.target.value)}/>
 <DateField id="admission-start" label="Application opens" required value={start} onChange={e=>setStart(e.target.value)}/>
 <DateField id="admission-end" label="Application closes" required value={end} onChange={e=>setEnd(e.target.value)}/>
 <TextField id="admission-seats" label="Total seats" type="number" required value={seats} onChange={e=>setSeats(e.target.value)}/>
 <TextField id="admission-fee" label="Application fee" type="number" value={fee} onChange={e=>setFee(e.target.value)}/>
 <Button type="submit" busy={busy} disabled={!institutionId||!academicYearId||!programId||!name||!start||!end||!seats}>Create cycle</Button>
 </form></Panel>
 <Panel title="Configured cycles">{rows.length===0?<EmptyState title="No admission cycles" description="Create a cycle to begin applicant intake."/>:<DataTable headers={["Cycle","Opens","Closes","Seats","Status"]}>{rows.map(row=><tr key={row.id}><td>{row.cycle_name}</td><td>{row.start_date}</td><td>{row.end_date}</td><td>{row.total_seats}</td><td>{row.status}</td></tr>)}</DataTable>}</Panel></div>;
}
