export type AcademicDetail = Record<string, unknown>;
async function call<T>(url:string,init?:RequestInit):Promise<T>{const r=await fetch(url,{credentials:"same-origin",headers:{Accept:"application/json",...(init?.body?{"Content-Type":"application/json"}:{}),...init?.headers},...init});if(!r.ok){const p=await r.json().catch(()=>null);throw new Error(p?.message||p?.detail||r.statusText)}if(r.status===204)return undefined as T;return r.json() as Promise<T>}
const base=(t:string)=>{if(!t?.trim())throw new Error("Tenant context required");return `/api/tenants/${encodeURIComponent(t)}`};
export const getInstitutionDetail=(t:string,id:string)=>call<AcademicDetail>(`${base(t)}/institutions/${encodeURIComponent(id)}`);
export const getDepartmentDetail=(t:string,id:string)=>call<AcademicDetail>(`${base(t)}/institutions/departments/${encodeURIComponent(id)}`);
export const getProgramDetail=(t:string,id:string)=>call<AcademicDetail>(`${base(t)}/institutions/programs/${encodeURIComponent(id)}`);
export const getSectionDetail=(t:string,id:string)=>call<AcademicDetail>(`${base(t)}/institutions/sections/${encodeURIComponent(id)}`);
export const getAcademicSessionDetail=(t:string,id:string)=>call<AcademicDetail>(`${base(t)}/academic-sessions/${encodeURIComponent(id)}`);
export const patchDetail=(url:string,patch:Partial<AcademicDetail>,etag?:string)=>call<AcademicDetail>(url,{method:"PATCH",headers:etag?{"If-Match":etag}:{},body:JSON.stringify(patch)});
