import { useCallback, useEffect, useState } from "react";
import Button from "../../../design-system/components/Button";
import PageHeader from "../../../design-system/components/PageHeader";
import { Panel } from "../../../design-system/components/Panel";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";
import { useAppSelector } from "../../../store/hooks";
import PrintableCertificate from "../components/PrintableCertificate";
import { documentsApi, type CertificateIssuance, type CertificatePrepared, type CertificateRequest, type CertificateTemplate, type CertificateVerification } from "../api/documentsApi";

const inputClass = "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900";
const labelClass = "mb-1 block text-sm font-medium text-slate-700";
const defaultTemplate = `<main style="font-family:Georgia,serif;max-width:760px;margin:48px auto;padding:48px;border:8px double #1e3a8a;text-align:center;color:#172554"><h1>OFFICIAL CERTIFICATE</h1><h2>{{institutionName}}</h2><p>This certifies</p><h1>{{studentName}}</h1><p>Programme: {{programName}}</p><p>{{certificateType}} · {{certificateNumber}}</p><p>Issued: {{issueDate}}</p><p>{{verificationUrl}}</p></main>`;

export default function CertificateRequestPage() {
  const tenantId = useAppSelector(s => s.auth.user?.tenantId);
  const { can } = useTenantAccess();
  const canRequest = can("CERTIFICATE_REQUEST"), canApprove = can("CERTIFICATE_APPROVE");
  const canPrepare = can("CERTIFICATE_PREPARE"), canTemplate = can("CERTIFICATE_TEMPLATE_MANAGE");
  const canOverride = can("NO_DUES_OVERRIDE"), canRevoke = can("CERTIFICATE_REVOKE");
  const canDepartmentClearance = can("DEPARTMENT_EQUIPMENT_CLEARANCE_MANAGE");
  const [institutionId,setInstitutionId]=useState(""),[studentId,setStudentId]=useState("");
  const [departmentId,setDepartmentId]=useState(""),[clearanceStudentId,setClearanceStudentId]=useState("");
  const [outstandingItems,setOutstandingItems]=useState("0"),[clearanceNotes,setClearanceNotes]=useState("");
  const [templateId,setTemplateId]=useState(""),[reason,setReason]=useState("");
  const [templates,setTemplates]=useState<CertificateTemplate[]>([]);
  const [requests,setRequests]=useState<CertificateRequest[]>([]);
  const [issuances,setIssuances]=useState<CertificateIssuance[]>([]);
  const [templateName,setTemplateName]=useState("Official certificate");
  const [templateType,setTemplateType]=useState("BONAFIDE"),[templateHtml,setTemplateHtml]=useState(defaultTemplate);
  const [prepared,setPrepared]=useState<CertificatePrepared|null>(null);
  const [printHtml,setPrintHtml]=useState(""),[printTitle,setPrintTitle]=useState("");
  const [verificationCode,setVerificationCode]=useState("");
  const [verification,setVerification]=useState<CertificateVerification|null>(null);
  const [error,setError]=useState(""),[notice,setNotice]=useState(""),[busy,setBusy]=useState(false);

  const load=useCallback(async()=>{
    if(!tenantId||!institutionId.trim())return;
    setBusy(true);setError("");
    try {
      const tasks:Promise<unknown>[]=[];
      if(canRequest||canApprove||canPrepare||canTemplate)tasks.push(documentsApi.certificateTemplates(tenantId,institutionId).then(setTemplates));
      if(canRequest||canApprove||canPrepare)tasks.push(documentsApi.certificateRequests(tenantId,institutionId).then(setRequests));
      if(canPrepare||canRevoke)tasks.push(documentsApi.certificateIssuances(tenantId,institutionId).then(setIssuances));
      await Promise.all(tasks);
    } catch(e){setError(messageOf(e,"Unable to load certificate records for this institution."));}
    finally{setBusy(false);}
  },[tenantId,institutionId,canRequest,canApprove,canPrepare,canTemplate]);
  useEffect(()=>{setTemplates([]);setRequests([]);setIssuances([]);},[institutionId]);

  async function createTemplate(e:React.FormEvent<HTMLFormElement>){
    e.preventDefault();if(!tenantId)return;setBusy(true);setError("");setNotice("");
    try{const t=await documentsApi.createCertificateTemplate(tenantId,institutionId,{type:templateType,name:templateName,templateHtml});setTemplateId(t.certificateTemplateId);setNotice("Certificate template created.");await load();}
    catch(e){setError(messageOf(e,"Unable to create certificate template."));}finally{setBusy(false);}
  }
  async function submit(e:React.FormEvent<HTMLFormElement>){
    e.preventDefault();if(!tenantId)return;setBusy(true);setError("");setNotice("");
    try{const r=await documentsApi.requestCertificate(tenantId,{studentId,templateId,reason});setNotice(r.status==="NO_DUES_PENDING"?"Request saved; unresolved or unavailable clearance integrations block issuance.":"Certificate request submitted.");setReason("");await load();}
    catch(e){setError(messageOf(e,"Unable to submit certificate request."));}finally{setBusy(false);}
  }
  async function approve(r:CertificateRequest){
    if(!tenantId)return;setBusy(true);setError("");setNotice("");
    try{const result=await documentsApi.approveCertificate(tenantId,r.certificateRequestId);setNotice(result.status==="NO_DUES_PENDING"?"Approval blocked by unresolved No-Dues items.":"Certificate request approved.");await load();}
    catch(e){setError(messageOf(e,"Unable to approve request."));}finally{setBusy(false);}
  }
  async function prepare(r:CertificateRequest){
    if(!tenantId)return;setBusy(true);setError("");setNotice("");
    try{const result=await documentsApi.prepareCertificate(tenantId,r.certificateRequestId);setPrepared(result);setVerificationCode(result.verificationCode);setNotice(`Issued ${result.certificateNumber}; retain the verification code.`);const html=await documentsApi.printCertificate(tenantId,result.certificateIssuanceId);setPrintHtml(html);setPrintTitle(result.certificateNumber);await load();}
    catch(e){setError(messageOf(e,"Unable to prepare certificate. Verify No-Dues and signing-key configuration."));}finally{setBusy(false);}
  }
  async function override(r:CertificateRequest){
    if(!tenantId)return;const why=window.prompt("Reason for overriding the No-Dues blocker:");if(!why?.trim())return;
    setBusy(true);setError("");setNotice("");
    try{await documentsApi.overrideNoDues(tenantId,r.certificateRequestId,why.trim());setNotice("Reasoned No-Dues override recorded.");await load();}
    catch(e){setError(messageOf(e,"Unable to record No-Dues override."));}finally{setBusy(false);}
  }
  async function revoke(i:CertificateIssuance){
    if(!tenantId)return;const why=window.prompt("Reason for revocation:");if(!why?.trim())return;
    setBusy(true);setError("");setNotice("");
    try{await documentsApi.revokeCertificate(tenantId,i.issuanceId,why.trim());setNotice("Certificate revoked.");await load();}
    catch(e){setError(messageOf(e,"Unable to revoke certificate."));}finally{setBusy(false);}
  }
  async function print(i:CertificateIssuance){
    if(!tenantId)return;setBusy(true);setError("");
    try{setPrintHtml(await documentsApi.printCertificate(tenantId,i.issuanceId));setPrintTitle(i.certificateNumber);}
    catch(e){setError(messageOf(e,"Unable to load printable certificate."));}finally{setBusy(false);}
  }
  async function recordDepartmentClearance(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(!tenantId)return;setBusy(true);setError("");setNotice("");
    try{
      const result=await documentsApi.recordDepartmentClearance(tenantId,{
        institutionId,departmentId,studentId:clearanceStudentId,
        outstandingItems:Number(outstandingItems),notes:clearanceNotes,
      });
      setNotice(`Department clearance recorded: ${result.status}.`);
      setClearanceNotes("");setOutstandingItems("0");
    }catch(e){setError(messageOf(e,"Unable to record departmental equipment clearance."));}
    finally{setBusy(false);}
  }
  async function verify(){
    setBusy(true);setError("");setVerification(null);
    try{setVerification(await documentsApi.verifyCertificate(verificationCode.trim()));}
    catch(e){setError(messageOf(e,"Certificate could not be verified."));}finally{setBusy(false);}
  }

  if(!tenantId)return <main className="page-container"><p>Loading account…</p></main>;
  return <main className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
    <PageHeader title="Official certificates" description="Request, approve, prepare, print, revoke and publicly verify certificates with fail-closed No-Dues checks."/>
    {error&&<p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {notice&&<p role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    <Panel title="Institution scope"><div className="flex flex-wrap items-end gap-3"><label className="min-w-[280px] flex-1"><span className={labelClass}>Institution ID</span><input className={inputClass} value={institutionId} onChange={e=>setInstitutionId(e.target.value)}/></label><Button onClick={()=>void load()} busy={busy} disabled={!institutionId.trim()}>Load certificates</Button></div></Panel>
    {canTemplate&&<Panel title="Certificate template management"><form className="space-y-3" onSubmit={createTemplate}>
      <div className="grid gap-3 md:grid-cols-2"><label><span className={labelClass}>Template name</span><input className={inputClass} required value={templateName} onChange={e=>setTemplateName(e.target.value)}/></label>
      <label><span className={labelClass}>Type</span><select className={inputClass} value={templateType} onChange={e=>setTemplateType(e.target.value)}><option value="BONAFIDE">Bonafide</option><option value="TRANSFER_CERTIFICATE">Transfer certificate</option><option value="CONDUCT">Conduct</option><option value="DEGREE">Degree</option></select></label></div>
      <label><span className={labelClass}>Template HTML</span><textarea className={inputClass+" min-h-56 font-mono"} value={templateHtml} onChange={e=>setTemplateHtml(e.target.value)}/></label>
      <p className="text-xs text-slate-500">Tokens: {"{{studentName}}"}, {"{{programName}}"}, {"{{certificateNumber}}"}, {"{{certificateType}}"}, {"{{institutionName}}"}, {"{{issueDate}}"}, {"{{verificationUrl}}"}.</p>
      <Button type="submit" busy={busy}>Create template</Button></form></Panel>}
    {canDepartmentClearance&&<Panel title="Department equipment clearance" description="Each active college department must record zero outstanding equipment before automated No-Dues can clear.">
      <form className="grid gap-3 md:grid-cols-2" onSubmit={recordDepartmentClearance}>
        <label><span className={labelClass}>Institution ID</span><input className={inputClass} required value={institutionId} onChange={e=>setInstitutionId(e.target.value)}/></label>
        <label><span className={labelClass}>Department ID</span><input className={inputClass} required value={departmentId} onChange={e=>setDepartmentId(e.target.value)}/></label>
        <label><span className={labelClass}>Student profile ID</span><input className={inputClass} required value={clearanceStudentId} onChange={e=>setClearanceStudentId(e.target.value)}/></label>
        <label><span className={labelClass}>Outstanding equipment items</span><input className={inputClass} type="number" min="0" max="10000" value={outstandingItems} onChange={e=>setOutstandingItems(e.target.value)}/></label>
        <label className="md:col-span-2"><span className={labelClass}>Notes</span><textarea className={inputClass} maxLength={2000} value={clearanceNotes} onChange={e=>setClearanceNotes(e.target.value)}/></label>
        <div><Button type="submit" busy={busy} disabled={!institutionId||!departmentId||!clearanceStudentId}>Save departmental clearance</Button></div>
      </form>
    </Panel>}
    {canRequest&&<Panel title="Request a certificate" description="Clearance is checked when requesting, approving and preparing. Missing integrations remain blockers.">
      <form className="grid gap-3 md:grid-cols-2" onSubmit={submit}>
        <label><span className={labelClass}>Student profile ID</span><input className={inputClass} required value={studentId} onChange={e=>setStudentId(e.target.value)}/></label>
        <label><span className={labelClass}>Template</span><select className={inputClass} required value={templateId} onChange={e=>setTemplateId(e.target.value)}><option value="">Select template</option>{templates.map(t=><option key={t.certificateTemplateId} value={t.certificateTemplateId}>{t.name} · {t.type}</option>)}</select></label>
        <label className="md:col-span-2"><span className={labelClass}>Reason</span><textarea className={inputClass} required maxLength={1000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
        <div><Button type="submit" busy={busy} disabled={!institutionId||!templateId}>Submit request</Button></div>
      </form></Panel>}
    {(canRequest||canApprove||canPrepare)&&<Panel title="Certificate requests" description="Requesters see only their own requests; preparation roles see the institution queue.">
      <div className="space-y-3">{requests.map(r=>{
        let blockers:Array<{source?:string;code?:string;description?:string}>=[];
        try{blockers=JSON.parse(r.duesJson) as typeof blockers;}catch{/* show request status only */}
        return <article key={r.certificateRequestId} className="rounded-lg border border-slate-200 p-3"><div className="flex flex-wrap items-start justify-between gap-3">
          <div><p className="font-semibold">{r.certificateRequestId}</p><p className="text-sm text-slate-600">{r.reason}</p><p className="mt-1 text-xs font-semibold uppercase">{r.status}</p></div>
          <div className="flex flex-wrap gap-2">{canApprove&&r.status!=="ISSUED"&&<Button size="sm" onClick={()=>void approve(r)} busy={busy}>Approve</Button>}
            {canOverride&&r.status==="NO_DUES_PENDING"&&<Button size="sm" variant="secondary" onClick={()=>void override(r)} busy={busy}>Override No-Dues</Button>}
            {canPrepare&&r.status==="APPROVED"&&<Button size="sm" onClick={()=>void prepare(r)} busy={busy}>Prepare / issue</Button>}</div></div>
          {blockers.length>0&&<ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-amber-800">{blockers.map((x,i)=><li key={i}>{x.source}: {x.description} ({x.code})</li>)}</ul>}
        </article>;
      })}{requests.length===0&&<p className="text-sm text-slate-500">No requests loaded.</p>}</div>
    </Panel>}
    {(canPrepare||canRevoke)&&<Panel title="Issued certificates"><div className="space-y-2">{issuances.map(i=><div key={i.issuanceId} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
      <div><p className="font-medium">{i.certificateNumber}</p><p className="text-sm text-slate-600">Issued {i.issuedAt} · {i.revokedAt?"REVOKED":"VALID"}</p></div>
      <div className="flex gap-2">{canPrepare&&<Button size="sm" variant="secondary" onClick={()=>void print(i)} busy={busy}>Print</Button>}{canRevoke&&!i.revokedAt&&<Button size="sm" variant="danger" onClick={()=>void revoke(i)} busy={busy}>Revoke</Button>}</div>
    </div>)}{issuances.length===0&&<p className="text-sm text-slate-500">No issued certificates loaded.</p>}</div></Panel>}
    {prepared&&<Panel title="Verification code"><p className="break-all rounded-lg bg-slate-50 p-3 font-mono text-sm">{prepared.verificationCode}</p><div className="mt-3 flex flex-wrap gap-3"><Button size="sm" variant="secondary" onClick={()=>void navigator.clipboard.writeText(prepared.verificationCode)}>Copy code</Button><a className="text-sm font-medium text-indigo-700 underline" href={prepared.verificationUrl} target="_blank" rel="noreferrer">Public verification</a></div></Panel>}
    {printHtml&&<PrintableCertificate html={printHtml} title={printTitle}/>}
    <Panel title="Public certificate verification" description="Code possession returns only certificate status and minimal public metadata."><div className="flex flex-wrap items-end gap-3">
      <label className="min-w-[280px] flex-1"><span className={labelClass}>Verification code</span><input className={inputClass} value={verificationCode} onChange={e=>setVerificationCode(e.target.value)}/></label>
      <Button variant="secondary" onClick={()=>void verify()} busy={busy} disabled={!verificationCode.trim()}>Verify</Button></div>
      {verification&&<dl className="mt-4 grid gap-2 rounded-lg border border-slate-200 p-3 text-sm md:grid-cols-2">
        <div><dt className="text-slate-500">Status</dt><dd className="font-semibold">{verification.status}</dd></div><div><dt className="text-slate-500">Type</dt><dd>{verification.certificateType}</dd></div>
        <div><dt className="text-slate-500">Student</dt><dd>{verification.studentName}</dd></div><div><dt className="text-slate-500">Programme</dt><dd>{verification.programName}</dd></div>
        <div><dt className="text-slate-500">Institution</dt><dd>{verification.institutionName}</dd></div><div><dt className="text-slate-500">Issued</dt><dd>{verification.issuedAt}</dd></div>
      </dl>}
    </Panel>
  </main>;
}

function messageOf(error:unknown,fallback:string){return (error as {response?:{data?:{message?:string}}})?.response?.data?.message??fallback;}
