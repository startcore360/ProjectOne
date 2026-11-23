import { useState } from "react";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Panel,Button,TextField,Notice,EmptyState } from "../../../design-system/components";
import MeritApprovalModal from "../components/MeritApprovalModal";
type MeritList={id:string;status:string;round_number:number;generated_at:string};
export default function MeritListGenerationPage(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);const [cycleId,setCycleId]=useState("");const [round,setRound]=useState("1");const [lists,setLists]=useState<MeritList[]>([]);const [selected,setSelected]=useState("");const [approvalOpen,setApprovalOpen]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState("");const [message,setMessage]=useState("");
 const load=async()=>{try{const r=await api.get(`/tenants/${tenantId}/admissions/cycles/${cycleId}/merit-lists`);setLists(r.data);}catch(e){setError(getApiErrorMessage(e));}};
 const generate=async()=>{setBusy(true);setError("");try{await api.post(`/tenants/${tenantId}/admissions/cycles/${cycleId}/merit-lists`,null,{params:{round:Number(round)}});setMessage("Provisional merit list generated.");await load();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 const approve=async()=>{setBusy(true);try{await api.post(`/tenants/${tenantId}/admissions/merit-lists/${selected}/approval`);setApprovalOpen(false);setMessage("Merit list approved.");await load();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 const allocate=async(id:string)=>{setBusy(true);try{await api.post(`/tenants/${tenantId}/admissions/merit-lists/${id}/seat-allocation`);setMessage("Seat offers created.");}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 return <div className="app-page"><PageHeader title="Merit lists and seat allocation" description="Generate provisional rankings, obtain individual C12 approval and issue quota-based seat offers."/>
 {error&&<Notice tone="danger">{error}</Notice>}{message&&<Notice tone="success">{message}</Notice>}
 <Panel title="Generate provisional ranking"><TextField id="merit-cycle" label="Admission cycle ID" required value={cycleId} onChange={e=>setCycleId(e.target.value)}/><TextField id="merit-round" label="Round" type="number" value={round} onChange={e=>setRound(e.target.value)}/><div className="app-inline-actions"><Button onClick={()=>void generate()} busy={busy} disabled={!cycleId}>Generate merit list</Button><Button variant="secondary" onClick={()=>void load()} disabled={!cycleId}>Load lists</Button></div></Panel>
 <Panel title="Merit list register">{lists.length===0?<EmptyState title="No merit lists" description="Generate a provisional list for a cycle."/>:lists.map(l=><article className="app-list-row" key={l.id}><div><strong>Round {l.round_number}</strong><p>{l.id} · {l.status}</p></div><div className="app-inline-actions"><Button size="sm" variant="secondary" onClick={()=>{setSelected(l.id);setApprovalOpen(true);}} disabled={l.status!=="PROVISIONAL"}>Approve</Button><Button size="sm" onClick={()=>void allocate(l.id)} disabled={l.status!=="APPROVED"}>Allocate seats</Button></div></article>)}</Panel>
 <MeritApprovalModal open={approvalOpen} busy={busy} onClose={()=>setApprovalOpen(false)} onApprove={()=>void approve()}/></div>;
}
