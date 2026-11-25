import { useCallback,useEffect,useState } from "react";
import { PageHeader,Button,EmptyState } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api,type Notice } from "../api/t10Api";
export default function NoticeBulletinPage(){
 const tenant=useAppSelector(s=>s.auth.user?.tenantId??""); const institution=useAppSelector(s=>(s.auth.user as any)?.institutionId??"");
 const [items,setItems]=useState<Notice[]>([]); const [error,setError]=useState(""); const [title,setTitle]=useState(""); const [body,setBody]=useState("");
 const load=useCallback(()=>{if(tenant&&institution)t10Api.notices(tenant,institution).then(setItems).catch(()=>setError("Notices could not be loaded."));},[tenant,institution]);
 useEffect(()=>{load();},[load]);
 const publish=async()=>{try{await t10Api.publishNotice(tenant,{institutionId:institution,title,body,targetRoles:[],urgent:false,requiresAcknowledgment:false});setTitle("");setBody("");load();}catch{setError("Notice could not be published. Check your permission and try again.");}};
 return <main className="app-page"><PageHeader title="Notice Board & Circulars" description="Institutional notices, urgent alerts and required acknowledgments."/>
 {error&&<p role="alert">{error}</p>}<section className="app-card"><h2>Publish a notice</h2><label>Title<input value={title} onChange={e=>setTitle(e.target.value)} maxLength={160}/></label><label>Message<textarea value={body} onChange={e=>setBody(e.target.value)}/></label><Button onClick={publish} disabled={!title.trim()||!body.trim()||!institution}>Publish notice</Button></section>
 <section className="app-stack" aria-label="Published notices">{items.length===0?<EmptyState title="No published notices" description="Published notices will appear here."/>:items.map(n=><article className="app-card" key={n.notice_id}><h2>{n.title} {n.is_urgent&&<strong>Urgent</strong>}</h2><p>{n.body}</p>{n.requires_acknowledgment&&!n.acknowledged&&<Button onClick={async()=>{await t10Api.acknowledge(tenant,institution,n.notice_id);load();}}>Acknowledge</Button>}</article>)}</section></main>;
}
