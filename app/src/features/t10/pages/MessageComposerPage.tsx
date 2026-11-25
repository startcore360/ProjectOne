import { useState } from "react";
import { PageHeader,Button } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";
export default function MessageComposerPage(){
 const tenant=useAppSelector(s=>s.auth.user?.tenantId??"");const institution=useAppSelector(s=>(s.auth.user as any)?.institutionId??"");
 const [recipient,setRecipient]=useState("");const [body,setBody]=useState("");const [status,setStatus]=useState("");
 const send=async()=>{try{const r=await t10Api.dispatch(tenant,{institutionId:institution,recipientUserId:recipient,body,channels:["IN_APP","PUSH","SMS","EMAIL"]});setStatus(`Dispatch queued: ${r.campaignId}`);setBody("");}catch{setStatus("Dispatch could not be queued. Check permissions and recipient details.");}};
 return <main className="app-page"><PageHeader title="Communications & Dispatch" description="Compose a private message and queue delivery through the configured channel fallback chain."/><section className="app-card"><label>Recipient user ID<input value={recipient} onChange={e=>setRecipient(e.target.value)}/></label><label>Message<textarea value={body} onChange={e=>setBody(e.target.value)}/></label><Button onClick={send} disabled={!tenant||!institution||!recipient||!body.trim()}>Queue dispatch</Button>{status&&<p role="status">{status}</p>}</section></main>;
}
