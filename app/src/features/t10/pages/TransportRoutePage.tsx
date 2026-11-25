import { useEffect,useState } from "react";
import { PageHeader,EmptyState } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";
export default function TransportRoutePage(){
 const tenant=useAppSelector(s=>s.auth.user?.tenantId??"");const institution=useAppSelector(s=>(s.auth.user as any)?.institutionId??"");
 const [routes,setRoutes]=useState<Record<string,unknown>[]>([]);const [error,setError]=useState("");
 useEffect(()=>{if(tenant&&institution)t10Api.routes(tenant,institution).then(setRoutes).catch(()=>setError("Routes could not be loaded."));},[tenant,institution]);
 return <main className="app-page"><PageHeader title="Transport Tracking" description="Routes, assigned vehicles, drivers and seat utilization."/>{error&&<p role="alert">{error}</p>}{routes.length===0?<EmptyState title="No routes found" description="Configured transport routes will appear here."/>:<div className="app-card"><table><thead><tr><th>Route</th><th>Vehicle</th><th>Driver</th><th>Seats</th><th>Fee code</th></tr></thead><tbody>{routes.map(r=><tr key={String(r.id)}><td>{String(r.route_name)}</td><td>{String(r.vehicle_number)}</td><td>{String(r.driver_name)}</td><td>{String(r.allocated_seats)}/{String(r.total_capacity)}</td><td>{String(r.transport_fee_code??"—")}</td></tr>)}</tbody></table></div>}</main>;
}
