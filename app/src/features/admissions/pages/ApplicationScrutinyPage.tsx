import { useEffect,useState } from "react";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Panel,Button,TextField,Notice,EmptyState } from "../../../design-system/components";
type Applicant={id:string;application_number:string;applicant_name:string;qualifying_marks_percent:number;verification_status:string;status:string;defect_notes?:string};
export default function ApplicationScrutinyPage(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);const [institutionId,setInstitutionId]=useState("");const [rows,setRows]=useState<Applicant[]>([]);const [notes,setNotes]=useState<Record<string,string>>({});const [error,setError]=useState("");const [busy,setBusy]=useState("");
 const load=async()=>{if(!tenantId||!institutionId)return;try{const r=await api.get(`/tenants/${tenantId}/admissions/scrutiny`,{params:{institutionId}});setRows(r.data);}catch(e){setError(getApiErrorMessage(e));}};
 const decide=async(id:string,decision:string)=>{setBusy(id);setError("");try{await api.post(`/tenants/${tenantId}/admissions/applications/${id}/scrutiny`,{decision,defectNotes:notes[id]??""});await load();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy("");}};
 return <div className="app-page"><PageHeader title="Application scrutiny" description="Review submitted applications and record verification outcomes."/>
 {error&&<Notice tone="danger">{error}</Notice>}<Panel title="Institution scope"><TextField id="scrutiny-institution" label="Institution ID" required value={institutionId} onChange={e=>setInstitutionId(e.target.value)}/><Button onClick={()=>void load()} disabled={!institutionId}>Load applications</Button></Panel>
 <Panel title="Applicant queue">{rows.length===0?<EmptyState title="No applications loaded" description="Enter an institution ID to load its scrutiny queue."/>:rows.map(a=><article key={a.id} className="app-list-row"><div><strong>{a.applicant_name}</strong><p>{a.application_number} · {a.qualifying_marks_percent}% · {a.verification_status}</p></div><TextField id={`defect-${a.id}`} label="Defect notes" value={notes[a.id]??a.defect_notes??""} onChange={e=>setNotes(v=>({...v,[a.id]:e.target.value}))}/><div className="app-inline-actions"><Button size="sm" busy={busy===a.id} onClick={()=>void decide(a.id,"VERIFIED")}>Verify</Button><Button size="sm" variant="secondary" busy={busy===a.id} onClick={()=>void decide(a.id,"DEFECTIVE")}>Request correction</Button><Button size="sm" variant="danger" busy={busy===a.id} onClick={()=>void decide(a.id,"REJECTED")}>Reject</Button></div></article>)}</Panel></div>;
}
