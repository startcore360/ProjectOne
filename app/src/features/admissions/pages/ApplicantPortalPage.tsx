import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useFormErrors } from "../../../hooks";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Panel, Button, TextField, DateField, Notice, SelectField, PhoneField } from "../../../design-system/components";
import { institutionService, type InstitutionItem } from "../../institution-admin/api/institutionService";

type Application={id:string;application_number:string;status:string;verification_status:string;fee_status:string;admission_fee_status?:string;defect_notes?:string};
type Seat={id:string;status:string;quota_code:string;offered_at:string;admission_fee_status?:string};
type Cycle={id:string;cycle_name:string;start_date:string;end_date:string;application_fee:number;status:string};
export default function ApplicantPortalPage(){
 const tenantId=useAppSelector(s=>s.auth.user?.tenantId);
 const [institutions,setInstitutions]=useState<InstitutionItem[]>([]);
 const [institutionId,setInstitutionId]=useState("");const [cycleId,setCycleId]=useState("");const [cycles,setCycles]=useState<Cycle[]>([]);
 const [name,setName]=useState("");const [email,setEmail]=useState("");const [phone,setPhone]=useState("");const [dob,setDob]=useState("");const [marks,setMarks]=useState("");
 const [category,setCategory]=useState("GENERAL");const [application,setApplication]=useState<Application|null>(null);const [seats,setSeats]=useState<Seat[]>([]);
 const [files,setFiles]=useState<File[]>([]);const [photo,setPhoto]=useState<File|null>(null);const [feeReceipt,setFeeReceipt]=useState<File|null>(null);const [admissionFeeReceipt,setAdmissionFeeReceipt]=useState<File|null>(null);
 const [error,setError]=useState("");const [message,setMessage]=useState("");const [busy,setBusy]=useState(false);
 const { fieldErrors, setFieldError, clearFieldError, clearAll, handleApiError } = useFormErrors();
 const chooseFiles=(e:ChangeEvent<HTMLInputElement>)=>setFiles(Array.from(e.target.files??[]));
 const loadCycles=async()=>{setError("");try{const r=await api.get(`/tenants/${tenantId}/admissions/cycles`,{params:{institutionId}});setCycles(r.data.filter((x:Cycle)=>x.status==="OPEN"));}catch(e){setError(getApiErrorMessage(e));}};

 useEffect(()=>{
   if(!tenantId)return;
   let active=true;
   institutionService.listInstitutions(tenantId)
     .then(r=>{if(active)setInstitutions(r.items??[]);})
     .catch(()=>{if(active)setInstitutions([]);});
   return()=>{active=false;};
 },[tenantId]);

 const submit=async(e:FormEvent)=>{e.preventDefault();setBusy(true);setError("");clearAll();try{
  const r=await api.post(`/tenants/${tenantId}/admissions/cycles/${cycleId}/applications`,{applicantName:name,email,phone,dateOfBirth:dob||null,qualifyingMarksPercent:Number(marks),category});
  setApplication(r.data);await loadSeats(r.data.id);setMessage("Application registered. Upload the required supporting documents and application-fee receipt."); 
 }catch(e){handleApiError(e);setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 const upload=async(file:File,type:string)=>{if(file.size>5*1024*1024)throw new Error("Each file must be 5 MB or smaller.");const ext=file.name.split(".").pop()?.toLowerCase();if(!["pdf","jpg","jpeg","png"].includes(ext??""))throw new Error("Only PDF, JPG and PNG files are accepted.");if(type==="PHOTO"&&!["jpg","jpeg","png"].includes(ext??""))throw new Error("Applicant photos must be JPG or PNG.");const body=new FormData();body.append("file",file);body.append("documentType",type);await api.post(`/tenants/${tenantId}/admissions/applications/${application?.id}/documents`,body,{headers:{"Content-Type":"multipart/form-data"}});};
 const uploadAll=async()=>{if(!application)return;setBusy(true);setError("");try{if(photo)await upload(photo,"PHOTO");for(const f of files)await upload(f,"SUPPORTING_DOCUMENT");if(feeReceipt)await upload(feeReceipt,"APPLICATION_FEE_RECEIPT");if(admissionFeeReceipt)await upload(admissionFeeReceipt,"ADMISSION_FEE_RECEIPT");setMessage("Documents uploaded. Files are security-scanned before they can be reviewed or used for fee confirmation.");}catch(e){setError(e instanceof Error?e.message:getApiErrorMessage(e));}finally{setBusy(false);}};
 const loadSeats=async(id:string)=>{try{const r=await api.get(`/tenants/${tenantId}/admissions/applications/${id}/seat-allotments`);setSeats(r.data);}catch(e){setError(getApiErrorMessage(e));}};
 useEffect(()=>{if(!tenantId)return;let active=true;api.get(`/tenants/${tenantId}/admissions/applications/mine`).then(async r=>{const latest=r.data?.[0];if(!active||!latest)return;setApplication(latest);const seatResponse=await api.get(`/tenants/${tenantId}/admissions/applications/${latest.id}/seat-allotments`);if(active)setSeats(seatResponse.data);}).catch(e=>{if(active)setError(getApiErrorMessage(e));});return()=>{active=false;};},[tenantId]);
 const refresh=async()=>{if(!application)return;setBusy(true);try{const r=await api.get(`/tenants/${tenantId}/admissions/applications/${application.id}`);setApplication(r.data);await loadSeats(application.id);}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 const decideSeat=async(id:string,decision:string)=>{setBusy(true);setError("");try{await api.post(`/tenants/${tenantId}/admissions/seat-allotments/${id}/decision`,{decision});await refresh();}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 const resubmit=async()=>{if(!application)return;setBusy(true);try{const r=await api.post(`/tenants/${tenantId}/admissions/applications/${application.id}/corrections/resubmit`);setApplication(r.data);setMessage("Corrected application resubmitted.");}catch(e){setError(getApiErrorMessage(e));}finally{setBusy(false);}};
 return <div className="app-page"><PageHeader title="Applicant portal" description="Register an application, upload supporting documents and track scrutiny and fee status."/>
 {error&&<Notice tone="danger">{error}</Notice>}{message&&<Notice tone="success">{message}</Notice>}
 {!application?<Panel title="Application registration"><form onSubmit={submit} className="app-form-grid">
 <SelectField id="applicant-institution" label="Institution" required value={institutionId} onChange={e=>{setInstitutionId(e.target.value);setCycleId("");}}>
   <option value="">Select an institution</option>
   {institutions.map(inst=><option key={inst.institutionId} value={inst.institutionId}>{inst.institutionName} ({inst.institutionCode})</option>)}
 </SelectField>
 <div className="app-inline-actions" style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}><Button type="button" variant="secondary" onClick={()=>void loadCycles()} disabled={!institutionId}>Load open cycles</Button><SelectField id="applicant-admission-cycle" label="Admission cycle" value={cycleId} onChange={e=>setCycleId(e.target.value)} required><option value="">Select an open admission cycle</option>{cycles.map(c=><option key={c.id} value={c.id}>{c.cycle_name} · {c.start_date} to {c.end_date} · Fee {c.application_fee}</option>)}</SelectField></div>
 <TextField
   id="applicant-name"
   label="Full name"
   required
   value={name}
   onChange={e=>setName(e.target.value)}
   fieldName="name"
   error={fieldErrors.name}
   setFieldError={setFieldError}
   clearFieldError={clearFieldError}
 />
 <TextField
   id="applicant-email"
   label="Email"
   type="email"
   required
   value={email}
   onChange={e=>setEmail(e.target.value)}
   fieldName="email"
   error={fieldErrors.email}
   setFieldError={setFieldError}
   clearFieldError={clearFieldError}
 />
 <PhoneField
   id="applicant-phone"
   label="Mobile number"
   required
   value={phone}
   onChange={e=>{setPhone(e.target.value);clearFieldError("phone");}}
   fieldName="phone"
   error={fieldErrors.phone}
   setFieldError={setFieldError}
   clearFieldError={clearFieldError}
 />
 <DateField id="applicant-dob" label="Date of birth" value={dob} onChange={e=>setDob(e.target.value)}/>
 <TextField id="applicant-marks" label="Qualifying marks (%)" type="number" required value={marks} onChange={e=>setMarks(e.target.value)}/>
 <SelectField id="applicant-category" label="Quota category" value={category} onChange={e=>setCategory(e.target.value)}><option>GENERAL</option><option>MERIT</option><option>SPORTS</option><option>MANAGEMENT</option></SelectField>
 <Button type="submit" busy={busy} disabled={!tenantId||!institutionId||!cycleId||!name||!email||!phone||!marks}>Register application</Button>
 </form></Panel>:<><Panel title="Application status"><p><strong>{application.application_number}</strong> · {application.status}</p><p>Verification: {application.verification_status} · Application fee: {application.fee_status} · Admission fee: {application.admission_fee_status??"PENDING"}</p>{application.defect_notes&&<Notice tone="warning">{application.defect_notes}</Notice>}<div className="app-inline-actions"><Button variant="secondary" onClick={()=>void refresh()} busy={busy}>Refresh status</Button>{application.verification_status==="DEFECTIVE"&&<Button onClick={()=>void resubmit()} busy={busy}>Resubmit corrections</Button>}</div></Panel>
 <Panel title="Seat offers">{seats.length===0?<p>No seat offer is available yet.</p>:seats.map(seat=><article key={seat.id} className="app-list-row"><div><strong>{seat.quota_code} quota</strong><p>{seat.status} · {seat.admission_fee_status??"Admission fee pending"}</p></div>{seat.status==="OFFERED"&&<div className="app-inline-actions"><Button size="sm" onClick={()=>void decideSeat(seat.id,"ACCEPTED")} busy={busy}>Accept seat</Button><Button size="sm" variant="secondary" onClick={()=>void decideSeat(seat.id,"DECLINED")} busy={busy}>Decline</Button></div>}</article>)}</Panel>
 <Panel title="Supporting documents"><p>PDF, JPG or PNG only. Maximum 5 MB per file. Files are virus-scanned before review.</p><label htmlFor="applicant-photo">Applicant photo (JPG or PNG)</label><input id="applicant-photo" type="file" accept=".jpg,.jpeg,.png" onChange={e=>setPhoto(e.target.files?.[0]??null)}/><label htmlFor="applicant-documents">Supporting documents</label><input id="applicant-documents" type="file" accept=".pdf,.jpg,.jpeg,.png" multiple onChange={chooseFiles}/><label htmlFor="applicant-fee-receipt">Application fee receipt</label><input id="applicant-fee-receipt" type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={e=>setFeeReceipt(e.target.files?.[0]??null)}/>{seats.some(seat=>seat.status==="ACCEPTED")&&<><label htmlFor="admission-fee-receipt">Admission fee receipt (after accepting a seat)</label><input id="admission-fee-receipt" type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={e=>setAdmissionFeeReceipt(e.target.files?.[0]??null)}/></>}<Button onClick={()=>void uploadAll()} busy={busy} disabled={!photo&&!files.length&&!feeReceipt&&!admissionFeeReceipt}>Upload documents</Button></Panel></>}
 </div>;
}
