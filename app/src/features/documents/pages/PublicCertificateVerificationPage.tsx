import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import Button from "../../../design-system/components/Button";
import PageHeader from "../../../design-system/components/PageHeader";
import { Panel } from "../../../design-system/components/Panel";
import { documentsApi, type CertificateVerification } from "../api/documentsApi";

const field="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm";
export default function PublicCertificateVerificationPage(){
 const [params]=useSearchParams();
 const [code,setCode]=useState(params.get("code")??"");
 const [result,setResult]=useState<CertificateVerification|null>(null);
 const [error,setError]=useState(""),[busy,setBusy]=useState(false);
 async function verify(value=code){
  if(!value.trim())return;setBusy(true);setError("");setResult(null);
  try{setResult(await documentsApi.verifyCertificate(value.trim()));}
  catch(e){setError((e as {response?:{data?:{message?:string}}})?.response?.data?.message??"Certificate could not be verified. Check the code or contact the issuing institution.");}
  finally{setBusy(false);}
 }
 useEffect(()=>{const initial=params.get("code");if(initial){setCode(initial);void verify(initial);}},[]);
 return <main className="mx-auto max-w-3xl space-y-6 p-4 md:p-8">
  <PageHeader title="Public certificate verification" description="Verify an official EDU 360 certificate using its signed verification code. No login is required."/>
  <Panel title="Verify certificate"><div className="space-y-3"><label className="block text-sm font-medium text-slate-700">Verification code<input className={field+" mt-1"} value={code} onChange={e=>setCode(e.target.value)} placeholder="Paste the certificate verification code"/></label><Button onClick={()=>void verify()} busy={busy} disabled={!code.trim()}>Verify certificate</Button></div></Panel>
  {error&&<p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>}
  {result&&<Panel title="Verification result"><p className={result.status==="VALID"?"text-lg font-semibold text-emerald-700":"text-lg font-semibold text-red-700"}>{result.status}</p><dl className="mt-3 grid gap-3 text-sm md:grid-cols-2">
    <div><dt className="text-slate-500">Certificate type</dt><dd>{result.certificateType}</dd></div>
    <div><dt className="text-slate-500">Student</dt><dd>{result.studentName}</dd></div>
    <div><dt className="text-slate-500">Programme</dt><dd>{result.programName}</dd></div>
    <div><dt className="text-slate-500">Institution</dt><dd>{result.institutionName}</dd></div>
    <div><dt className="text-slate-500">Issued at</dt><dd>{result.issuedAt}</dd></div>
  </dl><p className="mt-3 text-xs text-slate-500">This verification result is limited to public certificate metadata. It does not disclose student records or the underlying certificate request.</p></Panel>}
 </main>;
}
