import { useEffect, useState } from "react";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Panel,Button,TextField,SelectField,Notice,EmptyState } from "../../../design-system/components";
import { institutionService, type InstitutionItem } from "../../institution-admin/api/institutionService";
type AdmissionDocument={fileId:string;documentType:string;uploadedAt?:string};
type Applicant={id:string;application_number:string;applicant_name:string;qualifying_marks_percent:number;verification_status:string;status:string;defect_notes?:string;documents_json?:string|AdmissionDocument[];application_fee_receipt_file_id?:string|null;fee_status?:string};
export default function ApplicationScrutinyPage(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);
 const [institutions,setInstitutions]=useState<InstitutionItem[]>([]);
 const [institutionId,setInstitutionId]=useState("");const [rows,setRows]=useState<Applicant[]>([]);const [notes,setNotes]=useState<Record<string,string>>({});const [feeReferences,setFeeReferences]=useState<Record<string,string>>({});const [error,setError]=useState("");const [busy,setBusy]=useState("");

 useEffect(()=>{
   if(!tenantId)return;
   let active=true;
   institutionService.listInstitutions(tenantId)
     .then(r=>{if(active)setInstitutions(r.items??[]);})
     .catch(()=>{if(active)setInstitutions([]);});
   return()=>{active=false;};
 },[tenantId]);

 const load=async()=>{if(!tenantId||!institutionId)return;try{const r=await api.get(`/tenants/${tenantId}/admissions/scrutiny`,{params:{institutionId}});setRows(r.data);}catch(e){setError(getApiErrorMessage(e));}};
 const decide=async(id:string,decision:string)=>{setBusy(id);setError("");try{await api.post(`/tenants/${tenantId}/admissions/applications/${id}/scrutiny`,{decision,defectNotes:notes[id]??""});await load();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy("");}};
 const documents=(raw:Applicant["documents_json"]):AdmissionDocument[]=>{try{const value=typeof raw==="string"?JSON.parse(raw):raw;return Array.isArray(value)?value:[];}catch{return [];}};
 const download=async(fileId:string,documentType:string)=>{setBusy(fileId);setError("");try{const response=await api.get(`/tenants/${tenantId}/files/${fileId}/download`,{responseType:"blob"});const url=window.URL.createObjectURL(response.data);const link=document.createElement("a");link.href=url;link.download=`${documentType}-${fileId}`;document.body.appendChild(link);link.click();link.remove();window.URL.revokeObjectURL(url);}catch(e){setError(getApiErrorMessage(e));}finally{setBusy("");}};
 const confirmApplicationFee=async(a:Applicant)=>{setBusy(a.id);setError("");try{await api.post(`/tenants/${tenantId}/admissions/applications/${a.id}/application-fee-confirmation`,{reference:feeReferences[a.id]??""});await load();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy("");}};
 return <div className="app-page"><PageHeader title="Application scrutiny" description="Review submitted applications and record verification outcomes."/>
 {error&&<Notice tone="danger">{error}</Notice>}<Panel title="Institution scope"><SelectField id="scrutiny-institution" label="Institution" required value={institutionId} onChange={e=>setInstitutionId(e.target.value)}><option value="">Select an institution</option>{institutions.map(inst=><option key={inst.institutionId} value={inst.institutionId}>{inst.institutionName} ({inst.institutionCode})</option>)}</SelectField><Button onClick={()=>void load()} disabled={!institutionId}>Load applications</Button></Panel>
 <Panel title="Applicant queue">{rows.length===0?<EmptyState title="No applications loaded" description="Enter an institution ID to load its scrutiny queue."/>:rows.map(a=><article key={a.id} className="app-list-row"><div><strong>{a.applicant_name}</strong><p>{a.application_number} · {a.qualifying_marks_percent}% · {a.verification_status} · Application fee: {a.fee_status??"PENDING"}</p><div className="app-inline-actions">{documents(a.documents_json).map(doc=><Button key={doc.fileId} size="sm" variant="secondary" busy={busy===doc.fileId} onClick={()=>void download(doc.fileId,doc.documentType)}>{doc.documentType} · Download</Button>)}</div></div><div><TextField id={`defect-${a.id}`} label="Defect notes" value={notes[a.id]??a.defect_notes??""} onChange={e=>setNotes(v=>({...v,[a.id]:e.target.value}))}/>{a.fee_status!=="CONFIRMED"&&a.application_fee_receipt_file_id&&<><TextField id={`fee-reference-${a.id}`} label="Verified payment reference" value={feeReferences[a.id]??""} onChange={e=>setFeeReferences(v=>({...v,[a.id]:e.target.value}))}/><Button size="sm" variant="secondary" busy={busy===a.id} disabled={!feeReferences[a.id]?.trim()} onClick={()=>void confirmApplicationFee(a)}>Confirm application fee</Button></>}</div><div className="app-inline-actions"><Button size="sm" busy={busy===a.id} onClick={()=>void decide(a.id,"VERIFIED")}>Verify</Button><Button size="sm" variant="secondary" busy={busy===a.id} onClick={()=>void decide(a.id,"DEFECTIVE")}>Request correction</Button><Button size="sm" variant="danger" busy={busy===a.id} onClick={()=>void decide(a.id,"REJECTED")}>Reject</Button></div></article>)}</Panel></div>;
}
