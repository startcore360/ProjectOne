import { useEffect,useState } from "react";
import { PageHeader,MetricCard } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";
export default function ExecutiveDashboardPage(){
 const tenant=useAppSelector(s=>s.auth.user?.tenantId??"");const institution=useAppSelector(s=>s.tenantPermissions.current?.institutionId??"");
 const [data,setData]=useState<Record<string,unknown>>({});const [error,setError]=useState("");const [loading,setLoading]=useState(true);
 useEffect(()=>{if(!tenant||!institution){setLoading(false);return;}setLoading(true);t10Api.dashboard(tenant,institution).then(setData).catch(()=>setError("Dashboard is unavailable. Ensure the read-only edu_report connection is configured.")).finally(()=>setLoading(false));},[tenant,institution]);
 const labels:Record<string,string>={activeStudents:"Active students",activeStaff:"Active staff",libraryOutstandingFines:"Outstanding library fines",openLibraryCirculations:"Open circulations",transportAllocations:"Transport allocations",smsSent:"SMS sent"};
 return <main className="app-page"><PageHeader title="Executive Dashboard" description="Institutional indicators from the isolated, read-only reporting connection."/>{loading && <p role="status">Loading executive metrics…</p>}<p role="alert">{error}</p><div className="app-grid">{Object.entries(labels).map(([key,label])=><MetricCard key={key} label={label} value={data[key]===undefined?"—":String(data[key])}/>)}</div></main>;
}
