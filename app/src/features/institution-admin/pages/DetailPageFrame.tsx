import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
export function DetailFrame({title,backTo,backLabel,load,fields}:{title:string;backTo:string;backLabel:string;load:()=>Promise<Record<string,unknown>>;fields:string[]}) {
 const [data,setData]=useState<Record<string,unknown>|null>(null),[error,setError]=useState(""),[busy,setBusy]=useState(true);
 useEffect(()=>{let live=true;setBusy(true);load().then(v=>{if(live)setData(v)}).catch(e=>{if(live)setError(e instanceof Error?e.message:"Unable to load")}).finally(()=>{if(live)setBusy(false)});return()=>{live=false}},[load]);
 return <main className="academic-detail-page"><nav><Link to={backTo}>{backLabel}</Link> / {title}</nav><h1>{title}</h1>{busy&&<p role="status">Loading…</p>}{error&&<p role="alert">{error}</p>}{!busy&&!error&&data&&<dl>{fields.map(k=><React.Fragment key={k}><dt>{k.replace(/([A-Z])/g," $1")}</dt><dd>{data[k]==null||data[k]===""?"—":String(data[k])}</dd></React.Fragment>)}</dl>}</main>;
}