import { useEffect,useState } from "react";
import { PageHeader,EmptyState } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api,type CatalogBook } from "../api/t10Api";
export default function LibraryCatalogPage(){
 const tenant=useAppSelector(s=>s.auth.user?.tenantId??"");const institution=useAppSelector(s=>(s.auth.user as any)?.institutionId??"");
 const [q,setQ]=useState("");const [books,setBooks]=useState<CatalogBook[]>([]);const [error,setError]=useState("");
 useEffect(()=>{if(tenant&&institution)t10Api.catalog(tenant,institution,q).then(setBooks).catch(()=>setError("Catalog could not be loaded."));},[tenant,institution,q]);
 return <main className="app-page"><PageHeader title="Library Operations" description="Search the catalog and review copy availability."/><label>Search catalog<input value={q} onChange={e=>setQ(e.target.value)} placeholder="Title or ISBN"/></label>{error&&<p role="alert">{error}</p>}{books.length===0?<EmptyState title="No matching books" description="Try another title or ISBN."/>:<div className="app-card"><table><thead><tr><th>Title</th><th>Author</th><th>ISBN</th><th>Available</th></tr></thead><tbody>{books.map(b=><tr key={b.id}><td>{b.title}</td><td>{b.author??"—"}</td><td>{b.isbn??"—"}</td><td>{b.available_copies}/{b.total_copies}</td></tr>)}</tbody></table></div>}</main>;
}
